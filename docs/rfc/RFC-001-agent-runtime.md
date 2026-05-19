# RFC-001: Agent Runtime

| 字段 | 值 |
|------|------|
| RFC 编号 | 001 |
| 标题 | Agent Runtime |
| 状态 | Draft |
| 作者 | LightBot Team |
| 创建日期 | 2025-05-19 |
| 最后更新 | 2025-05-19 |
| 关联模块 | lightbot-core/agent |

---

## 1. 背景

AI Agent 是 LightBot 平台的核心执行单元。一个 Agent 承载了从接收用户输入、调度 LLM 推理、调用外部工具、管理上下文记忆到返回最终响应的完整链路。

当前业界主流的 Agent 框架（LangChain、Dify）多基于 Python 实现，Java 生态中缺乏一套**轻量、可扩展、企业级**的 Agent Runtime。Spring AI 虽然提供了基础的 LLM 调用抽象，但在 Agent 生命周期管理、上下文传播、Tool 调度、流式输出等方面仍需上层封装。

本 RFC 定义 LightBot Agent Runtime 的核心架构与设计决策。

---

## 2. 问题定义

### 2.1 核心问题

**如何构建一个面向 Java 企业级场景的 Agent 运行时，使其满足以下要求：**

1. **生命周期可控** — Agent 从创建到销毁的每个阶段可被监控和干预
2. **上下文可传播** — 请求级上下文（用户信息、租户、会话）在 Agent 执行链路中自动透传
3. **状态可管理** — Agent 执行过程中的中间状态可持久化、可恢复、可查询
4. **工具可扩展** — Tool 注册、发现、调用机制标准化，支持热插拔
5. **输出可流式** — 支持 SSE 流式输出，前端实时渲染
6. **执行可追踪** — 每次 Agent 调用的完整链路可被记录和回放

### 2.2 约束条件

| 约束 | 说明 |
|------|------|
| 技术栈 | Spring Boot 3 + Spring AI，不引入额外 AI 框架依赖 |
| 部署形态 | 单体应用，不依赖微服务基础设施 |
| 性能 | 单实例支撑 100+ 并发 Agent 会话 |
| 兼容性 | 兼容 OpenAI Chat Completions API 协议的模型均可接入 |

---

## 3. 设计目标

| 优先级 | 目标 | 衡量标准 |
|--------|------|----------|
| P0 | **最小闭环** | Agent 接收 prompt → 调用 LLM → 返回响应，端到端可用 |
| P0 | **Tool Calling** | Agent 可调用注册的 Tool，处理 Tool 返回结果，支持多轮调用 |
| P0 | **流式输出** | SSE 流式推送，首 token 延迟 < 500ms |
| P1 | **上下文管理** | 会话级 Context 自动维护，支持 Message Window 策略 |
| P1 | **执行追踪** | 每次调用记录 input/output/tool_calls/latency/token_usage |
| P2 | **多模型路由** | 同一 Agent 可配置多个模型，支持 failover 和负载均衡 |
| P2 | **中断与恢复** | 长时间运行的 Agent 可被中断，支持从断点恢复 |

---

## 4. 非目标

以下能力**不在本 RFC 范围内**，将在后续 RFC 或版本中单独设计：

| 非目标 | 原因 | 计划版本 |
|--------|------|----------|
| 多 Agent 协同 | 单 Agent + Workflow 已覆盖多数场景 | v0.3+ |
| Sandbox 执行 | 安全风险高，Tool 层面做限制即可 | v1.0+ |
| 自动 Prompt 优化 | 业务场景驱动，不做通用优化 | - |
| 流量调度 / 限流 | 属于平台层能力，不属于 Runtime | v1.0 |
| 模型训练 / 微调 | 平台定位为推理侧 | - |

---

## 5. 核心架构

### 5.1 分层架构

