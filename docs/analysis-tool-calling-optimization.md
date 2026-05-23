# AI 工具调用链路优化分析

> 对比 Yuxi、spring-ai-alibaba-admin 的工具调用与流式架构，分析 LightBot 的问题并给出方案

---

## 一、问题现状

### 1.1 链路耗时对比

| 项目 | 工具检测 | 流式输出 | 总耗时 | LLM调用次数 |
|------|---------|---------|--------|------------|
| **LightBot** | 20240ms | 24373ms | **44837ms** | **2次** |
| **spring-ai-alibaba-admin** | ~5s | ~5s | **~10s** | **1次** |
| **Yuxi** | ~3s | ~7s | **~10s** | **1次** |

### 1.2 LightBot 当前链路

```
用户消息
  ↓
[1] chatModel.call() — 非流式，检测是否需要工具 → 20s ← 浪费！
  ↓
  ├─ 有工具 → 执行工具 → [2] chatModel.stream() — 流式输出 → 24s
  └─ 无工具 → [2] chatModel.stream() — 流式输出 → 24s
                      ↑
              两次独立的LLM调用，总耗时 = 20+24 = 44s
```

**根因：工具检测和流式输出是两次独立的 LLM 调用。**

---

## 二、spring-ai-alibaba-admin 的解决方案

### 2.1 核心架构：单次流式调用 + 递归工具循环

```java
// BasicAgentExecutor.java — 核心流式入口
return chatClientBuilder.build()
    .prompt(prompt)
    .stream()                    // ← 只调用一次 stream()
    .chatResponse()
    .concatMap(response ->       // ← 每个响应片段都经过工具检测
        processToolCallsRecursively(...)
    );
```

**关键设计：`concatMap` 将流式响应的每个片段都送入工具检测函数。**

### 2.2 递归工具循环

```java
private Flux<AgentResponse> processToolCallsRecursively(...) {
    if (!response.hasToolCalls()) {
        // 无工具 → 直接输出文本
        return convertResponse(response).flux();
    }

    return Flux.concat(
        // 1. 发送工具调用事件给前端
        convertResponse(response).flux(),
        // 2. 执行工具
        Mono.fromCallable(() -> {
            ToolExecutionResult result = toolCallingManager.executeToolCalls(prompt, response);
            return result;
        }).flux().flatMap(result -> {
            // 3. 用工具结果重新调用LLM（流式）
            Prompt newPrompt = new Prompt(result.conversationHistory(), chatOptions);
            return Flux.concat(
                Flux.just(toolResultEvent),
                chatClientBuilder.build().prompt(newPrompt).stream().chatResponse()
                    .concatMap(newResponse -> processToolCallsRecursively(...))  // ← 递归
            );
        })
    );
}
```

### 2.3 工具注册：CompositeToolCallbackProvider

```java
// 四源聚合：Plugin + MCP + Agent组件 + Workflow组件
public class CompositeToolCallbackProvider implements ToolCallbackProvider {
    private final List<ToolCallback> allCallbacks;

    public CompositeToolCallbackProvider(PluginService pluginService,
                                          McpServerService mcpService,
                                          AppComponentManager appComponentManager,
                                          AgentConfig config) {
        // 1. Plugin工具（OpenAPI定义）
        List<ToolCallback> pluginTools = resolvePluginTools(pluginService, config);
        // 2. MCP工具
        List<ToolCallback> mcpTools = resolveMcpTools(mcpService, config);
        // 3. Agent组件（子Agent作为工具）
        List<ToolCallback> agentTools = resolveAgentTools(appComponentManager, config);
        // 4. Workflow组件
        List<ToolCallback> workflowTools = resolveWorkflowTools(appComponentManager, config);

        this.allCallbacks = new ArrayList<>();
        this.allCallbacks.addAll(pluginTools);
        this.allCallbacks.addAll(mcpTools);
        this.allCallbacks.addAll(agentTools);
        this.allCallbacks.addAll(workflowTools);
    }
}
```

### 2.4 工具执行控制

```java
// 关键：禁用Spring AI内置工具执行，自己管理
ToolCallingChatOptions chatOptions = buildChatOptions(config);
chatOptions.setInternalToolExecutionEnabled(false);  // ← 手动控制

// 工具回调设置在 options 上，不是 Advisor
ToolCallback[] toolCallbacks = toolCallbackProvider.getToolCallbacks();
chatOptions.setToolCallbacks(Arrays.stream(toolCallbacks).toList());
```

### 2.5 系统提示词

```java
// 构建方式：用户配置的 instructions + 变量模板
private SystemMessage buildInstructions(AgentConfig config, AgentRequest request) {
    String instructions = config.getInstructions();
    if (StringUtils.isBlank(instructions)) {
        instructions = commonConfig.getFileSearchPrompt();  // 兜底
    }
    // 支持变量替换
    return new SystemPromptTemplate(instructions).createMessage(variables);
}
```

