package com.lightbot.tool.registrar;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.entity.McpServer;
import com.lightbot.enums.CommonStatus;
import com.lightbot.enums.McpInstallType;
import com.lightbot.enums.McpTransportType;
import com.lightbot.service.McpServerService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 内置 MCP Server 自动注册器
 * <p>启动时检查数据库，缺失的内置 MCP Server 自动插入（仅 DB 记录，不做工具发现）</p>
 *
 * @author finch
 * @since 2026-05-23
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BuiltinMcpRegistrar {

    private final McpServerService mcpServerService;
    private final ObjectMapper objectMapper;

    /** 内置 MCP Server 定义 */
    private static final List<BuiltInMcp> BUILT_IN_MCPS = List.of(
            new BuiltInMcp(
                    "sequentialthinking",
                    "顺序思考工具，帮助AI将复杂问题分解为多个步骤",
                    McpInstallType.SSE,
                    McpTransportType.STREAMABLE_HTTP,
                    "https://remote.mcpservers.org/sequentialthinking/mcp",
                    null
            ),
            new BuiltInMcp(
                    "mcp-server-chart",
                    "图表生成工具，支持生成柱状图、折线图、饼图等各类图表",
                    McpInstallType.NPX,
                    McpTransportType.STDIO,
                    null,
                    Map.of("packageName", "@antv/mcp-server-chart")
            )
    );

    @PostConstruct
    public void registerBuiltInMcpServers() {
        int imported = 0;
        for (BuiltInMcp def : BUILT_IN_MCPS) {
            try {
                // 1. 检查是否已存在
                McpServer existing = mcpServerService.getOne(
                        new LambdaQueryWrapper<McpServer>()
                                .eq(McpServer::getName, def.name)
                                .last("LIMIT 1"));

                if (existing == null) {
                    // 2. 不存在 → 插入
                    McpServer server = new McpServer();
                    server.setName(def.name);
                    server.setDescription(def.description);
                    server.setInstallType(def.installType);
                    server.setTransport(def.transport);
                    server.setHost(def.host);
                    server.setDeployConfig(def.deployConfig != null
                            ? objectMapper.writeValueAsString(def.deployConfig) : null);
                    server.setStatus(CommonStatus.DISABLED);
                    mcpServerService.save(server);
                    imported++;
                    log.info("[BuiltinMcpRegistrar] 导入内置 MCP Server: name={}, id={}", def.name, server.getId());
                } else {
                    log.debug("[BuiltinMcpRegistrar] 内置 MCP Server 已存在，跳过: name={}", def.name);
                }
            } catch (Exception e) {
                log.warn("[BuiltinMcpRegistrar] 处理内置 MCP Server 失败: name={}, error={}", def.name, e.getMessage());
            }
        }
        log.info("[BuiltinMcpRegistrar] 内置 MCP Server 扫描完成: total={}, imported={}", BUILT_IN_MCPS.size(), imported);
    }

    /**
     * 内置 MCP Server 定义
     */
    private record BuiltInMcp(
            String name,
            String description,
            McpInstallType installType,
            McpTransportType transport,
            String host,
            Map<String, Object> deployConfig
    ) {}
}
