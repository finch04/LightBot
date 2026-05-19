# RFC-003: Tool System

| 字段 | 值 |
|------|------|
| RFC 编号 | 003 |
| 标题 | Tool System |
| 状态 | Draft |
| 作者 | LightBot Team |
| 创建日期 | 2025-05-19 |
| 最后更新 | 2025-05-19 |
| 关联模块 | lightbot-core/tool |

---

## 1. 背景

Tool Calling 是 AI Agent 与外部世界交互的核心能力。LLM 本身只能生成文本，通过 Tool 调用才能执行实际操作：查询数据库、调用 API、操作文件、搜索互联网等。

Spring AI 提供了基础的 `FunctionCallback` 抽象，但在以下方面需要上层封装：

- **Tool 生命周期管理** — 注册、发现、版本管理
- **参数校验** — 自动校验 LLM 返回的参数是否符合 Schema
- **执行隔离** — Tool 执行超时、异常隔离
- **热插拔** — 运行时动态注册/注销 Tool
- **MCP 集成** — 与外部 MCP Server 的 Tool 统一管理

---

## 2. 问题定义

### 2.1 核心问题

**如何构建一个标准化的 Tool 框架，使其满足：**

1. **定义标准化** — Tool 以统一协议描述（名称、描述、参数 Schema），LLM 可准确理解
2. **注册自动化** — Java 方法通过注解自动注册为 Tool，零模板代码
3. **调用安全** — 参数校验、超时控制、异常隔离、权限检查
4. **热插拔** — 支持运行时动态注册/注销，不重启服务
5. **统一管理** — 内置 Tool 和 MCP 外部 Tool 统一注册、统一调用
6. **可观测** — 每次 Tool 调用的输入/输出/耗时可被追踪

### 2.2 约束条件

| 约束 | 说明 |
|------|------|
| 参数格式 | 遵循 JSON Schema 规范 |
| 返回值 | 统一为 String（LLM 友好） |
| 执行模型 | 同步执行，超时中断 |
| 并发限制 | 单次 Agent 执行内并行 Tool 数 ≤ 5 |

---

## 3. 设计目标

| 优先级 | 目标 | 衡量标准 |
|--------|------|----------|
| P0 | **注解驱动注册** | `@Tool` 注解 + `@ToolParam` 自动注册 |
| P0 | **参数自动校验** | 基于 JSON Schema 自动校验 LLM 返回参数 |
| P0 | **OpenAI Schema 兼容** | 自动生成 OpenAI function calling 格式 Schema |
| P1 | **超时与异常隔离** | Tool 执行超时自动中断，异常不影响 Agent 主流程 |
| P1 | **热插拔** | 运行时通过 API 动态注册/注销 Tool |
| P1 | **MCP 统一集成** | MCP Server 的 Tool 与内置 Tool 统一管理 |
| P2 | **Tool 执行追踪** | 记录每次调用的 input/output/latency |
| P2 | **权限控制** | Tool 级别的调用权限（租户隔离） |

---

## 4. 非目标

| 非目标 | 原因 |
|--------|------|
| Tool 编排（串行/并行组合） | 属于 Workflow 引擎能力（RFC-002） |
| Tool 沙盒执行 | 安全风险高，v1.0 考虑 |
| Tool 自动发现（Agent 自主创建 Tool） | 复杂度高，暂不考虑 |
| Tool 市场 | v1.0+ 规划 |

---

## 5. 核心架构

### 5.1 分层架构

```
┌─────────────────────────────────────────────────────────────┐
│                    Tool System Architecture                  │
├─────────────────────────────────────────────────────────────┤
│  Registration Layer                                         │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐                   │
│  │ @Tool    │ │ Manual   │ │ MCP      │                   │
│  │ Scanner  │ │ Register │ │ Discovery│                   │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘                   │
├───────┼────────────┼────────────┼───────────────────────────┤
│       └────────────┼────────────┘                          │
│              ┌─────▼──────┐                                │
│              │ToolRegistry│  统一注册中心                   │
│              └─────┬──────┘                                │
├────────────────────┼────────────────────────────────────────┤
│  Execution Layer   │                                        │
│  ┌─────────────────▼──────────────────────────────────┐    │
│  │              ToolExecutor                           │    │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐           │    │
│  │  │ Schema   │ │ Param    │ │ Timeout  │           │    │
│  │  │ Validator│ │ Resolver │ │ Guard    │           │    │
│  │  └──────────┘ └──────────┘ └──────────┘           │    │
│  └────────────────────────────────────────────────────┘    │
├─────────────────────────────────────────────────────────────┤
│  Infrastructure                                             │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐                   │
│  │ Tracer   │ │ Metrics  │ │ EventBus │                   │
│  └──────────┘ └──────────┘ └──────────┘                   │
└─────────────────────────────────────────────────────────────┘
```

### 5.2 核心组件