### 2.6 总结：为什么快

| 设计点 | 效果 |
|--------|------|
| **单次 stream() 调用** | 工具检测和文本生成在同一个 LLM 调用中完成 |
| **concatMap 递归** | 工具执行后自动重新调用 LLM，无需手动管理循环 |
| **internalToolExecutionEnabled(false)** | 自己控制工具执行，可以发送中间状态事件 |
| **ChatClient + Advisor** | RAG通过Advisor链注入，不额外调用LLM |

---

## 三、Yuxi 的解决方案

### 3.1 核心架构：LangGraph 状态图 + 中间件链

```python
# chat_service.py — 流式入口
async for mode, payload in graph.astream(
    {"messages": messages},
    stream_mode=["messages", "values"],  # ← 单次流式调用
    context=context,
):
    yield mode, payload
```

**关键设计：LangGraph 的 StateGraph 自动处理工具调用循环。**

### 3.2 图结构

```
START → before_agent → before_model → model → [after_model] → tools → [循环回model] → END
                                    ↑                                          |
                                    └──────────────────────────────────────────┘
```

`model` 节点调用 LLM，如果返回工具调用，自动路由到 `tools` 节点执行，然后循环回 `model`。

### 3.3 中间件链（核心创新）

```python
middlewares = [
    FilesystemMiddleware,           # 文件系统操作
    KnowledgeBaseMiddleware,        # 知识库工具注入
    RuntimeConfigMiddleware,        # 工具筛选（根据用户配置）
    SkillsMiddleware,               # 动态技能激活
    subagents_middleware,           # 子Agent
    summary_middleware,             # 对话摘要
    TodoListMiddleware,             # 任务列表
    PatchToolCallsMiddleware,       # 工具调用修补
    ModelRetryMiddleware,           # 模型重试
]
```

每个中间件可以在以下钩子中注入逻辑：
- `awrap_model_call`：修改模型参数、工具列表、系统消息
- `awrap_tool_call`：修改工具执行行为
- `abefore_agent` / `aafter_agent`：Agent生命周期钩子

### 3.4 工具注册：@tool 装饰器 + 全局注册表

```python
# registry.py
_extra_registry: dict[str, ToolExtraMetadata] = {}
_all_tool_instances: list = []

@tool
def calculator(a: float, b: float, operation: str) -> str:
    """执行数学运算"""
    ...

# @tool 装饰器自动将工具注册到 _all_tool_instances
```

### 3.5 工具筛选：RuntimeConfigMiddleware

```python
class RuntimeConfigMiddleware:
    async def awrap_model_call(self, request: ModelRequest, context):
        # 从用户配置中读取启用的工具名
        enabled_tool_names = context.tools  # ["tavily_search", "calculator"]

        # 匹配已注册的工具实例
        filtered_tools = [
            tool for tool in _all_tool_instances
            if tool.name in enabled_tool_names
        ]

        # 加上MCP工具
        for server_name in context.mcps:
            mcp_tools = get_enabled_mcp_tools(server_name)
            filtered_tools.extend(mcp_tools)

        # 覆盖请求中的工具列表
        request.override(tools=filtered_tools)
        return await super().awrap_model_call(request, context)
```

### 3.6 知识库工具：KnowledgeBaseMiddleware

```python
class KnowledgeBaseMiddleware:
    async def awrap_model_call(self, request: ModelRequest, context):
        # 动态注入知识库工具
        kb_tools = [list_kbs, get_mindmap, query_kb]
        request.override(tools=request.tools + kb_tools)
        return await super().awrap_model_call(request, context)
```

### 3.7 系统提示词

```python
# 基础提示词
PROMPT = """
你是一个交互式智能体"语析"。
专门用来回答用户的问题...
<| 文件系统约束 |>...
<| 知识库访问 |>...
"""

# 运行时注入时间
cur_datetime = f"当前时间：{shanghai_now().strftime('%Y-%m-%d %H:%M:%S')}"
system_prompt = f"{cur_datetime}\n\n{system_prompt}"

# 追加用户自定义提示词
system_prompt = build_prompt_with_context(base_prompt, context.system_prompt)
```

### 3.8 总结：为什么快

| 设计点 | 效果 |
|--------|------|
| **LangGraph 单次 astream()** | 工具调用循环由图引擎自动管理 |
| **中间件链** | 工具筛选、知识库注入都在同一次LLM调用内完成 |
| **@tool 装饰器** | 工具注册零配置，启动时自动收集 |
| **StreamingResponse (NDJSON)** | 无SSE开销，直接流式推送 |

---