```
┌─────────────────────────────────────────────────────────────┐
│                    Agent Runtime Architecture                │
├─────────────────────────────────────────────────────────────┤
│  API Layer                                                  │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │ Chat API    │  │ Agent API   │  │ Stream API  │         │
│  │ /chat       │  │ /agents     │  │ /chat/stream│         │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘         │
├─────────┼────────────────┼────────────────┼─────────────────┤
│  Engine Layer                                               │
│  ┌──────▼──────────────────▼──────────────────▼──────┐      │
│  │              AgentExecutor                         │      │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐          │      │
│  │  │ Context  │ │ Prompt   │ │ Response │          │      │
│  │  │ Builder  │ │ Compiler │ │ Handler  │          │      │
│  │  └──────────┘ └──────────┘ └──────────┘          │      │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐          │      │
│  │  │ Tool     │ │ Memory   │ │ Stream   │          │      │
│  │  │ Router   │ │ Manager  │ │ Emitter  │          │      │
│  │  └──────────┘ └──────────┘ └──────────┘          │      │
│  └───────────────────────────────────────────────────┘      │
├─────────────────────────────────────────────────────────────┤
│  Model Layer                                                │
│  ┌───────────────────────────────────────────────────┐      │
│  │              ModelRouter                           │      │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐          │      │
│  │  │ OpenAI   │ │ Tongyi   │ │ DeepSeek │ ...      │      │
│  │  └──────────┘ └──────────┘ └──────────┘          │      │
│  └───────────────────────────────────────────────────┘      │
├─────────────────────────────────────────────────────────────┤
│  Infrastructure Layer                                       │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐      │
│  │ Tracer   │ │ Metrics  │ │ EventBus │ │ Storage  │      │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘      │
└─────────────────────────────────────────────────────────────┘
```

### 5.2 核心组件

| 组件 | 职责 | 接口 |
|------|------|------|
| `AgentExecutor` | Agent 执行入口，编排整个执行流程 | `AgentResponse execute(AgentRequest)` |
| `ContextBuilder` | 构建执行上下文，聚合 SystemPrompt / History / Variables | `AgentContext build(ExecutionContext)` |
| `PromptCompiler` | 将模板化的 Prompt 编译为最终消息序列 | `List<Message> compile(AgentContext)` |
| `ToolRouter` | 解析 LLM 返回的 tool_calls，路由到对应 Tool 执行 | `ToolResult route(ToolCall)` |
| `MemoryManager` | 管理会话历史，实现 Message Window / Summary 等策略 | `List<Message> recall(sessionId)` |
| `StreamEmitter` | 管理 SSE 连接，推送流式 token | `void emit(StreamEvent)` |
| `ModelRouter` | 根据 Agent 配置选择目标模型，处理 failover | `ChatModel route(AgentConfig)` |
| `ResponseHandler` | 处理 LLM 响应，判断是否需要 Tool 调用循环 | `AgentResponse handle(ChatResponse)` |

### 5.3 类图

```java
// 核心接口定义
public interface AgentExecutor {
    AgentResponse execute(AgentRequest request);
    Flux<StreamEvent> executeStream(AgentRequest request);
}

public interface AgentLifecycle {
    void onCreate(AgentDefinition definition);
    void onStart(AgentExecutionContext context);
    void onToolCall(AgentExecutionContext context, ToolCall toolCall);
    void onToolResult(AgentExecutionContext context, ToolResult result);
    void onResponse(AgentExecutionContext context, AgentResponse response);
    void onError(AgentExecutionContext context, Throwable error);
    void onComplete(AgentExecutionContext context);
}

public interface AgentInterceptor {
    boolean supports(AgentDefinition definition);
    AgentRequest preHandle(AgentRequest request, AgentDefinition definition);
    void postHandle(AgentResponse response, AgentExecutionContext context);
    void afterCompletion(AgentExecutionContext context, @Nullable Throwable error);
}
```

---

## 6. 生命周期

### 6.1 状态机

