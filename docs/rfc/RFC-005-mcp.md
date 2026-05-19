# RFC-005: MCP (Model Context Protocol)

| 字段 | 值 |
|------|------|
| RFC 编号 | 005 |
| 标题 | MCP (Model Context Protocol) |
| 状态 | Draft |
| 作者 | LightBot Team |
| 创建日期 | 2025-05-19 |
| 最后更新 | 2025-05-19 |
| 关联模块 | lightbot-core/mcp |

---

## 1. 背景

Model Context Protocol (MCP) 是 Anthropic 提出的开放协议，定义了 AI 模型与外部工具/资源之间的标准化通信方式。MCP 解决了 Tool 生态碎片化问题：不同的 AI 应用各自定义 Tool 接口，导致工具无法复用。MCP 通过统一协议，使得一个 Tool Server 可以被任何支持 MCP 的 AI 应用调用。

LightBot 需要内置 MCP 支持，既是 **MCP Client**（调用外部 MCP Server 的 Tool/Resource），也可作为 **MCP Server**（暴露自身能力给外部 AI 应用）。

---

## 2. 问题定义

### 2.1 核心问题

**如何在 LightBot 中实现 MCP 协议支持，使其满足：**

1. **Client 能力** — 连接外部 MCP Server，发现并调用其 Tool 和 Resource
2. **Server 能力** — 将 LightBot 的 Tool/Resource 暴露为 MCP Server
3. **传输协议** — 支持 stdio、SSE、Streamable HTTP 三种传输方式
4. **与 Tool 系统统一** — MCP Tool 与内置 Tool 在 Agent 调用层面无差异
5. **生命周期管理** — MCP Server 连接的建立、心跳、重连、断开
6. **安全控制** — MCP Server 的白名单、Tool 调用权限

---

## 3. 设计目标

| 优先级 | 目标 | 衡量标准 |
|--------|------|----------|
| P0 | **MCP Client** | 连接 MCP Server，发现 Tool，调用 Tool |
| P0 | **stdio 传输** | 支持通过 stdio 启动 MCP Server 进程 |
| P0 | **Tool 统一** | MCP Tool 自动注册到 ToolRegistry |
| P1 | **SSE 传输** | 支持 SSE 长连接传输 |
| P1 | **MCP Server** | 将 LightBot Tool 暴露为 MCP Server |
| P1 | **Resource 支持** | 支持 MCP Resource 读取 |
| P2 | **Streamable HTTP** | 支持 Streamable HTTP 传输（MCP 2025-03-26） |
| P2 | **连接池管理** | MCP 连接的复用与健康检查 |

---

## 4. 非目标

| 非目标 | 原因 |
|--------|------|
| MCP Prompt 支持 | MCP Prompt 是可选能力，暂不实现 |
| MCP Sampling 支持 | Server 请求 Client 调用 LLM，场景复杂，暂不实现 |
| 自动发现 MCP Server | 安全风险，手动配置白名单 |

---

## 5. MCP 协议概览

### 5.1 协议层次

```
┌─────────────────────────────────────────┐
│           MCP Protocol Layer            │
├─────────────────────────────────────────┤
│  Capabilities                           │
│  ┌──────────┐ ┌──────────┐ ┌────────┐ │
│  │  Tools   │ │ Resources│ │ Prompts│ │
│  └──────────┘ └──────────┘ └────────┘ │
├─────────────────────────────────────────┤
│  Transport                              │
│  ┌──────────┐ ┌──────────┐ ┌────────┐ │
│  │  stdio   │ │   SSE    │ │ Stream │ │
│  │          │ │          │ │ HTTP   │ │
│  └──────────┘ └──────────┘ └────────┘ │
├─────────────────────────────────────────┤
│  JSON-RPC 2.0                           │
└─────────────────────────────────────────┘
```

### 5.2 核心交互流程

