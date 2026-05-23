# AI 流式对话：思考过程过滤 & Markdown 实时渲染 & 调用链监控调研

> 调研时间：2026-05-23
> 调研项目：Yuxi、spring-ai-alibaba-admin、LightBot

---

## 一、问题现状

### 1.1 思考过程泄漏

AI 返回给前端的对话中混入了思考调用工具的过程，例如：

```
好的，我来查询知识库中关于期音科技清明放假安排的信息
PausingStuck? Let's recap: I already called query_knowledge...
根据知识库中的《期音科技2026年清明节放假通知》，期音科技清明节放假3天
```

用户只需要最后的核心答案，中间的思考过程不应展示。

### 1.2 流式 Markdown 渲染问题

- 流式输出时换行符不生效，内容挤在一起
- 代码块、列表等 Markdown 元素在流式过程中显示异常
- 只有切换会话重新加载历史后才显示完整 Markdown 结构

### 1.3 缺乏 AI 调用链监控

- Token 用量硬编码为 0，未从 Spring AI 提取实际用量
- 无请求关联 ID，日志无法串联
- 无结构化的调用链追踪，排查问题靠人工翻日志

---

## 二、SSE 换行符问题根因分析（已验证）

### 2.1 数据流全链路

```
LLM 响应 → assistantMsg.getText()（含真实 \n）
→ Flux.just(text)（原样传递）
→ SseEmitter.send(chunk)（Jackson 序列化）
→ SSE wire: data:{"text":"第一行\n第二行"}（\n 被转义为 \\n）
→ 前端 processSseLines 按 \n 分割 SSE 帧
→ onChunk 接收到的 chunk 中 \n 已被 JSON 解析还原
```

### 2.2 根因定位

| 环节 | 状态 | 说明 |
|------|------|------|
| LLM 输出 | ✅ 正常 | `getText()` 返回含真实 `\n` 的字符串 |
| Flux 传递 | ✅ 正常 | `Flux.just(text)` 原样传递，无转换 |
| `SseEmitter.send()` | ⚠️ 关键 | 内部调用 `ObjectMapper.writeValueAsString()`，Jackson 将 `\n` 转义为 `\\n` |
| SSE wire format | ✅ 正确 | `\\n` 不会破坏 SSE 帧分隔 |
| 前端 SSE 解析 | ⚠️ 问题点 | `processSseLines` 按 `\n` 分割帧，然后 `data.slice(5)` 取原始文本 |

### 2.3 问题所在

**SSE wire 格式：**
```
data:第一行\n第二行\n\n
```