```
                    ┌──────────┐
                    │  CREATED │  Agent 定义加载完成
                    └────┬─────┘
                         │ onStart()
                    ┌────▼─────┐
                    │ RUNNING  │  正在执行
                    └────┬─────┘
                         │
              ┌──────────┼──────────┐
              │          │          │
         onToolCall()  onResponse()  onError()
              │          │          │
         ┌────▼────┐ ┌───▼────┐ ┌──▼──────┐
         │TOOL_CALL│ │COMPLETED│ │ ERROR   │
         └────┬────┘ └────────┘ └─────────┘
              │
         Tool 执行完成
              │
         ┌────▼────┐
         │ RUNNING │  回到执行态（多轮 Tool 调用）
         └─────────┘
```

### 6.2 生命周期回调

```java
@Component
public class AgentTracingListener implements AgentLifecycle {

    @Override
    public void onStart(AgentExecutionContext context) {
        log.info("[Agent] 开始执行 agentId=[{}], sessionId=[{}]",
            context.getAgentId(), context.getSessionId());
        context.setAttribute("startTime", System.currentTimeMillis());
    }

    @Override
    public void onToolCall(AgentExecutionContext context, ToolCall toolCall) {
        log.info("[Agent] 调用工具 toolName=[{}], callId=[{}]",
            toolCall.getName(), toolCall.getCallId());
    }

    @Override
    public void onResponse(AgentExecutionContext context, AgentResponse response) {
        long cost = System.currentTimeMillis() - (long) context.getAttribute("startTime");
        log.info("[Agent] 执行完成 agentId=[{}], tokens=[{}], latency=[{}ms]",
            context.getAgentId(), response.getUsage().getTotalTokens(), cost);
    }

    @Override
    public void onError(AgentExecutionContext context, Throwable error) {
        log.error("[Agent] 执行异常 agentId=[{}], error=[{}]",
            context.getAgentId(), error.getMessage(), error);
    }
}
```

### 6.3 执行流程

```
User Request
    │
    ▼
┌──────────────────┐
│ 1. Context Build │  构建 AgentContext（SystemPrompt + Variables + History）
└────────┬─────────┘
         ▼
┌──────────────────┐
│ 2. Prompt Compile│  编译消息序列，注入变量
└────────┬─────────┘
         ▼
┌──────────────────┐
│ 3. Model Invoke  │  调用 LLM（流式 / 非流式）
└────────┬─────────┘
         ▼
┌──────────────────┐     ┌──────────────┐
│ 4. Response Check│────▶│ 5. Tool Call │  LLM 返回 tool_calls
└────────┬─────────┘     └──────┬───────┘
         │                      │
         │                      ▼
         │              ┌──────────────┐
         │              │ 6. Tool Exec │  执行 Tool，收集结果
         │              └──────┬───────┘
         │                      │
         │                      ▼
         │              ┌──────────────┐
         └──────────────│ 7. Append    │  将 Tool 结果追加到消息历史
                        │    to Memory │  回到步骤 3（循环）
                        └──────────────┘
         ▼
┌──────────────────┐
│ 8. Response Emit │  返回最终响应 / 流式推送
└────────┬─────────┘
         ▼
┌──────────────────┐
│ 9. Persist &     │  持久化会话历史，记录执行追踪
│    Trace         │
└──────────────────┘
```

---

## 7. Context 机制

### 7.1 Context 结构

AgentContext 是 Agent 执行过程中的核心数据载体，在整个执行链路中自动传播。

```java
public class AgentContext {

    // === 身份标识 ===
    private String agentId;           // Agent 定义 ID
    private String sessionId;         // 会话 ID（多轮对话关联）
    private String executionId;       // 本次执行唯一 ID（追踪用）
    private String traceId;           // 链路追踪 ID

    // === 请求上下文 ===
    private UserContext userContext;   // 用户信息（userId, tenantId, roles）
    private RequestContext requestContext; // HTTP 请求上下文（IP, UA, Headers）

    // === Agent 配置 ===
    private AgentDefinition agentDefinition; // Agent 定义（prompt, model, tools）
    private Map<String, Object> variables;   // 运行时变量

    // === 执行状态 ===
    private List<Message> messages;    // 消息历史（含本次执行产生的）
    private List<ToolCall> toolCalls;  // 本次执行的 Tool 调用记录
    private AgentStatus status;        // 当前状态

    // === 扩展属性 ===
    private Map<String, Object> attributes; // 自定义扩展属性
}
```