```
LightBot (Client)                    MCP Server
    │                                    │
    │──── initialize ──────────────────▶│  初始化握手
    │◀─── initialize result ────────────│
    │                                    │
    │──── tools/list ──────────────────▶│  发现工具
    │◀─── tools list result ────────────│
    │                                    │
    │──── tools/call ──────────────────▶│  调用工具
    │◀─── tools/call result ────────────│
    │                                    │
    │──── resources/list ──────────────▶│  发现资源
    │◀─── resources list result ────────│
    │                                    │
    │──── resources/read ──────────────▶│  读取资源
    │◀─── resources read result ────────│
```

---

## 6. MCP Client

### 6.1 连接管理

```java
public class MCPClientManager {

    private final Map<String, MCPClient> clients = new ConcurrentHashMap<>();
    private final MCPClientConfig config;

    /**
     * 连接到 MCP Server
     */
    public MCPClient connect(MCPServerConfig serverConfig) {
        MCPTransport transport = createTransport(serverConfig);
        MCPClient client = new DefaultMCPClient(transport);

        // 初始化握手
        InitializeResult initResult = client.initialize(
            new InitializeRequest("LightBot", "1.0.0",
                Map.of("tools", Map.of())));

        // 发现 Tool
        List<MCPTool> tools = client.listTools();

        // 自动注册到 ToolRegistry
        for (MCPTool tool : tools) {
            ToolRegistry.register(new MCPToolAdapter(tool, client));
        }

        clients.put(serverConfig.getName(), client);
        return client;
    }

    private MCPTransport createTransport(MCPServerConfig config) {
        return switch (config.getTransportType()) {
            case STDIO -> new StdioTransport(config.getCommand(), config.getArgs());
            case SSE -> new SSETransport(config.getUrl());
            case STREAMABLE_HTTP -> new StreamableHttpTransport(config.getUrl());
        };
    }
}
```

### 6.2 配置

```yaml
lightbot:
  mcp:
    servers:
      - name: github
        transport: stdio
        command: npx
        args: ["-y", "@modelcontextprotocol/server-github"]
        env:
          GITHUB_TOKEN: ${GITHUB_TOKEN}

      - name: database
        transport: sse
        url: http://localhost:3001/sse

      - name: filesystem
        transport: stdio
        command: npx
        args: ["-y", "@modelcontextprotocol/server-filesystem", "/data"]
```

### 6.3 Tool 调用

```java
public class DefaultMCPClient implements MCPClient {

    private final MCPTransport transport;

    @Override
    public MCPToolResult callTool(String name, Map<String, Object> arguments) {
        // JSON-RPC 2.0 请求
        JSONRPCRequest request = new JSONRPCRequest(
            "2.0",
            nextId(),
            "tools/call",
            Map.of("name", name, "arguments", arguments)
        );

        JSONRPCResponse response = transport.sendRequest(request);

        if (response.getError() != null) {
            throw new MCPException(response.getError().getMessage());
        }

        return parseToolResult(response.getResult());
    }

    @Override
    public List<MCPTool> listTools() {
        JSONRPCRequest request = new JSONRPCRequest("2.0", nextId(), "tools/list", Map.of());
        JSONRPCResponse response = transport.sendRequest(request);
        return parseToolList(response.getResult());
    }
}
```

---

## 7. MCP Server

### 7.1 暴露 LightBot 能力

LightBot 可作为 MCP Server，将自身 Tool 暴露给外部 AI 应用：

```java
public class LightBotMCPServer {

    private final ToolRegistry toolRegistry;

    /**
     * 处理 tools/list 请求
     */
    public List<MCPTool> handleListTools() {
        return toolRegistry.getAll().stream()
            .filter(tool -> tool.isExposedViaMCP())  // 仅暴露标记为 MCP 可用的 Tool
            .map(this::toMCPTool)
            .collect(Collectors.toList());
    }

    /**
     * 处理 tools/call 请求
     */
    public MCPToolResult handleCallTool(String name, Map<String, Object> arguments) {
        Tool tool = toolRegistry.get(name);
        if (tool == null) {
            return MCPToolResult.error("Tool not found: " + name);
        }

        ToolResult result = tool.execute(
            new ToolInput(UUID.randomUUID().toString(), name, arguments),
            AgentContextHolder.getContext());

        return new MCPToolResult(
            List.of(new MCPTextContent(result.getContent())),
            !result.isSuccess());
    }
}
```