Jackson 将 `\n` 转义为两个字符 `\` 和 `n`。前端 `processSseLines` 按 `\n` 分割时，会把 `第一行\n第二行` 当作一整行（因为 `\\n` 不是真正的换行符）。

**前端 `chat.js` 的 `processSseLines`：**
```javascript
// 按 \n 分割
const lines = text.split('\n')
// 找到 data: 开头的行
const data = line.slice(5)  // "第一行\\n第二行"
```

此时 `data` 中的 `\\n` 是字面量字符串，不是换行符。前端需要做一次 JSON.parse 或手动替换才能还原。

### 2.4 验证结论

| 结论 | 说明 |
|------|------|
| **后端 SSE 发送正确** | Jackson JSON 转义是标准行为，不破坏 SSE 协议 |
| **前端缺少还原步骤** | 收到的 chunk 中 `\\n` 未被还原为 `\n` |
| **修复位置在前端** | `processSseLines` 中需要对 chunk 做 `replace(/\\n/g, '\n')` |

### 2.5 修复方案

```javascript
// chat.js processSseLines 中，提取 data 后还原转义
let data = line.slice(5) // 去掉 "data:" 前缀
// 还原 Jackson 的 JSON 转义
data = data.replace(/\\n/g, '\n').replace(/\\t/g, '\t').replace(/\\"/g, '"')
```

或者更稳妥的做法——前端对整个 data 做 JSON.parse（因为后端发的是 JSON 字符串）：
```javascript
try {
  data = JSON.parse(data)  // 自动还原所有转义字符
} catch {
  // 非 JSON 格式，保持原样
}
```

---

## 三、思考过程过滤方案推荐

### 3.1 两个参考项目方案对比

| | Yuxi | spring-ai-alibaba-admin |
|---|---|---|
| **后端** | 不过滤，`msg.model_dump()` 原样发送 | 从 metadata 提取 `reasoningContent` 独立字段 |
| **前端检测** | 三层：结构化字段 → `<think>` 标签 → UI 分离 | 直接读取 `reasoning_content` 字段 |
| **前端展示** | 折叠面板（默认收起） | "深度思考" 手风琴（Steps 组件） |
| **流式累积** | `useStreamSmoother` 独立缓冲区 | `chat.ts` 分别累积 content 和 reasoning |
| **依赖** | LangChain `additional_kwargs` | Spring AI `Generation.getMetadata()` |

### 3.2 推荐方案：采用 spring-ai-alibaba-admin 模式（适配 LightBot 架构）

**理由：**

1. **与 LightBot 架构一致**：LightBot 已使用 Spring AI，`Generation.getMetadata().get("reasoningContent")` 是原生支持的 API，无需额外适配
2. **复用现有 SSE 协议**：LightBot 已有 `[STATUS]` 事件机制，reasoning 内容可作为新的事件类型发送，无需改协议
3. **前后端职责清晰**：后端负责提取和分离，前端只负责展示，不需要做正则解析
4. **Yuxi 方案的前端三层检测冗余**：LightBot 不依赖 LangChain，不需要 `additional_kwargs` 兼容层

### 3.3 具体实现设计

#### 后端：ChatServiceImpl 改造

```java
// processToolCallsRecursively 中，LLM 响应处理
Generation gen = response.getResult();
AssistantMessage assistantMsg = gen.getOutput();

// 1. 提取正文
String text = assistantMsg.getText() != null ? assistantMsg.getText() : "";

// 2. 提取 reasoningContent（从 Spring AI metadata）
String reasoningContent = null;
if (assistantMsg.getMetadata() != null) {
    Object rc = assistantMsg.getMetadata().get("reasoningContent");
    if (rc != null && !rc.toString().isBlank()) {
        reasoningContent = rc.toString();
    }
}

// 3. 通过 [STATUS] 事件发送 reasoningContent
if (reasoningContent != null) {
    String event = OBJECT_MAPPER.writeValueAsString(Map.of(
        "type", "reasoning_content",
        "content", reasoningContent));
    // 发送到前端
    return Flux.concat(
        Flux.just(STATUS_PREFIX + event),
        Flux.just(text));
}
```

#### 前端：chat.js 事件路由

```javascript
// processSseLines 中，处理 STATUS 事件
if (event.type === 'reasoning_content') {
  onToolEvent({ type: 'reasoning_content', content: event.content })
} else if (event.type === 'tool_call') {
  // ...现有逻辑
}
```

#### 前端：Chat.vue 渲染

```html
<!-- 思考过程折叠面板（默认收起） -->
<div v-if="msg._reasoningContent" class="reasoning-group">
  <div class="reasoning-header" @click="msg._reasoningExpanded = !msg._reasoningExpanded">
    <bulb-outlined class="reasoning-icon" />
    <span>深度思考</span>
    <RightOutlined :class="{ expanded: msg._reasoningExpanded }" />
  </div>
  <div v-show="msg._reasoningExpanded" class="reasoning-content">
    {{ msg._reasoningContent }}
  </div>
</div>
<!-- 正文：Markdown 渲染 -->
<div class="message-content" v-html="renderMarkdown(msg.content)" />
```

#### 兜底：保留 `<think>` 标签过滤

对于不在 metadata 中返回 reasoningContent 的模型（如某些开源模型），保留现有的 `stripThinkingContent` 作为兜底。同时将过滤掉的 thinking 内容存入 metadata 供调试：

```java
// stripThinkingContent 中，返回过滤前后的内容
record ThinkingResult(String content, String reasoning) {}

private ThinkingResult stripThinkingContent(String text) {
    // ... 正则提取
    return new ThinkingResult(cleanedContent, extractedReasoning);
}
```

---

## 四、流式 Markdown 渲染方案

### 4.1 当前技术栈

| 组件 | 版本 | 作用 |
|------|------|------|
| marked | v15 | Markdown 解析 |
| marked-highlight | v2.2.4 | highlight.js 集成 |
| highlight.js | v11.11.0 | 代码语法高亮 |

**已正确配置：** `breaks: true`、`gfm: true`

### 4.2 根因确认

换行不生效的根因是 **前端 SSE 解析未还原 Jackson 的 JSON 转义**（见第二章），而非 marked 配置问题。`breaks: true` 本身没问题，只要输入的 text 包含真实 `\n`，marked 就能正确渲染。

### 4.3 推荐改进（方案 A：保持 marked，增强流式处理）

| 改进项 | 做法 | 理由 |
|--------|------|------|
| **还原 SSE 转义** | `data = JSON.parse(data)` 或手动 replace | 根因修复，优先级最高 |
| **流式预处理** | 添加 `fixMarkdownBold` | 修复未闭合的 `**` 标记 |
| **chunk 节流** | 16ms 节流（requestAnimationFrame） | 避免高频重渲染导致卡顿 |
| **不完整代码块** | 检测未闭合的 ``` 并临时补全 | 避免代码块"吞噬"后续内容 |

**不需要替换 marked 为 markdown-it**，当前技术栈足够用。Yuxi 和 spring-ai-alibaba-admin 用 markdown-it/react-markdown 是因为它们是 React 技术栈，Vue 生态中 marked 是主流选择。

---

## 五、AI 调用链监控系统设计

### 5.1 现状评估

| 已有 | 缺失 |
|------|------|
| `[Chat][Trace]` 手动打点（22处） | 无结构化追踪，日志不可查询 |
| `ToolEventEmitter` 工具事件收集 | 事件仅存 metadata，无独立监控 |
| `MemoryLogAppender` 实时日志流 | 无指标聚合，无告警 |
| `Message.tokenCount` 字段 | 硬编码为 0，未提取实际用量 |
| `requestId` (System.nanoTime()) | 仅本地使用，未关联全链路 |

### 5.2 监控系统架构设计

```
┌─────────────────────────────────────────────────────────┐
│                    LightBot 监控体系                      │
├─────────────┬─────────────┬─────────────┬───────────────┤
│  链路追踪    │  指标采集    │  日志关联    │  成本核算     │
│  (Trace)    │  (Metrics)  │  (Log)      │  (Cost)       │
├─────────────┼─────────────┼─────────────┼───────────────┤
│ requestId   │ Micrometer  │ MDC 统一ID  │ Token 用量    │
│ Span 级打点 │ Timer/Count │ 结构化日志   │ 模型单价      │
│ 递归深度追踪 │ 分布统计     │ 全链路串联   │ 按用户/Agent  │
└─────────────┴─────────────┴─────────────┴───────────────┘
         │              │              │              │
         ▼              ▼              ▼              ▼
    ┌─────────────────────────────────────────────────────┐
    │              Langfuse（自部署）                       │
    │  Trace 瀑布图 · Token 仪表盘 · 成本报表 · 告警      │
    └─────────────────────────────────────────────────────┘
```

### 5.3 Phase 1：低成本立即可做（0 依赖变更）

#### 5.3.1 请求关联 ID（requestId）

**现状：** `String.valueOf(System.nanoTime())` 仅用于 RAG 跨线程通信。

**改造：** 在 Controller 入口生成 UUID，通过 MDC 全链路传递。

```java
// ChatController.java
@PostMapping("/stream")
public SseEmitter chatStream(@Valid @RequestBody ChatRequest request) {
    String requestId = UUID.randomUUID().toString();
    MDC.put("requestId", requestId);
    // ... 传入 chatService
}

// logback-spring.xml
<pattern>%d{HH:mm:ss.SSS} [%thread] [%X{requestId}] %-5level %logger{36} - %msg%n</pattern>
```

#### 5.3.2 Token 用量提取

**现状：** `msg.setTokenCount(0)` 硬编码。

**改造：** 从 Spring AI `ChatResponse.getMetadata().getUsage()` 提取。

```java
// ChatServiceImpl.processToolCallsRecursively 中
var usage = response.getMetadata().getUsage();
if (usage != null) {
    totalInputTokens += usage.getPromptTokens();
    totalOutputTokens += usage.getCompletionTokens();
}

// 流式结束后存入 Message
msg.setTokenCount(totalOutputTokens);
session.setTotalTokens(session.getTotalTokens() + totalOutputTokens);
```

#### 5.3.3 结构化调用事件

将散落的 `log.info("[Chat][Trace]...")` 收集为结构化对象：

```java
@Data
public class LlmTraceEvent {
    private String requestId;
    private String sessionId;
    private Long agentId;
    private String model;
    private String stage;        // "session_resolve" | "model_call" | "tool_execute" | ...
    private long durationMs;
    private int inputTokens;
    private int outputTokens;
    private String toolName;     // 仅 tool_execute 阶段
    private boolean success;
    private String errorMessage;
}
```

### 5.4 Phase 2：指标采集（Micrometer）

**依赖：** `spring-boot-starter-actuator`

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: prometheus,health
  metrics:
    tags:
      application: lightbot
```

**核心指标定义：**

| 指标名 | 类型 | Tags | 说明 |
|--------|------|------|------|
| `llm.request.duration` | Timer | model, provider, has_tools | 端到端请求耗时 |
| `llm.ttft` | Timer | model, provider | 首 Token 耗时 |
| `llm.tokens.input` | DistributionSummary | model | 输入 Token 分布 |
| `llm.tokens.output` | DistributionSummary | model | 输出 Token 分布 |
| `llm.tool.calls` | Counter | tool_name, status | 工具调用次数 |
| `llm.tool.duration` | Timer | tool_name | 工具执行耗时 |
| `llm.rag.search` | Timer | knowledge_id | RAG 检索耗时 |
| `llm.error` | Counter | error_type, model | 错误计数 |

**代码示例：**

```java
@Service
@RequiredArgsConstructor
public class LlmMetrics {
    private final MeterRegistry registry;

    public void recordRequest(String model, String provider, boolean hasTools,
                               long durationMs, int inputTokens, int outputTokens) {
        registry.timer("llm.request.duration",
                "model", model, "provider", provider, "has_tools", String.valueOf(hasTools))
            .record(durationMs, TimeUnit.MILLISECONDS);
        registry.summary("llm.tokens.input", "model", model).record(inputTokens);
        registry.summary("llm.tokens.output", "model", model).record(outputTokens);
    }

    public void recordToolCall(String toolName, String status, long durationMs) {
        registry.timer("llm.tool.duration", "tool_name", toolName)
            .record(durationMs, TimeUnit.MILLISECONDS);
        registry.counter("llm.tool.calls", "tool_name", toolName, "status", status).increment();
    }
}
```

### 5.5 Phase 3：分布式追踪（OpenTelemetry + Langfuse）

**技术选型：** Langfuse（自部署，开源，支持 OTLP）

```yaml
# docker-compose.yml
services:
  langfuse:
    image: langfuse/langfuse:latest
    ports:
      - "3000:3000"
    environment:
      - DATABASE_URL=postgresql://...
      - NEXTAUTH_SECRET=...
```

**Spring AI + OTel 集成：**

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>
```

**自定义 Span（工具调用链）：**

```java
Span toolSpan = tracer.spanBuilder("tool.execute")
    .setAttribute("tool.name", toolName)
    .setAttribute("tool.args.length", toolArgs.length())
    .startSpan();
try (Scope scope = toolSpan.makeCurrent()) {
    result = callback.call(toolArgs, context);
    toolSpan.setAttribute("tool.result.length", result.length());
} catch (Exception e) {
    toolSpan.recordException(e);
    toolSpan.setStatus(StatusCode.ERROR);
} finally {
    toolSpan.end();
}
```

### 5.6 Phase 4：告警规则

| 告警 | 条件 | 级别 |
|------|------|------|
| 高延迟 | P95 > 10s | Warning |
| 超高延迟 | P99 > 30s | Critical |
| 错误率飙升 | 5min 错误率 > 10% | Critical |
| Token 超限 | 单用户日 Token > 100K | Warning |
| 工具递归触顶 | 深度 = 10 的调用占比 > 5% | Warning |
| 限流触发 | 429 错误 > 0 | Info |

### 5.7 成本核算模型

```java
// 模型单价配置
Map<String, Double> modelPricePer1kTokens = Map.of(
    "qwen-plus", 0.008,      // ¥0.008/千Token
    "qwen-turbo", 0.002,
    "deepseek-chat", 0.001,
    "gpt-4o", 0.035
);

// 每次请求计算成本
double cost = (inputTokens + outputTokens) / 1000.0
    * modelPricePer1kTokens.getOrDefault(model, 0.0);

// 按用户/天聚合
registry.gauge("llm.cost.daily", tags, dailyCostAccumulator);
```

---

## 六、实施优先级总览

| 阶段 | 任务 | 优先级 | 预估工时 | 依赖 |
|------|------|--------|----------|------|
| **P0** | 前端 SSE 解析还原 `\n`（换行修复） | P0 | 30min | 无 |
| **P0** | Token 用量从 ChatResponse 提取 | P0 | 30min | 无 |
| **P0** | requestId + MDC 全链路关联 | P0 | 1h | 无 |
| **P1** | 后端提取 reasoningContent + STATUS 事件 | P1 | 2h | P0 |
| **P1** | 前端思考过程折叠面板 | P1 | 1.5h | P1 |
| **P1** | 流式 Markdown 预处理（fixMarkdownBold） | P1 | 1h | P0 |
| **P1** | Micrometer 指标采集 | P1 | 半天 | 无 |
| **P2** | OpenTelemetry + Langfuse 部署 | P2 | 1-2天 | P1 |
| **P2** | 成本核算 + 模型单价配置 | P2 | 2h | P0 |
| **P2** | Grafana 仪表盘 | P2 | 半天 | P1 |
| **P3** | RAG 专项指标（向量检索延迟等） | P3 | 半天 | P1 |
| **P3** | 告警规则配置 | P3 | 2h | P2 |

---

## 七、参考资源

| 项目 | 关键文件 | 内容 |
|------|----------|------|
| Yuxi | `web/src/components/AgentMessageComponent.vue` | 三层 thinking 检测 + 折叠面板 |
| Yuxi | `web/src/utils/markdown_preview.js` | markdown-it 配置 + shiki |
| Yuxi | `web/src/composables/useStreamSmoother.js` | 流式节奏控制 |
| spring-ai-alibaba-admin | `BasicAgentExecutor.java:L482-490` | reasoningContent 提取 |
| spring-ai-alibaba-admin | `ChatMessage.java:L73-75` | reasoning_content 字段定义 |
| spring-ai-alibaba-admin | `SparkChat/libs/chat.ts:L178-184` | 前端累积 reasoning |
| spring-ai-alibaba-admin | `@spark-ai/chat/dist/markdown/utils.js` | fixMarkdownBold 预处理 |
| LightBot | `ChatServiceImpl.java:L246-275` | SSE 文本生成链路 |
| LightBot | `ChatController.java:L55` | SseEmitter.send() 调用点 |
| LightBot | `chat.js:L47-75` | 前端 SSE 解析 |
| OTel | [GenAI Semantic Conventions](https://opentelemetry.io/docs/specs/semconv/gen-ai/) | LLM 追踪标准 |
| Langfuse | [Self-hosted](https://langfuse.com/docs/deployment/self-host) | 开源 LLM 可观测平台 |