### 7.2 Context 传播

Context 在 Spring 环境中通过 `ThreadLocal` + `TransmittableThreadLocal` 传播，确保异步场景下上下文不丢失。

```java
public class AgentContextHolder {

    private static final TransmittableThreadLocal<AgentContext> HOLDER = new TransmittableThreadLocal<>();

    public static AgentContext getContext() {
        AgentContext ctx = HOLDER.get();
        if (ctx == null) {
            throw new AgentException("AgentContext not initialized");
        }
        return ctx;
    }

    public static void setContext(AgentContext context) {
        HOLDER.set(context);
    }

    public static void clear() {
        HOLDER.remove();
    }
}
```

### 7.3 变量注入

Agent Prompt 支持 `{{variable}}` 占位符，运行时由 ContextBuilder 注入。

```java
// Agent 定义
{
    "systemPrompt": "你是一个客服助手，当前用户：{{userName}}，VIP等级：{{vipLevel}}"
}

// 变量注入
AgentContext context = AgentContext.builder()
    .agentId("agent-001")
    .variable("userName", "张三")
    .variable("vipLevel", "钻石会员")
    .build();
```

---

## 8. 状态管理

### 8.1 会话状态

| 状态类型 | 存储 | 生命周期 | 说明 |
|----------|------|----------|------|
| 会话历史 | PostgreSQL | 会话级 | 多轮对话的 Message 列表 |
| 执行中间态 | Redis | 执行级 | Tool 调用过程中的中间结果 |
| Agent 定义 | PostgreSQL | 持久 | Agent 配置（prompt, model, tools） |
| 运行指标 | PostgreSQL | 持久 | token_usage, latency, call_count |

### 8.2 Message Window 策略

会话历史采用滑动窗口策略，防止 Context Window 溢出。

```java
public class MessageWindowStrategy implements MemoryStrategy {

    private final int maxMessages;      // 最大消息数
    private final int maxTokens;        // 最大 token 数
    private final boolean keepSystem;   // 是否保留 System Prompt

    @Override
    public List<Message> apply(List<Message> history, AgentContext context) {
        List<Message> result = new ArrayList<>();

        // 1. 始终保留 System Prompt
        if (keepSystem) {
            history.stream()
                .filter(m -> m.getMessageType() == MessageType.SYSTEM)
                .forEach(result::add);
        }

        // 2. 从最新消息向前截取，直到达到限制
        List<Message> nonSystem = history.stream()
            .filter(m -> m.getMessageType() != MessageType.SYSTEM)
            .collect(Collectors.toList());

        int tokenCount = 0;
        for (int i = nonSystem.size() - 1; i >= 0; i--) {
            Message msg = nonSystem.get(i);
            int tokens = tokenCounter.count(msg);
            if (tokenCount + tokens > maxTokens || result.size() >= maxMessages) {
                break;
            }
            result.add(0, msg);
            tokenCount += tokens;
        }

        return result;
    }
}
```

### 8.3 状态持久化

