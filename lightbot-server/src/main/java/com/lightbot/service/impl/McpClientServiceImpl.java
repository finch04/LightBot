package com.lightbot.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.BizException;
import com.lightbot.entity.McpServer;
import com.lightbot.enums.ErrorCode;
import com.lightbot.enums.McpTransportType;
import com.lightbot.service.McpClientService;
import com.lightbot.service.McpServerService;
import com.lightbot.util.RedisUtil;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.SyncMcpToolCallback;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * MCP 客户端服务实现：连接管理、工具发现、工具转回调
 *
 * @author finch
 * @since 2026-05-23
 */
@Slf4j
@Service
@Lazy
@RequiredArgsConstructor
public class McpClientServiceImpl implements McpClientService {

    private final McpServerService mcpServerService;
    private final RedisUtil redisUtil;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** Redis 缓存 key 前缀 */
    private static final String MCP_TOOLS_CACHE_KEY = "mcp:tools:";

    /** 客户端缓存：serverId → McpSyncClient */
    private final ConcurrentHashMap<Long, McpSyncClient> clientCache = new ConcurrentHashMap<>();

    /** ToolCallback 缓存：serverId → List<ToolCallback>（已过滤 disabled_tools） */
    private final ConcurrentHashMap<Long, List<ToolCallback>> callbackCache = new ConcurrentHashMap<>();