| 组件 | 职责 |
|------|------|
| `ToolRegistry` | Tool 注册中心，管理所有 Tool 的元数据和实例 |
| `ToolExecutor` | Tool 执行器，负责参数校验、超时控制、异常隔离 |
| `ToolScanner` | 扫描 `@Tool` 注解，自动注册 Spring Bean |
| `SchemaGenerator` | 根据方法签名自动生成 JSON Schema |
| `MCPToolAdapter` | 将 MCP Server 的 Tool 适配为内部 Tool 接口 |

---

## 6. 注解驱动注册

### 6.1 @Tool 注解

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface Tool {
    /** Tool 名称，LLM 通过此名称调用 */
    String name() default "";

    /** Tool 描述 */
    String description() default "";

    /** 是否需要 AgentContext */
    boolean requiresContext() default false;
}
```

### 6.2 @ToolParam 注解

```java
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface ToolParam {
    /** 参数描述 */
    String description() default "";

    /** 是否必填 */
    boolean required() default true;
}
```

### 6.3 使用示例

```java
@Component
public class WeatherTool {

    @Tool(name = "get_weather", description = "获取指定城市的当前天气信息")
    public String getWeather(
            @ToolParam(description = "城市名称，如：北京", required = true) String city,
            @ToolParam(description = "温度单位", required = false) String unit) {
        // 调用天气 API
        WeatherResult result = weatherApi.query(city, unit != null ? unit : "celsius");
        return String.format("%s 当前温度：%s°C，天气：%s",
            city, result.getTemperature(), result.getDescription());
    }
}
```

### 6.4 自动注册流程

```
Spring 容器启动
    │
    ▼
┌──────────────────┐
│ 1. Bean Scanner  │  扫描所有 @Component Bean
└────────┬─────────┘
         ▼
┌──────────────────┐
│ 2. Method Scan   │  扫描 @Tool 注解方法
└────────┬─────────┘
         ▼
┌──────────────────┐
│ 3. Schema Gen    │  生成 JSON Schema
└────────┬─────────┘
         ▼
┌──────────────────┐
│ 4. Register      │  注册到 ToolRegistry
└──────────────────┘
```

---

## 7. Schema 自动生成

### 7.1 从方法签名生成 JSON Schema

```java
public class SchemaGenerator {

    /**
     * 将 Java 方法签名转换为 OpenAI function calling 格式
     */
    public ToolDefinition generate(Method method, Tool toolAnnotation) {
        ToolDefinition def = new ToolDefinition();
        def.setName(resolveName(method, toolAnnotation));
        def.setDescription(toolAnnotation.description());

        // 解析参数
        Parameter[] params = method.getParameters();
        Map<String, JsonSchemaProperty> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        for (Parameter param : params) {
            if (param.isAnnotationPresent(ToolParam.class)) {
                ToolParam annotation = param.getAnnotation(ToolParam.class);
                String paramName = param.getName();
                properties.put(paramName, JsonSchemaProperty.of(
                    resolveJsonType(param.getType()),
                    annotation.description()
                ));
                if (annotation.required()) {
                    required.add(paramName);
                }
            }
        }

        def.setParameters(JsonSchema.of(properties, required));
        return def;
    }

    private String resolveJsonType(Class<?> type) {
        if (type == String.class) return "string";
        if (type == int.class || type == Integer.class) return "integer";
        if (type == double.class || type == Double.class) return "number";
        if (type == boolean.class || type == Boolean.class) return "boolean";
        if (List.class.isAssignableFrom(type)) return "array";
        return "object";
    }
}
```

### 7.2 生成结果示例

```json
{
    "type": "function",
    "function": {
        "name": "get_weather",
        "description": "获取指定城市的当前天气信息",
        "parameters": {
            "type": "object",
            "properties": {
                "city": {
                    "type": "string",
                    "description": "城市名称，如：北京"
                },
                "unit": {
                    "type": "string",
                    "description": "温度单位"
                }
            },
            "required": ["city"]
        }
    }
}
```

---

## 8. 执行机制

### 8.1 执行流程

```java
public class DefaultToolExecutor implements ToolExecutor {

    private final ToolRegistry registry;
    private final ExecutorService executor;

    @Override
    public ToolResult execute(ToolCall call, AgentContext context) {
        // 1. 查找 Tool
        ToolEntry entry = registry.get(call.getName());
        if (entry == null) {
            return ToolResult.error(call.getCallId(), "Tool not found: " + call.getName());
        }

        // 2. 参数校验
        ValidationResult validation = schemaValidator.validate(
            entry.getDefinition().getParameters(), call.getArguments());
        if (!validation.isValid()) {
            return ToolResult.error(call.getCallId(), "Invalid parameters: " + validation.getErrors());
        }

        // 3. 超时执行
        try {
            Future<String> future = executor.submit(() ->
                entry.invoke(call.getArguments(), context));
            String result = future.get(30, TimeUnit.SECONDS);
            return ToolResult.success(call.getCallId(), result);
        } catch (TimeoutException e) {
            return ToolResult.error(call.getCallId(), "Tool execution timeout");
        } catch (ExecutionException e) {
            return ToolResult.error(call.getCallId(), "Tool error: + e.getCause().getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolResult.error(call.getCallId(), "Tool execution interrupted");
        }
    }
}
```

### 8.2 参数解析

LLM 返回的 JSON 参数自动解析为 Java 方法参数：

```java
public class ParameterResolver {