## 四、LightBot 的两个核心问题

### 问题1：为什么工具调用判定和流式调用这么慢

**根因：两次独立的 LLM 调用**

```
LightBot:   call(检测工具) 20s + stream(生成回复) 24s = 44s
Admin:      stream(检测+生成) 10s = 10s
Yuxi:       astream(检测+生成) 10s = 10s
```

| 对比项 | LightBot | spring-ai-alibaba-admin | Yuxi |
|--------|----------|------------------------|------|
| LLM调用次数 | 2次 | 1次 | 1次 |
| 工具检测方式 | 独立 call() | stream() 内 concatMap | graph astream |
| 工具执行后 | 重新 stream() | 递归 concatMap | 图引擎自动循环 |
| RAG注入方式 | 工具调用 query_knowledge | Advisor链 pre-call | 中间件 pre-call |

### 问题2：为什么大模型不知道调用工具

**根因：系统提示词缺乏工具使用引导**

| 对比项 | LightBot | spring-ai-alibaba-admin | Yuxi |
|--------|----------|------------------------|------|
| 系统提示词 | "你是 LightBot 智能助手，请用中文回答用户问题。" | 用户自定义 instructions + 变量模板 | 基础prompt + 用户prompt + 时间注入 |
| 工具使用引导 | **无** | 通过工具description引导 | 通过工具description引导 |
| 知识库工具 | query_knowledge（需模型主动调用） | KnowledgeBaseRetrievalAdvisor 自动注入 | KnowledgeBaseMiddleware 自动注入 |
| 工具description | 有（@Tool注解） | 有（OpenAPI/MCP schema） | 有（@tool装饰器） |

**LightBot 的 `query_knowledge` 工具 description 是：**
> "搜索智能体绑定的知识库，获取与问题相关的文档内容。当用户问题涉及特定领域知识、需要查找文档资料时调用此工具。"

这个描述本身没问题，但系统提示词完全没有提及"你有工具可用"或"你应该优先使用工具"。

---

## 五、LightBot 改造方案

### 5.1 方案总览

参考 spring-ai-alibaba-admin 的架构（Java/Spring AI 生态更匹配），做以下改造：

```
改造前:
  call(检测) → stream(输出) = 2次LLM调用 = 44s

改造后:
  stream(检测+输出) + 工具递归 = 1次LLM调用 = ~15s
```

### 5.2 改造点1：合并工具检测和流式输出为单次调用

**参考 spring-ai-alibaba-admin 的 `processToolCallsRecursively` 模式：**

```java
// 改造后的 chatStream 核心逻辑
@Override
public Flux<String> chatStream(ChatRequest request) {
    // ... 前置准备（解析会话、加载Agent、构建消息）...

    ToolCallingChatOptions toolOptions = buildChatOptionsWithTools(...);
    toolOptions.setInternalToolExecutionEnabled(false);  // 手动控制工具执行

    // 单次流式调用，工具检测在流中完成
    Prompt prompt = new Prompt(messages, toolOptions);
    return chatModel.stream(prompt)
        .concatMap(response -> processToolCallsRecursively(
            chatModel, response, messages, toolOptions, ...));
}

private Flux<String> processToolCallsRecursively(
        ChatModel chatModel, ChatResponse response,
        List<Message> messages, ToolCallingChatOptions options, ...) {

    if (!response.hasToolCalls()) {
        // 无工具调用 → 直接输出文本
        String text = response.getResult().getOutput().getText();
        return Flux.just(text);
    }

    // 有工具调用 → 执行工具 → 重新调用LLM
    return Flux.concat(
        // 1. 发送工具调用事件
        Flux.concat(toolCalls.stream()
            .map(tc -> Flux.just(STATUS_PREFIX + generateToolCallEvent(tc)))
            .toList()),
        // 2. 执行工具
        Mono.fromCallable(() -> executeTools(toolCalls, messages)).flux()
            .flatMap(toolResults -> {
                // 3. 重新调用LLM（流式）
                Prompt newPrompt = new Prompt(messages, options);
                return chatModel.stream(newPrompt)
                    .concatMap(nextResponse ->
                        processToolCallsRecursively(chatModel, nextResponse, messages, options, ...));
            })
    );
}
```

**预期效果：**
- 无工具场景：1次 stream 调用，~10-15s（取决于模型速度）
- 有工具场景：1次 stream + N次 stream（每轮工具后），总耗时 = 单次LLM时间 × (1 + 工具轮数)

### 5.3 改造点2：优化系统提示词