    @Override
    public List<McpSchema.Tool> testConnection(Long mcpServerId) {
        McpServer server = mcpServerService.getById(mcpServerId);
        if (server == null) {
            throw new BizException(ErrorCode.MCP_SERVER_NOT_FOUND);
        }

        // 测试连接：创建新客户端，发现工具，然后关闭
        McpSyncClient client = null;
        try {
            client = createAndInitClient(server);
            List<McpSchema.Tool> tools = client.listTools().tools();
            log.info("[MCP] 测试连接成功: server={}, 工具数={}", server.getName(), tools.size());
            return tools;
        } finally {
            if (client != null) {
                try { client.close(); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * 获取工具列表（优先从Redis缓存读取）
     *
     * @param mcpServerId MCP Server ID
     * @return 工具列表（从缓存或运行时获取）
     */
    @Override
    public List<McpSchema.Tool> getToolsWithCache(Long mcpServerId) {
        String cacheKey = MCP_TOOLS_CACHE_KEY + mcpServerId;

        // 1. 先查 Redis 缓存
        String cachedJson = redisUtil.get(cacheKey);
        if (cachedJson != null && !cachedJson.isBlank()) {
            try {
                List<McpSchema.Tool> tools = parseToolsFromJson(cachedJson);
                log.info("[MCP] 从Redis缓存读取工具: serverId={}, 工具数={}", mcpServerId, tools.size());
                return tools;
            } catch (Exception e) {
                log.warn("[MCP] 解析缓存失败，重新获取: serverId={}, error={}", mcpServerId, e.getMessage());
            }
        }

        // 2. 缓存不存在，从运行时获取
        McpServer server = mcpServerService.getById(mcpServerId);
        if (server == null) {
            throw new BizException(ErrorCode.MCP_SERVER_NOT_FOUND);
        }

        McpSyncClient client = null;
        try {
            client = createAndInitClient(server);
            List<McpSchema.Tool> tools;
            try {
                tools = client.listTools().tools();
            } catch (Exception e) {
                log.error("[MCP] 获取工具失败: serverId={}, name={}", mcpServerId, server.getName(), e);
                throw new BizException(ErrorCode.MCP_TOOLS_FETCH_FAILED);
            }

            // 3. 写入 Redis 缓存（不过期，手动刷新才清除）
            try {
                String json = serializeToolsToJson(tools);
                redisUtil.set(cacheKey, json);
                log.info("[MCP] 工具列表已缓存到Redis: serverId={}, 工具数={}", mcpServerId, tools.size());
            } catch (Exception e) {
                log.warn("[MCP] 写入Redis缓存失败: serverId={}, error={}", mcpServerId, e.getMessage());
            }

            return tools;
        } catch (BizException e) {
            if (e.getCode() == ErrorCode.MCP_SERVER_NOT_FOUND.getCode()
                    || e.getCode() == ErrorCode.MCP_TOOLS_FETCH_FAILED.getCode()) {
                throw e;
            }
            log.warn("[MCP] 获取工具失败: serverId={}, name={}, error={}",
                    mcpServerId, server.getName(), e.getMessage());
            throw new BizException(ErrorCode.MCP_TOOLS_FETCH_FAILED);
        } catch (Exception e) {
            log.error("[MCP] 获取工具失败: serverId={}, name={}", mcpServerId, server.getName(), e);
            throw new BizException(ErrorCode.MCP_TOOLS_FETCH_FAILED);
        } finally {
            if (client != null) {
                try { client.close(); } catch (Exception ignored) {}
            }
        }
    }

    @Override
    public List<ToolCallback> getToolCallbacks(Long mcpServerId) {
        return callbackCache.computeIfAbsent(mcpServerId, id -> {
            McpServer server = mcpServerService.getById(id);
            if (server == null) {
                return List.of();
            }

            McpSyncClient client = getOrCreateClient(server);
            List<McpSchema.Tool> mcpTools = client.listTools().tools();

            // 解析 disabled_tools
            Set<String> disabledTools = parseDisabledTools(server.getDisabledTools());

            List<ToolCallback> callbacks = mcpTools.stream()
                    .filter(tool -> !disabledTools.contains(tool.name()))
                    .map(tool -> (ToolCallback) SyncMcpToolCallback.builder()
                            .mcpClient(client)
                            .tool(tool)
                            .build())
                    .collect(Collectors.toList());
            log.info("[MCP] 缓存ToolCallback: serverId={}, 工具数={}", id, callbacks.size());
            return callbacks;
        });
    }

    @Override
    public List<ToolCallback> getAllToolCallbacks(Long mcpServerId) {
        McpServer server = mcpServerService.getById(mcpServerId);
        if (server == null) {
            return List.of();
        }

        McpSyncClient client = getOrCreateClient(server);
        List<McpSchema.Tool> mcpTools = client.listTools().tools();

        return mcpTools.stream()
                .map(tool -> (ToolCallback) SyncMcpToolCallback.builder()
                        .mcpClient(client)
                        .tool(tool)
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public void clearCache(Long mcpServerId) {
        // 1. 清除本地缓存
        callbackCache.remove(mcpServerId);
        McpSyncClient client = clientCache.remove(mcpServerId);
        if (client != null) {
            try {
                client.close();
                log.info("[MCP] 已清除缓存并关闭客户端: serverId={}", mcpServerId);
            } catch (Exception e) {
                log.warn("[MCP] 关闭客户端失败: serverId={}, error={}", mcpServerId, e.getMessage());
            }
        }

        // 2. 清除 Redis 缓存
        String cacheKey = MCP_TOOLS_CACHE_KEY + mcpServerId;
        redisUtil.delete(cacheKey);
        log.info("[MCP] 已清除Redis缓存: serverId={}", mcpServerId);
    }

    @PreDestroy
    public void closeAll() {
        callbackCache.clear();
        clientCache.forEach((id, client) -> {
            try {
                client.close();
            } catch (Exception e) {
                log.warn("[MCP] 关闭客户端失败: serverId={}, error={}", id, e.getMessage());
            }
        });
        clientCache.clear();
        log.info("[MCP] 已关闭所有MCP客户端连接");
    }

    /**
     * 获取或创建 McpSyncClient（懒初始化 + 缓存）
     */
    private McpSyncClient getOrCreateClient(McpServer server) {
        return clientCache.computeIfAbsent(server.getId(), id -> {
            try {
                McpSyncClient client = createAndInitClient(server);
                log.info("[MCP] 创建MCP客户端: serverId={}, name={}", id, server.getName());
                return client;
            } catch (Exception e) {
                log.error("[MCP] 创建MCP客户端失败: serverId={}, name={}, error={}", id, server.getName(), e.getMessage(), e);
                throw new BizException(ErrorCode.MCP_CONNECTION_FAILED, e);
            }
        });
    }

    /**
     * 创建并初始化 McpSyncClient
     */
    private McpSyncClient createAndInitClient(McpServer server) {
        McpClientTransport transport = createTransport(server);
        McpSyncClient client = McpClient.sync(transport)
                .clientInfo(new McpSchema.Implementation("lightbot", "1.0.0"))
                .requestTimeout(Duration.ofSeconds(30))
                .initializationTimeout(Duration.ofSeconds(60))
                .build();
        client.initialize();
        return client;
    }

    /**
     * 根据传输类型创建 Transport
     */
    private McpClientTransport createTransport(McpServer server) {
        McpTransportType transportType = server.getTransport();
        if (transportType == null) {
            // 兼容旧数据：从 installType 推断
            transportType = inferTransportType(server);
        }

        switch (transportType) {
            case SSE:
                return createSseTransport(server);
            case STREAMABLE_HTTP:
                return createStreamableHttpTransport(server);
            case STDIO:
                return createStdioTransport(server);
            default:
                throw new BizException(ErrorCode.MCP_CONFIG_ERROR);
        }
    }

    /**
     * 创建 SSE Transport
     */
    private McpClientTransport createSseTransport(McpServer server) {
        String url = server.getHost();
        if (url == null || url.isBlank()) {
            throw new BizException(ErrorCode.MCP_CONFIG_ERROR);
        }
        HttpClientSseClientTransport.Builder builder = HttpClientSseClientTransport.builder(url);
        addHeaders(builder, server.getHeaders());
        return builder.build();
    }

    /**
     * 创建 Streamable HTTP Transport
     */
    private McpClientTransport createStreamableHttpTransport(McpServer server) {
        String url = server.getHost();
        if (url == null || url.isBlank()) {
            throw new BizException(ErrorCode.MCP_CONFIG_ERROR);
        }
        HttpClientStreamableHttpTransport.Builder builder = HttpClientStreamableHttpTransport.builder(url);
        addHeaders(builder, server.getHeaders());
        return builder.build();
    }

    /**
     * 创建 stdio Transport
     */
    private McpClientTransport createStdioTransport(McpServer server) {
        Map<String, Object> deployConfig = parseJson(server.getDeployConfig());
        String command = resolveCommand(server, deployConfig);
        List<String> args = resolveArgs(deployConfig);
        Map<String, String> env = resolveEnv(deployConfig);

        // Windows: ProcessBuilder 不解析 .cmd/.bat，需通过 cmd /c 调用
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            List<String> fullArgs = new ArrayList<>();
            fullArgs.add(command);
            fullArgs.addAll(args);
            command = "cmd";
            args = new ArrayList<>();
            args.add("/c");
            args.addAll(fullArgs);
        }

        ServerParameters.Builder paramBuilder = ServerParameters.builder(command);
        if (!args.isEmpty()) {
            paramBuilder.args(args);
        }
        if (!env.isEmpty()) {
            paramBuilder.env(env);
        }
        ServerParameters params = paramBuilder.build();

        log.info("[MCP] 创建stdio传输: command={}, args={}", command, args);
        return new StdioClientTransport(params, new JacksonMcpJsonMapper(new com.fasterxml.jackson.databind.ObjectMapper()));
    }

    /**
     * 从 deployConfig 解析命令
     */
    private String resolveCommand(McpServer server, Map<String, Object> deployConfig) {
        // 优先从 deployConfig.packageName 获取
        Object packageName = deployConfig.get("packageName");
        if (packageName != null && !packageName.toString().isBlank()) {
            // 根据 installType 决定前缀
            String prefix = server.getInstallType() != null && "uvx".equals(server.getInstallType().getCode())
                    ? "uvx" : "npx";
            return prefix;
        }
        throw new BizException(ErrorCode.MCP_CONFIG_ERROR);
    }

    /**
     * 解析命令参数
     */
    @SuppressWarnings("unchecked")
    private List<String> resolveArgs(Map<String, Object> deployConfig) {
        List<String> args = new ArrayList<>();
        Object packageName = deployConfig.get("packageName");
        if (packageName != null) {
            args.add("-y");
            args.add(packageName.toString());
        }
        Object extraArgs = deployConfig.get("args");
        if (extraArgs instanceof List<?> list) {
            for (Object item : list) {
                args.add(item.toString());
            }
        }
        return args;
    }

    /**
     * 解析环境变量
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> resolveEnv(Map<String, Object> deployConfig) {
        Object env = deployConfig.get("env");
        if (env instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .collect(Collectors.toMap(
                            e -> e.getKey().toString(),
                            e -> e.getValue().toString()));
        }
        return Map.of();
    }

    /**
     * 给 SSE/StreamableHttp builder 添加请求头
     */
    private void addHeaders(HttpClientSseClientTransport.Builder builder, String headersJson) {
        Map<String, String> headers = parseHeaders(headersJson);
        if (!headers.isEmpty()) {
            builder.customizeRequest(rb -> headers.forEach(rb::header));
        }
    }

    private void addHeaders(HttpClientStreamableHttpTransport.Builder builder, String headersJson) {
        Map<String, String> headers = parseHeaders(headersJson);
        if (!headers.isEmpty()) {
            builder.customizeRequest(rb -> headers.forEach(rb::header));
        }
    }

    /**
     * 解析 headers JSON
     */
    private Map<String, String> parseHeaders(String headersJson) {
        if (headersJson == null || headersJson.isBlank()) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(headersJson, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("[MCP] 解析headers失败: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * 解析 disabled_tools JSON 数组
     */
    private Set<String> parseDisabledTools(String disabledToolsJson) {
        if (disabledToolsJson == null || disabledToolsJson.isBlank()) {
            return Set.of();
        }
        try {
            List<String> list = OBJECT_MAPPER.readValue(disabledToolsJson, new TypeReference<>() {});
            return Set.copyOf(list);
        } catch (Exception e) {
            log.warn("[MCP] 解析disabled_tools失败: {}", e.getMessage());
            return Set.of();
        }
    }

    /**
     * 从 installType 推断 transport 类型（兼容旧数据）
     */
    private McpTransportType inferTransportType(McpServer server) {
        if (server.getInstallType() == null) {
            return McpTransportType.SSE;
        }
        return switch (server.getInstallType()) {
            case NPX, UVX -> McpTransportType.STDIO;
            case SSE -> McpTransportType.SSE;
        };
    }

    /**
     * 解析 JSON 字符串为 Map
     */
    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("[MCP] 解析JSON失败: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * 序列化工具列表为 JSON
     */
    private String serializeToolsToJson(List<McpSchema.Tool> tools) throws Exception {
        List<Map<String, Object>> toolMaps = new ArrayList<>();
        for (McpSchema.Tool tool : tools) {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("name", tool.name());
            map.put("description", tool.description());
            map.put("inputSchema", tool.inputSchema());
            toolMaps.add(map);
        }
        return OBJECT_MAPPER.writeValueAsString(toolMaps);
    }

    /**
     * 从 JSON 解析工具列表
     */
    private List<McpSchema.Tool> parseToolsFromJson(String json) throws Exception {
        List<Map<String, Object>> toolMaps = OBJECT_MAPPER.readValue(json, new TypeReference<>() {});
        List<McpSchema.Tool> tools = new ArrayList<>();
        for (Map<String, Object> map : toolMaps) {
            String name = (String) map.get("name");
            String description = (String) map.get("description");
            Object inputSchemaObj = map.get("inputSchema");
            McpSchema.JsonSchema inputSchema = null;
            if (inputSchemaObj != null) {
                inputSchema = OBJECT_MAPPER.convertValue(inputSchemaObj, McpSchema.JsonSchema.class);
            }
            // 使用完整构造函数（MCP SDK 2025版本）
            tools.add(new McpSchema.Tool(
                    name,
                    description,
                    null,                   // title
                    inputSchema,
                    Map.of(),               // _annotations
                    null,                   // annotations
                    Map.of()                // _meta
            ));
        }
        return tools;
    }
}