### 7.2 Server 配置

```yaml
lightbot:
  mcp:
    server:
      enabled: true
      name: lightbot
      version: 1.0.0
      transport: sse
      port: 8081
      # 暴露哪些 Tool（支持通配符）
      exposed-tools:
        - "search_knowledge"
        - "get_weather"
        - "lightbot_*"
```

---

## 8. 传输协议

### 8.1 stdio 传输

通过标准输入/输出与 MCP Server 进程通信：

```java
public class StdioTransport implements MCPTransport {

    private final Process process;
    private final BufferedWriter writer;
    private final BufferedReader reader;

    public StdioTransport(String command, List<String> args) {
        ProcessBuilder pb = new ProcessBuilder();
        pb.command(command, args.toArray(new String[0]));
        this.process = pb.start();
        this.writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
        this.reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
    }

    @Override
    public JSONRPCResponse sendRequest(JSONRPCRequest request) throws IOException {
        writer.write(JsonUtils.toJson(request));
        writer.newLine();
        writer.flush();

        String line = reader.readLine();
        return JsonUtils.fromJson(line, JSONRPCResponse.class);
    }

    @Override
    public void close() {
        process.destroy();
    }
}
```

### 8.2 SSE 传输

通过 HTTP SSE 长连接通信：

```java
public class SSETransport implements MCPTransport {

    private final String endpointUrl;
    private final HttpClient httpClient;
    private String messageEndpoint;

    @Override
    public JSONRPCResponse sendRequest(JSONRPCRequest request) throws IOException {
        HttpRequest httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(messageEndpoint))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(JsonUtils.toJson(request)))
            .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        return JsonUtils.fromJson(response.body(), JSONRPCResponse.class);
    }
}
```

---

## 9. 安全控制

### 9.1 MCP Server 白名单

仅允许连接配置的 MCP Server，防止任意连接：

```java
public class MCPServerWhitelist {

    private final Set<String> allowedServers;

    public void validate(MCPServerConfig config) {
        if (!allowedServers.contains(config.getName())) {
            throw new MCPSecurityException("MCP Server not in whitelist: " + config.getName());
        }
    }
}
```

### 9.2 Tool 调用权限

```java
public class MCPToolPermission {

    /** Tool 调用前的权限检查 */
    public boolean checkPermission(String toolName, AgentContext context) {
        // 租户级权限
        String tenantId = context.getUserContext().getTenantId();
        return permissionService.hasToolPermission(tenantId, toolName);
    }
}
```

---

## 10. 风险分析

| 风险 | 等级 | 缓解措施 |
|------|------|----------|
| MCP Server 恶意行为 | 高 | 白名单 + Tool 权限控制 |
| MCP Server 进程崩溃 | 中 | 进程监控 + 自动重启 |
| SSE 连接断开 | 中 | 心跳检测 + 自动重连 |
| Tool 响应过大 | 中 | 响应大小限制（默认 1MB） |
| JSON-RPC 序列化异常 | 低 | 严格 Schema 校验 |

---

## 11. 后续演进

| 阶段 | 能力 |
|------|------|
| v0.2 | MCP Client（stdio）+ Tool 自动注册 |
| v0.2+ | MCP Client（SSE）+ MCP Server |
| v0.3 | MCP Tool 作为 Workflow 节点 |
| v1.0 | Streamable HTTP + 连接池 + 完整安全控制 |

---

## 附录：配置项

```yaml
lightbot:
  mcp:
    # MCP Client 配置
    client:
      connection-timeout-ms: 10000
      request-timeout-ms: 30000
      max-response-size: 1048576   # 1MB
    # MCP Server 配置
    server:
      enabled: false
      port: 8081
      max-connections: 50
```