```sql
-- Agent 定义表
CREATE TABLE agent_definition (
    id              VARCHAR(36) PRIMARY KEY,
    name            VARCHAR(128) NOT NULL,
    system_prompt   TEXT NOT NULL,
    model_config    JSONB NOT NULL,       -- 模型配置（provider, model, temperature, etc.）
    tool_config     JSONB,                -- 工具配置列表
    memory_config   JSONB,                -- 记忆策略配置
    status          VARCHAR(16) NOT NULL, -- ACTIVE / INACTIVE / DRAFT
    tenant_id       VARCHAR(36) NOT NULL,
    created_by      VARCHAR(36) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 会话历史表
CREATE TABLE chat_message (
    id              VARCHAR(36) PRIMARY KEY,
    session_id      VARCHAR(36) NOT NULL,
    agent_id        VARCHAR(36) NOT NULL,
    role            VARCHAR(16) NOT NULL, -- SYSTEM / USER / ASSISTANT / TOOL
    content         TEXT,
    tool_calls      JSONB,                -- Tool 调用请求（assistant 消息）
    tool_call_id    VARCHAR(36),          -- Tool 结果对应的 call_id（tool 消息）
    token_count     INTEGER,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_chat_message_session ON chat_message(session_id, created_at);

-- 执行追踪表
CREATE TABLE agent_execution_trace (
    id              VARCHAR(36) PRIMARY KEY,
    execution_id    VARCHAR(36) NOT NULL UNIQUE,
    agent_id        VARCHAR(36) NOT NULL,
    session_id      VARCHAR(36) NOT NULL,
    status          VARCHAR(16) NOT NULL, -- RUNNING / COMPLETED / ERROR
    input_tokens    INTEGER,
    output_tokens   INTEGER,
    tool_call_count INTEGER,
    latency_ms      BIGINT,
    error_message   TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);
```

---

## 9. Tool 调用机制

### 9.1 Tool 调用流程

```
LLM Response (tool_calls)
    │
    ▼
┌──────────────────┐
│ 1. Parse Calls   │  解析 tool_calls JSON
└────────┬─────────┘
         ▼
┌──────────────────┐
│ 2. Route to Tool │  根据 tool_name 查找注册的 Tool
└────────┬─────────┘
         ▼
┌──────────────────┐
│ 3. Validate Args │  校验参数类型与必填项
└────────┬─────────┘
         ▼
┌──────────────────┐
│ 4. Execute Tool  │  调用 Tool 实现（支持并行）
└────────┬─────────┘
         ▼
┌──────────────────┐
│ 5. Wrap Result   │  封装为 ToolMessage 追加到历史
└────────┬─────────┘
         ▼
┌──────────────────┐
│ 6. Re-invoke LLM │  带上 Tool 结果重新调用 LLM
└──────────────────┘
```

### 9.2 Tool 协议

```java
public interface Tool {

    /** Tool 唯一名称，LLM 通过此名称调用 */
    String getName();

    /** Tool 描述，用于 LLM 理解 Tool 功能 */
    String getDescription();

    /** 参数 Schema（JSON Schema 格式） */
    ToolParameterSchema getParameterSchema();

    /** 执行 Tool */
    ToolResult execute(ToolInput input, AgentContext context);
}

public record ToolInput(String callId, String name, Map<String, Object> arguments) {}

public record ToolResult(String callId, String name, String content, boolean success) {}
```

### 9.3 并行 Tool 调用

当 LLM 一次返回多个 tool_calls 时，支持并行执行：

```java
public class DefaultToolRouter implements ToolRouter {

    private final Map<String, Tool> toolRegistry;
    private final ExecutorService toolExecutor;

    @Override
    public List<ToolResult> route(List<ToolCall> toolCalls, AgentContext context) {
        // 并行提交所有 Tool 调用
        List<CompletableFuture<ToolResult>> futures = toolCalls.stream()
            .map(call -> CompletableFuture.supplyAsync(
                () -> executeSingle(call, context), toolExecutor))
            .collect(Collectors.toList());

        // 等待所有完成
        return futures.stream()
            .map(CompletableFuture::join)
            .collect(Collectors.toList());
    }

    private ToolResult executeSingle(ToolCall call, AgentContext context) {
        Tool tool = toolRegistry.get(call.getName());
        if (tool == null) {
            return new ToolResult(call.getCallId(), call.getName(),
                "Tool not found: " + call.getName(), false);
        }
        try {
            return tool.execute(new ToolInput(call.getCallId(), call.getName(),
                call.getArguments()), context);
        } catch (Exception e) {
            return new ToolResult(call.getCallId(), call.getName(),
                "Tool execution error: " + e.getMessage(), false);
        }
    }
}
```