    public Object[] resolve(Method method, Map<String, Object> arguments) {
        Parameter[] params = method.getParameters();
        Object[] args = new Object[params.length];

        for (int i = 0; i < params.length; i++) {
            Parameter param = params[i];
            if (param.getType() == AgentContext.class) {
                args[i] = AgentContextHolder.getContext();
            } else if (arguments.containsKey(param.getName())) {
                args[i] = convert(arguments.get(param.getName()), param.getType());
            } else if (param.isAnnotationPresent(ToolParam.class)
                       && param.getAnnotation(ToolParam.class).required()) {
                throw new ToolException("Missing required parameter: " + param.getName());
            }
        }
        return args;
    }
}
```

---

## 9. 热插拔机制

### 9.1 动态注册 API

```
POST   /api/tools                  — 动态注册 Tool
DELETE /api/tools/{name}           — 注销 Tool
GET    /api/tools                  — 列出所有已注册 Tool
GET    /api/tools/{name}/schema    — 获取 Tool Schema
```

### 9.2 动态注册实现

```java
@RestController
@RequestMapping("/api/tools")
public class ToolManageController {

    private final ToolRegistry registry;
    private final SchemaGenerator schemaGenerator;

    @PostMapping
    public ToolDefinition register(@RequestBody ToolRegistrationRequest request) {
        // 动态创建 Tool 定义
        ToolDefinition def = ToolDefinition.builder()
            .name(request.getName())
            .description(request.getDescription())
            .parameters(request.getParameters())
            .build();

        // 注册为 HTTP Tool（通过 HTTP 调用外部服务）
        HttpTool tool = new HttpTool(def, request.getEndpoint());
        registry.register(def, tool);

        return def;
    }

    @DeleteMapping("/{name}")
    public void unregister(@PathVariable String name) {
        registry.unregister(name);
    }
}
```

---

## 10. MCP 统一集成

### 10.1 MCP Tool 适配

MCP Server 的 Tool 通过适配器注册到内部 ToolRegistry，对 Agent 透明：

```java
public class MCPToolAdapter implements Tool {

    private final MCPTool mcpTool;
    private final MCPClient mcpClient;

    @Override
    public String getName() {
        return "mcp_" + mcpTool.getName();  // 加前缀避免冲突
    }

    @Override
    public String getDescription() {
        return mcpTool.getDescription();
    }

    @Override
    public ToolParameterSchema getParameterSchema() {
        return ToolParameterSchema.from(mcpTool.getInputSchema());
    }

    @Override
    public ToolResult execute(ToolInput input, AgentContext context) {
        // 通过 MCP 协议调用远程 Tool
        MCPToolResult result = mcpClient.callTool(mcpTool.getName(), input.getArguments());
        return new ToolResult(input.callId(), getName(), result.getContent(), result.isError());
    }
}
```

### 10.2 统一 Tool 视图

对 Agent 而言，内置 Tool 和 MCP Tool 没有区别：

```
ToolRegistry
├── get_weather          (内置 Java Tool)
├── search_knowledge     (内置 Java Tool)
├── mcp_github_pr        (MCP Tool - GitHub)
├── mcp_slack_send       (MCP Tool - Slack)
└── mcp_db_query         (MCP Tool - Database)
```

---

## 11. 风险分析

| 风险 | 等级 | 缓解措施 |
|------|------|----------|
| Tool 执行超时阻塞 Agent | 高 | 线程池隔离 + 超时中断 |
| LLM 生成非法参数 | 中 | JSON Schema 自动校验 + 友好错误信息返回 LLM 重试 |
| Tool 异常导致 Agent 崩溃 | 中 | try-catch 隔离，返回错误信息给 LLM |
| 恶意 Tool 注入 | 中 | 租户级 Tool 权限控制 |
| MCP Server 不可用 | 中 | 健康检查 + 降级处理 |

---

## 12. 后续演进

| 阶段 | 能力 |
|------|------|
| v0.1 | 内置 Tool（HTTP、时间）+ 基础注册 |
| v0.2 | `@Tool` 注解自动注册 + MCP 集成 |
| v0.3 | Tool 作为 Workflow 节点 |
| v1.0 | Tool 权限控制 + 租户隔离 + Tool 市场 |

---

## 附录：配置项

```yaml
lightbot:
  tool:
    # Tool 执行超时（秒）
    execution-timeout-seconds: 30
    # 并行 Tool 执行线程池大小
    thread-pool-size: 10
    # 最大参数大小（字节）
    max-parameter-size: 65536
    # MCP 工具前缀
    mcp-prefix: "mcp_"
```