```java
// 当前（太弱）
private static final String DEFAULT_SYSTEM_PROMPT = "你是 LightBot 智能助手，请用中文回答用户问题。";

// 改进后
private static final String DEFAULT_SYSTEM_PROMPT = """
你是 LightBot 智能助手。请根据用户的提问，利用可用的工具来提供准确、详细的回答。

## 工具使用原则
- 当用户问题涉及知识库内容时，必须先调用 query_knowledge 工具检索相关文档
- 当用户需要精确计算时，调用 calculator 工具
- 当用户询问实时信息（天气、新闻、股价等）时，调用 web_search 工具
- 当用户需要查询数据库时，调用 pg_query 工具
- 调用工具后，基于工具返回的结果来回答用户，不要编造信息

## 回答规范
- 使用中文回答
- 回答应简洁准确
- 如果工具返回了参考文献，在回答末尾标注来源
- 遇到不确定的信息请如实告知
""";
```

### 5.4 改造点3：Agent配置中增加工具使用提示词

在 Agent 详情页的"系统提示词"中，自动追加工具使用引导：

```java
// buildMessages 中追加
if (!toolCallbackMap.isEmpty()) {
    String toolGuide = buildToolGuide(toolCallbackMap);
    systemPrompt = toolGuide + "\n\n" + systemPrompt;
}

private String buildToolGuide(Map<String, ToolCallback> tools) {
    StringBuilder sb = new StringBuilder("## 可用工具\n");
    for (var entry : tools.entrySet()) {
        sb.append("- ").append(entry.getKey()).append(": ")
          .append(entry.getValue().getToolDefinition().description()).append("\n");
    }
    return sb.toString();
}
```

### 5.5 改造点4：借鉴 Yuxi 的知识库中间件模式

当前 LightBot 的知识库查询是通过 `query_knowledge` 工具让模型主动调用。可以参考 Yuxi 的 `KnowledgeBaseMiddleware`，在工具检测之前自动注入知识库上下文：

```java
// 在 buildMessages 中，如果Agent绑定了知识库，自动检索并注入system prompt
if (agent != null) {
    List<Long> knowledgeIds = agentService.getKnowledgeIds(agent.getId());
    if (!knowledgeIds.isEmpty()) {
        // 自动检索相关文档（不需要模型决定）
        List<Map<String, Object>> docs = autoRetrieveDocs(knowledgeIds, userMessage);
        if (!docs.isEmpty()) {
            String context = formatDocsContext(docs);
            messages.add(0, new SystemMessage("以下是与用户问题相关的参考文档：\n" + context
                + "\n\n请基于以上文档回答用户问题。"));
        }
    }
}
```

这样做的好处：
- 不依赖模型主动调用 `query_knowledge` 工具
- 知识库检索在 LLM 调用之前完成，不占用 LLM 时间
- 模型直接看到文档内容，回答更准确

### 5.6 改造优先级

| 优先级 | 改造点 | 预期效果 | 工作量 |
|--------|--------|---------|--------|
| **P0** | 合并工具检测和流式输出 | 耗时减半（44s→22s） | 中 |
| **P0** | 优化系统提示词 | 模型更愿意使用工具 | 小 |
| **P1** | Agent级工具使用提示词 | 进一步引导模型 | 小 |
| **P1** | 知识库自动注入（参考Yuxi） | 不依赖模型主动调用工具 | 中 |
| **P2** | 工具类型标记（参考Admin） | 前端展示优化 | 小 |

---

## 六、关键代码参考

### spring-ai-alibaba-admin 核心文件

| 文件 | 作用 |
|------|------|
| `BasicAgentExecutor.java` | 核心编排：构建ChatClient、消息、管理工具循环 |
| `CompositeToolCallbackProvider.java` | 四源工具聚合 |
| `AgentToolCallback.java` | 自定义ToolCallback接口（带类型标记） |
| `KnowledgeBaseRetrievalAdvisor.java` | RAG Advisor（pre-call注入文档） |
| `ToolCallingManager` (Spring AI) | 工具执行管理器 |

### Yuxi 核心文件

| 文件 | 作用 |
|------|------|
| `chat_service.py` | 流式对话入口 |
| `base.py` | BaseAgent + LangGraph流式 |
| `graph.py` | ChatbotAgent图定义 + 中间件链 |
| `runtime_config_middleware.py` | 工具筛选中间件 |
| `knowledge_base_middleware.py` | 知识库工具注入 |
| `registry.py` | 工具全局注册表 |

---

## 七、总结

| 维度 | 当前LightBot | 改造后目标 |
|------|-------------|-----------|
| LLM调用次数 | 2次（检测+输出） | 1次（stream内检测+输出） |
| 工具检测 | 独立call() 20s | stream() 内concatMap 0s额外开销 |
| 系统提示词 | 无工具引导 | 工具使用原则 + 可用工具列表 |
| 知识库 | 模型主动调用工具 | 自动注入 + 工具双保险 |
| 预期总耗时 | 44s | 10-15s |