### 9.4 Tool 最大调用轮次

防止 LLM 无限循环调用 Tool，设置最大轮次限制：

```java
public class AgentExecutorConfig {
    /** 最大 Tool 调用轮次 */
    private int maxToolCallRounds = 10;

    /** 单轮最大并行 Tool 数 */
    private int maxParallelTools = 5;

    /** 单个 Tool 执行超时（秒） */
    private int toolExecutionTimeoutSeconds = 30;
}
```

---

## 10. Streaming 设计

### 10.1 SSE 协议

基于 Server-Sent Events 实现流式输出，事件格式遵循 OpenAI SSE 协议：

```
event: message_start
data: {"id":"exec-001","agent_id":"agent-001","model":"gpt-4o"}

event: content_delta
data: {"delta":"你"}

event: content_delta
data: {"delta":"好"}

event: tool_call_start
data: {"call_id":"call-001","name":"search_knowledge","arguments":""}

event: tool_call_delta
data: {"call_id":"call-001","arguments":"{\"query\":"}

event: tool_call_end
data: {"call_id":"call-001"}

event: tool_result
data: {"call_id":"call-001","content":"搜索结果：..."}

event: message_end
data: {"usage":{"prompt_tokens":120,"completion_tokens":85,"total_tokens":205}}
```

### 10.2 流式实现

```java
public class DefaultStreamEmitter implements StreamEmitter {

    private final SseEmitter emitter;

    @Override
    public void emit(StreamEvent event) {
        try {
            emitter.send(SseEmitter.event()
                .name(event.getType())
                .data(event.getData()));
        } catch (IOException e) {
            throw new AgentStreamException("Failed to emit stream event", e);
        }
    }
}

// Controller 层
@GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter chatStream(@RequestBody AgentRequest request) {
    SseEmitter emitter = new SseEmitter(120_000L); // 2min timeout
    agentExecutor.executeStream(request)
        .doOnNext(event -> streamEmitter.emit(event))
        .doOnComplete(emitter::complete)
        .doOnError(emitter::completeWithError)
        .subscribe();
    return emitter;
}
```

### 10.3 背压处理

流式输出采用 Reactive Streams 背压机制，防止生产者速度过快导致消费者积压：

```java
Flux<StreamEvent> stream = agentExecutor.executeStream(request)
    .onBackpressureBuffer(256, BufferOverflowStrategy.DROP_OLDEST)
    .delayElements(Duration.ofMillis(10)); // 控制推送频率
```

---

## 11. 扩展性

### 11.1 扩展点一览

| 扩展点 | 接口 | 说明 |
|--------|------|------|
| 自定义 Tool | `Tool` | 实现 Tool 接口，注册到 Spring 容器 |
| 记忆策略 | `MemoryStrategy` | 自定义会话历史截取策略 |
| 拦截器 | `AgentInterceptor` | 在 Agent 执行前后插入自定义逻辑 |
| 生命周期监听 | `AgentLifecycle` | 监听 Agent 执行各阶段事件 |
| 模型提供者 | `ModelProvider` | 接入新的 LLM 提供商 |
| Prompt 模板引擎 | `PromptTemplateEngine` | 自定义 Prompt 编译逻辑 |
| 输出解析器 | `ResponseParser` | 自定义 LLM 响应解析逻辑 |

### 11.2 Tool 注册

```java
@Component
public class WebSearchTool implements Tool {

    @Override
    public String getName() {
        return "web_search";
    }

    @Override
    public String getDescription() {
        return "搜索互联网获取最新信息";
    }

    @Override
    public ToolParameterSchema getParameterSchema() {
        return ToolParameterSchema.builder()
            .property("query", "string", "搜索关键词", true)
            .property("maxResults", "integer", "最大结果数", false)
            .build();
    }

    @Override
    public ToolResult execute(ToolInput input, AgentContext context) {
        String query = (String) input.getArguments().get("query");
        // ... 执行搜索逻辑
        return new ToolResult(input.callId(), input.name(), result, true);
    }
}
```

### 11.3 拦截器

```java
@Component
public class RateLimitInterceptor implements AgentInterceptor {

    @Override
    public boolean supports(AgentDefinition definition) {
        return true; // 对所有 Agent 生效
    }

    @Override
    public AgentRequest preHandle(AgentRequest request, AgentDefinition definition) {
        String userId = request.getUserContext().getUserId();
        if (!rateLimiter.tryAcquire(userId)) {
            throw new RateLimitExceededException("请求过于频繁");
        }
        return request;
    }

    @Override
    public void postHandle(AgentResponse response, AgentExecutionContext context) {
        // 记录 token 消耗
    }

    @Override
    public void afterCompletion(AgentExecutionContext context, Throwable error) {
        // 清理资源
    }
}
```

---

## 12. 风险分析

| 风险 | 等级 | 影响 | 缓解措施 |
|------|------|------|----------|
| LLM 无限循环调用 Tool | 高 | 资源耗尽 | maxToolCallRounds 限制 + 超时机制 |
| LLM 响应超时 | 高 | 请求挂起 | 模型调用超时配置（默认 60s） |
| Tool 执行异常 | 中 | 单次调用失败 | Tool 级别 try-catch，返回错误信息给 LLM 再次决策 |
| Context Window 溢出 | 中 | 调用失败 | MessageWindow 策略截取 + token 计数 |
| 并发会话内存压力 | 中 | OOM | 会话历史持久化，内存只保留窗口期内消息 |
| SSE 连接泄漏 | 低 | 连接数增长 | SseEmitter 超时自动关闭 + 心跳检测 |
| 模型 API 不可用 | 高 | 服务不可用 | ModelRouter 支持 failover 备选模型 |

### 12.1 超时控制矩阵

| 环节 | 超时时间 | 超时处理 |
|------|----------|----------|
| LLM 单次调用 | 60s | 返回超时错误，中断执行 |
| Tool 单次执行 | 30s | 返回 Tool 超时结果，LLM 再决策 |
| SSE 连接 | 120s | 自动关闭连接 |
| 整次 Agent 执行 | 300s | 强制中断，返回已完成部分 |

---

## 13. 后续演进

| 阶段 | 能力 | RFC |
|------|------|-----|
| v0.2 | RAG 集成（Agent + 知识库检索） | RFC-004 |
| v0.2 | MCP 协议支持（外部 Tool 生态） | RFC-005 |
| v0.2 | Memory 增强（长期记忆、摘要压缩） | RFC-006 |
| v0.3 | Workflow 引擎（Agent 作为 Workflow 节点） | RFC-002 |
| v1.0 | 多 Agent 协同（Agent 编排） | TBD |
| v1.0 | Agent Sandbox（安全隔离执行） | TBD |

---

## 附录 A：依赖关系

```
lightbot-core/agent
    ├── lightbot-core/model      (模型调用抽象)
    ├── lightbot-core/tool       (Tool 框架)
    ├── lightbot-core/memory     (会话记忆管理)
    ├── lightbot-core/common     (公共基础设施)
    └── spring-ai-openai         (Spring AI OpenAI 集成)
```

## 附录 B：配置项

```yaml
lightbot:
  agent:
    # 最大 Tool 调用轮次
    max-tool-call-rounds: 10
    # 单轮最大并行 Tool 数
    max-parallel-tools: 5
    # Tool 执行超时（秒）
    tool-timeout-seconds: 30
    # Agent 执行总超时（秒）
    execution-timeout-seconds: 300
    # SSE 连接超时（毫秒）
    sse-timeout-ms: 120000
    # 会话历史最大消息数
    max-history-messages: 50
    # 会话历史最大 token 数
    max-history-tokens: 8000
```
