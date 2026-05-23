package com.lightbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.constant.ConfigKeys;
import com.lightbot.dto.ChatRequest;
import com.lightbot.dto.RagReferenceVO;
import com.lightbot.entity.Agent;
import com.lightbot.tool.ToolEventEmitter;
import com.lightbot.tool.builtintool.QueryKnowledgeTool;
import com.lightbot.entity.ChatSession;
import com.lightbot.entity.Knowledge;
import com.lightbot.entity.LlmTrace;
import com.lightbot.entity.Message;
import com.lightbot.dto.LlmTraceSpan;
import com.lightbot.enums.ContentType;
import com.lightbot.enums.MessageRole;
import com.lightbot.mapper.MessageMapper;
import com.lightbot.model.ModelFactory;
import com.lightbot.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * AI对话服务实现类
 *
 * @author finch
 * @since 2026-05-19
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final MessageMapper messageMapper;
    private final ChatSessionService chatSessionService;
    private final AgentService agentService;
    private final EmbeddingService embeddingService;
    private final KnowledgeService knowledgeService;
    private final EmbeddingModel embeddingModel;
    private final ModelFactory modelFactory;
    private final ToolService toolService;
    private final TaskExecutor taskExecutor;
    private final LlmTraceService llmTraceService;

    private static final String DEFAULT_SYSTEM_PROMPT = """
            你是 LightBot 智能助手。请根据用户的提问，利用可用的工具来提供准确、详细的回答。

            ## 工具使用原则
            - 当工具返回了检索结果或参考资料时，必须基于这些结果来回答用户，不要忽略工具返回的内容
            - 调用工具后，将工具返回的结果作为回答的主要依据
            - 如果工具返回了参考文献，在回答末尾标注来源
            - 如果工具返回"未找到相关内容"，再根据自身知识回答并说明知识库中未找到

            ## 回答规范
            - 使用中文回答
            - 回答应简洁准确
            - 遇到不确定的信息请如实告知

            ## 输出格式要求（必须严格遵守，这是最重要的规则）
            你必须使用标准 Markdown 格式输出，严禁输出纯文本段落。

            ### 列表格式（必须使用）
            当回答包含多个要点、步骤、特征时，必须使用列表：
            - 无序列表用 `- ` 开头（注意短横线后有空格）
            - 有序列表用 `1. ` `2. ` `3. ` 开头
            - 每个列表项单独一行，不要合并到同一段落

            ### 标题格式
            - 一级标题：`# 标题`
            - 二级标题：`## 标题`
            - 三级标题：`### 标题`

            ### 表格格式
            当涉及对比、参数、多维信息时使用表格：
            | 列1 | 列2 | 列3 |
            |-----|-----|-----|
            | 值1 | 值2 | 值3 |

            ### 其他格式
            - 重点内容使用 `**加粗**` 标记
            - 代码使用 `` `代码` `` 或代码块
            - 段落之间空一行
            """;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 状态消息前缀，前端通过此前缀识别状态消息 */
    private static final String STATUS_PREFIX = "[STATUS]";
    private static final String DONE_PREFIX = "[DONE]";

    /** metadata消息前缀，前端通过此前缀识别metadata消息 */
    private static final String METADATA_PREFIX = "[METADATA]";

    /** 并行检索线程池 */
    private static final ExecutorService RAG_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "rag-search");
        t.setDaemon(true);
        return t;
    });

    @Override
    public String chat(ChatRequest request) {
        // 1. 解析会话ID，无则新建
        Long sessionId = resolveSessionId(request.getSessionId(), request.getAgentId());

        // 2. 加载Agent配置
        Agent agent = loadAgent(request.getAgentId());

        // 3. 解析config获取providerId和配置
        Map<String, Object> configMap = parseConfig(agent != null ? agent.getConfig() : null);
        Long providerId = getProviderId(configMap);

        // 4. 构建消息列表（含系统提示词 + RAG上下文 + 历史消息）
        List<org.springframework.ai.chat.messages.Message> messages = buildMessages(sessionId, request.getMessage(), agent);

        // 5. 通过 ModelFactory 获取 ChatModel，构建 ChatOptions（含工具）
        ChatModel chatModel = modelFactory.getChatModel(providerId);
        ChatOptions options = buildChatOptionsWithTools(providerId, configMap, agent);

        log.info("[Chat] 用户消息: sessionId={}, agentId={}, message={}", sessionId,
                agent != null ? agent.getId() : null, request.getMessage());

        // 6. 调用模型获取回复
        ChatResponse response = chatModel.call(new Prompt(messages, options));
        String reply = response.getResult().getOutput().getText();

        log.info("[Chat] AI回复: sessionId={}, length={}", sessionId, reply != null ? reply.length() : 0);

        // 7. 持久化用户消息和AI回复
        saveMessage(sessionId, MessageRole.USER, request.getMessage());
        saveMessage(sessionId, MessageRole.ASSISTANT, reply);

        // 8. 异步生成标题
        taskExecutor.execute(() -> generateTitle(sessionId));

        return reply;
    }

    @Override
    public Flux<String> chatStream(ChatRequest request) {
        long t0 = System.currentTimeMillis();
        List<LlmTraceSpan> spans = new ArrayList<>();

        // 1. 解析会话ID
        Long sessionId = resolveSessionId(request.getSessionId(), request.getAgentId());
        long t1 = System.currentTimeMillis();
        log.info("[Chat][Trace] 会话解析: {}ms, sessionId={}", t1 - t0, sessionId);
        spans.add(buildSpan("s1", null, "session_resolve", t0, t1 - t0, "OK", Map.of("sessionId", sessionId)));

        // 2. 加载Agent配置
        Agent agent = loadAgent(request.getAgentId());
        long t2 = System.currentTimeMillis();
        log.info("[Chat][Trace] Agent加载: {}ms, agentId={}", t2 - t1, agent != null ? agent.getId() : null);
        spans.add(buildSpan("s2", "s1", "agent_load", t1, t2 - t1, "OK",
                Map.of("agentId", agent != null ? agent.getId() : null, "agentName", agent != null ? agent.getName() : null)));

        // 3. 解析config获取providerId和配置
        Map<String, Object> configMap = parseConfig(agent != null ? agent.getConfig() : null);
        Long providerId = getProviderId(configMap);

        // 4. 保存用户消息
        saveMessage(sessionId, MessageRole.USER, request.getMessage());
        long t3 = System.currentTimeMillis();
        log.info("[Chat][Trace] 消息持久化: {}ms", t3 - t2);
        spans.add(buildSpan("s3", "s1", "build_messages", t2, t3 - t2, "OK", Map.of()));

        // 5. 构建消息列表
        List<org.springframework.ai.chat.messages.Message> messages = buildMessages(sessionId, request.getMessage(), agent);
        long t4 = System.currentTimeMillis();
        log.info("[Chat][Trace] 消息构建: {}ms, 历史消息数={}", t4 - t3, messages.size() - 2);

        // 6. 获取 ChatModel + ChatOptions（含工具）
        ChatModel chatModel = modelFactory.getChatModel(providerId);
        ToolCallingChatOptions toolOptions = buildChatOptionsWithTools(providerId, configMap, agent);
        Map<String, ToolCallback> toolCallbackMap = buildToolCallbackMap(toolOptions);
        long t5 = System.currentTimeMillis();
        log.info("[Chat][Trace] 模型+工具加载: {}ms, providerId={}, 工具数={}, 工具名={}",
                t5 - t4, providerId, toolCallbackMap.size(), toolCallbackMap.keySet());
        spans.add(buildSpan("s4", "s1", "load_model_tools", t3, t5 - t3, "OK",
                Map.of("providerId", providerId, "toolCount", toolCallbackMap.size(), "toolNames", toolCallbackMap.keySet().toString())));

        // 7. 禁用内置工具执行，手动管理工具循环
        toolOptions.setInternalToolExecutionEnabled(false);
        String requestId = String.valueOf(System.nanoTime());
        String[] ragMetadataHolder = {null};
        int[] toolCallCountHolder = {0};
        int[] inputTokenHolder = {0};
        int[] outputTokenHolder = {0};
        List<Map<String, Object>> toolEventsList = new ArrayList<>();
        StringBuilder fullReply = new StringBuilder();
        StringBuilder reasoningContent = new StringBuilder();

        // 8. 单次流式调用 + 递归工具循环（参考 spring-ai-alibaba-admin 的 processToolCallsRecursively 模式）
        long tStreamStart = System.currentTimeMillis();

        // 直接进入递归：首次调用LLM → 检测工具 → 执行 → 递归
        // [DONE] 在整个流的最末尾发送，确保前端在所有内容（文本+工具事件+metadata）都收到后才触发完成
        return processToolCallsRecursively(
                        chatModel, messages, toolOptions, toolCallbackMap,
                        agent, fullReply, ragMetadataHolder, toolCallCountHolder, inputTokenHolder, outputTokenHolder, toolEventsList, requestId, 0, spans, tStreamStart, configMap, reasoningContent)
                .concatWith(Flux.just(DONE_PREFIX))
                .doOnComplete(() -> {
                    long t7 = System.currentTimeMillis();
                    log.info("[Chat][Trace] ═══════ 链路汇总 ═══════");
                    log.info("[Chat][Trace] 会话解析:      {}ms", t1 - t0);
                    log.info("[Chat][Trace] Agent加载:      {}ms", t2 - t1);
                    log.info("[Chat][Trace] 消息持久化:    {}ms", t3 - t2);
                    log.info("[Chat][Trace] 消息构建:      {}ms", t4 - t3);
                    log.info("[Chat][Trace] 模型+工具加载:  {}ms", t5 - t4);
                    log.info("[Chat][Trace] 流式输出:      {}ms, 工具调用{}次", t7 - tStreamStart, toolCallCountHolder[0]);
                    log.info("[Chat][Trace] 总耗时:        {}ms, 回复长度={}", t7 - t0, fullReply.length());
                    log.info("[Chat][Trace] ════════════════════════");
                    saveMessage(sessionId, MessageRole.ASSISTANT, fullReply.toString(), ragMetadataHolder[0], inputTokenHolder[0] + outputTokenHolder[0]);
                    taskExecutor.execute(() -> generateTitle(sessionId));

                    // 构建调用链Trace并异步写库
                    String modelName = configMap.containsKey("modelId") ? configMap.get("modelId").toString() : null;
                    long userId = 0;
                    try { userId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong(); } catch (Exception ignored) {}
                    LlmTrace trace = new LlmTrace();
                    trace.setRequestId(requestId);
                    trace.setSessionId(sessionId);
                    trace.setUserId(userId);
                    trace.setAgentId(agent != null ? agent.getId() : null);
                    trace.setAgentName(agent != null ? agent.getName() : null);
                    trace.setModel(modelName);
                    trace.setStatus("completed");
                    trace.setInputTokens(inputTokenHolder[0]);
                    trace.setOutputTokens(outputTokenHolder[0]);
                    trace.setTotalTokens(inputTokenHolder[0] + outputTokenHolder[0]);
                    trace.setToolCallCount(toolCallCountHolder[0]);
                    trace.setTotalDurationMs(t7 - t0);
                    trace.setReplyContent(fullReply.toString());
                    // 追加AI思考内容到spans
                    if (reasoningContent.length() > 0) {
                        spans.add(buildSpan("reasoning", null, "ai_reasoning", t5, t7 - t5, "OK",
                                Map.of("content", reasoningContent.toString())));
                    }
                    try {
                        trace.setSpans(OBJECT_MAPPER.writeValueAsString(spans));
                    } catch (Exception e) {
                        trace.setSpans("[]");
                    }
                    llmTraceService.recordTrace(trace);
                }).doOnError(e -> {
                    log.error("[Chat] 流式对话异常: sessionId={}, error={}", sessionId, e.getMessage(), e);

                    // 异常时也记录Trace
                    String modelName = configMap.containsKey("modelId") ? configMap.get("modelId").toString() : null;
                    long userId = 0;
                    try { userId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong(); } catch (Exception ignored) {}
                    long tErr = System.currentTimeMillis();
                    LlmTrace trace = new LlmTrace();
                    trace.setRequestId(requestId);
                    trace.setSessionId(sessionId);
                    trace.setUserId(userId);
                    trace.setAgentId(agent != null ? agent.getId() : null);
                    trace.setAgentName(agent != null ? agent.getName() : null);
                    trace.setModel(modelName);
                    trace.setStatus("failed");
                    trace.setTotalDurationMs(tErr - t0);
                    trace.setErrorMessage(e.getMessage());
                    try {
                        trace.setSpans(OBJECT_MAPPER.writeValueAsString(spans));
                    } catch (Exception ex) {
                        trace.setSpans("[]");
                    }
                    llmTraceService.recordTrace(trace);
                });
    }

    /**
     * 递归处理工具调用：调用LLM → 检测工具 → 执行 → 重新调用LLM
     * <p>关键设计：LLM调用在方法内部执行，确保工具执行完成后才输出文本，
     * 避免 outer stream 完成导致 inner chain 被取消</p>
     *
     * @param chatModel    聊天模型
     * @param messages     消息历史（会被修改：追加AssistantMessage和ToolResponseMessage）
     * @param toolOptions  工具配置选项
     * @param toolCallbackMap 工具名→回调映射
     * @param agent        当前Agent
     * @param fullReply    累积完整回复文本
     * @param ragMetadataHolder RAG引用元数据（数组包装，允许lambda修改）
     * @param toolCallCountHolder 工具调用计数（数组包装，允许lambda修改）
     * @param tokenUsageHolder Token用量累计（数组包装，允许lambda修改）
     * @param toolEventsList 所有工具执行事件列表（收集tool_call/tool_status/tool_result，用于持久化到metadata）
     * @param requestId    请求ID，用于跨线程传递工具搜索结果
     * @param depth        递归深度（防止无限循环）
     * @param spans        调用链Span列表（收集各阶段耗时）
     * @param llmCallStart 本轮LLM调用开始时间（用于计算LLM call span）
     * @param configMap    Agent配置Map（用于记录模型信息）
     * @param reasoningContent AI思考内容（累积）
     * @return Flux<String> 流式输出片段
     */
    private Flux<String> processToolCallsRecursively(
            ChatModel chatModel,
            List<org.springframework.ai.chat.messages.Message> messages,
            ToolCallingChatOptions toolOptions,
            Map<String, ToolCallback> toolCallbackMap,
            Agent agent,
            StringBuilder fullReply,
            String[] ragMetadataHolder,
            int[] toolCallCountHolder,
            int[] inputTokenHolder,
            int[] outputTokenHolder,
            List<Map<String, Object>> toolEventsList,
            String requestId,
            int depth,
            List<LlmTraceSpan> spans,
            long llmCallStart,
            Map<String, Object> configMap,
            StringBuilder reasoningContent) {

        // 防止无限循环
        if (depth >= 10) {
            log.warn("[Chat][Trace] 工具调用递归深度达到上限({})，停止循环", depth);
            return Flux.just("\n[工具调用轮次已达上限，请简化问题后重试]");
        }

        // 1. 调用LLM（流式）
        String llmSpanId = "llm_" + depth;
        Prompt prompt = new Prompt(new ArrayList<>(messages), toolOptions);
        boolean[] llmSpanAdded = {false};

        return chatModel.stream(prompt)
                .concatMap(response -> {
                    Generation gen = response.getResult();
                    AssistantMessage assistantMsg = (gen != null) ? gen.getOutput() : null;

                    // 2. 无工具调用 → 直接输出文本（结束递归）
                    if (assistantMsg == null || !assistantMsg.hasToolCalls()) {
                        String text = (assistantMsg != null) ? assistantMsg.getText() : "";
                        if (text == null) text = "";

                        // 过滤 thinking/reasoning 内容
                        if (gen != null && gen.getOutput() != null) {
                            var metadata = gen.getOutput().getMetadata();
                            if (metadata != null) {
                                Object reasoningObj = metadata.get("reasoningContent");
                                if (reasoningObj != null && !reasoningObj.toString().isBlank()) {
                                    String reasoning = reasoningObj.toString();
                                    reasoningContent.append(reasoning);
                                    return Flux.just(STATUS_PREFIX + generateReasoningEvent(reasoning));
                                }
                            }
                        }

                        // 兜底：过滤 thinking 标签内容
                        String stripped = stripThinkingTags(text);
                        if (stripped.isEmpty()) return Flux.empty();
                        text = stripped;

                        // 提取 Token 用量（每轮LLM调用都累计）
                        if (response != null && response.getMetadata() != null) {
                            org.springframework.ai.chat.metadata.Usage usage = response.getMetadata().getUsage();
                            if (usage != null) {
                                inputTokenHolder[0] += usage.getPromptTokens();
                                outputTokenHolder[0] += usage.getCompletionTokens();
                            }
                        }

                        fullReply.append(text);
                        // LLM调用Span（只添加一次）
                        if (!llmSpanAdded[0]) {
                            spans.add(buildSpan(llmSpanId, "s1", "llm_call", llmCallStart,
                                    System.currentTimeMillis() - llmCallStart, "OK",
                                    Map.of("depth", depth, "model", configMap.getOrDefault("modelId", ""),
                                            "inputTokens", inputTokenHolder[0], "outputTokens", outputTokenHolder[0],
                                            "replyPreview", fullReply.length() > 500 ? fullReply.substring(0, 500) + "..." : fullReply.toString())));
                            llmSpanAdded[0] = true;
                        }
                        return Flux.just(text);
                    }

                    // 3. 有工具调用 → 只执行第一个工具 → 将结果反馈给模型决定是否继续
                    messages.add(assistantMsg);

                    // 提取 Token 用量（每轮LLM调用都累计，包括工具调用轮次）
                    if (response != null && response.getMetadata() != null) {
                        org.springframework.ai.chat.metadata.Usage usage = response.getMetadata().getUsage();
                        if (usage != null) {
                            inputTokenHolder[0] += usage.getPromptTokens();
                            outputTokenHolder[0] += usage.getCompletionTokens();
                        }
                    }

                    List<AssistantMessage.ToolCall> toolCalls = assistantMsg.getToolCalls();
                    boolean asyncEnabled = Boolean.TRUE.equals(configMap.get("asyncToolCalls"));

                    // LLM调用Span（只添加一次）
                    if (!llmSpanAdded[0]) {
                        spans.add(buildSpan(llmSpanId, "s1", "llm_call", llmCallStart,
                                System.currentTimeMillis() - llmCallStart, "OK",
                                Map.of("depth", depth, "model", configMap.getOrDefault("modelId", ""),
                                        "toolCount", toolCalls.size(),
                                        "toolNames", toolCalls.stream().map(AssistantMessage.ToolCall::name).toList().toString())));
                        llmSpanAdded[0] = true;
                    }

                    List<Flux<String>> statusFluxes = new ArrayList<>();
                    List<Map<String, Object>> kbResultsHolder = new ArrayList<>();
                    List<org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();
                    int toolContentOffset = fullReply.length();

                    if (asyncEnabled && toolCalls.size() > 1) {
                        // 并行执行所有工具
                        log.info("[Chat][Trace] 工具调用(depth={}): {}个工具, 并行执行", depth, toolCalls.size());
                        List<CompletableFuture<String>> futures = new ArrayList<>();
                        for (AssistantMessage.ToolCall tc : toolCalls) {
                            String tcArgs = tc.arguments() != null ? tc.arguments() : "";
                            toolEventsList.add(Map.of("type", "tool_call", "toolName", tc.name(), "args",
                                    tcArgs, "contentOffset", toolContentOffset));
                            statusFluxes.add(Flux.just(STATUS_PREFIX + generateToolCallEvent(tc.name(), tcArgs, toolContentOffset)));
                            toolCallCountHolder[0]++;
                            final String tcName = tc.name();
                            final String tcId = tc.id();
                            futures.add(CompletableFuture.supplyAsync(() -> {
                                long tStart = System.currentTimeMillis();
                                String result;
                                ToolCallback cb = toolCallbackMap.get(tcName);
                                if (cb != null) {
                                    try {
                                        result = cb.call(tcArgs, new ToolContext(Map.of("agentId", agent.getId(), "requestId", requestId)));
                                    } catch (Exception e) {
                                        log.error("[Chat] 工具执行异常: name={}, error={}", tcName, e.getMessage(), e);
                                        result = "工具执行失败: " + e.getMessage();
                                    }
                                } else {
                                    result = "工具不存在: " + tcName;
                                }
                                long tEnd = System.currentTimeMillis();
                                log.info("[Chat][Trace] 工具执行结果: name={}, 耗时={}ms, resultLength={}", tcName, tEnd - tStart, result.length());
                                spans.add(buildSpan("tool_" + toolCallCountHolder[0], llmSpanId, "tool_execute",
                                        tStart, tEnd - tStart, "OK",
                                        Map.of("toolName", tcName, "args", tcArgs != null ? tcArgs : "", "resultLength", result.length())));
                                // 知识库搜索结果捕获
                                if ("query_knowledge".equals(tcName)) {
                                    List<Map<String, Object>> kbResults = QueryKnowledgeTool.getSearchResults(requestId);
                                    synchronized (kbResultsHolder) { kbResultsHolder.addAll(kbResults); }
                                }
                                toolEventsList.add(Map.of("type", "tool_result", "toolName", tcName, "result",
                                        result.length() > 2000 ? result.substring(0, 2000) + "..." : result,
                                        "contentOffset", toolContentOffset));
                                return result;
                            }, RAG_EXECUTOR));
                        }
                        // 等待所有工具完成，收集结果
                        for (int i = 0; i < toolCalls.size(); i++) {
                            AssistantMessage.ToolCall tc = toolCalls.get(i);
                            String result = futures.get(i).join();
                            toolResponses.add(new org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse(
                                    tc.id(), tc.name(), result));
                        }
                    } else {
                        // 串行执行：只执行第一个工具（默认行为）
                        AssistantMessage.ToolCall firstTool = toolCalls.get(0);
                        log.info("[Chat][Trace] 工具调用(depth={}): {}个工具, 只执行第一个: {}",
                                depth, toolCalls.size(), firstTool.name());
                        String toolName = firstTool.name();
                        String toolArgs = firstTool.arguments();
                        toolCallCountHolder[0]++;

                        String safeArgs = toolArgs != null ? toolArgs : "";
                        toolEventsList.add(Map.of("type", "tool_call", "toolName", toolName, "args",
                                safeArgs, "contentOffset", toolContentOffset));
                        statusFluxes.add(Flux.just(STATUS_PREFIX + generateToolCallEvent(toolName, safeArgs, toolContentOffset)));

                        long tToolStart = System.currentTimeMillis();
                        String toolResult;
                        ToolCallback callback = toolCallbackMap.get(toolName);
                        if (callback != null) {
                            try {
                                toolResult = callback.call(toolArgs, new ToolContext(Map.of(
                                        "agentId", agent.getId(),
                                        "requestId", requestId)));
                            } catch (Exception e) {
                                log.error("[Chat] 工具执行异常: name={}, error={}", toolName, e.getMessage(), e);
                                toolResult = "工具执行失败: " + e.getMessage();
                            }
                        } else {
                            toolResult = "工具不存在: " + toolName;
                            log.warn("[Chat][Trace] 工具不存在: name={}, 可用工具={}", toolName, toolCallbackMap.keySet());
                        }
                        long tToolEnd = System.currentTimeMillis();
                        log.info("[Chat][Trace] 工具执行结果: name={}, 耗时={}ms, resultLength={}", toolName, tToolEnd - tToolStart, toolResult.length());

                        spans.add(buildSpan("tool_" + toolCallCountHolder[0], llmSpanId, "tool_execute",
                                tToolStart, tToolEnd - tToolStart, "OK",
                                Map.of("toolName", toolName, "args", toolArgs != null ? toolArgs : "",
                                        "resultLength", toolResult.length())));

                        if ("query_knowledge".equals(toolName)) {
                            List<Map<String, Object>> kbResults = QueryKnowledgeTool.getSearchResults(requestId);
                            if (!kbResults.isEmpty()) kbResultsHolder.addAll(kbResults);
                        }

                        // 1. 先收集工具执行过程中的中间状态事件（正在向量化、共找到等）
                        List<String> emittedEvents = ToolEventEmitter.drain();
                        for (String event : emittedEvents) {
                            toolEventsList.add(Map.of("type", "tool_status", "message", event,
                                    "contentOffset", toolContentOffset));
                            statusFluxes.add(Flux.just(STATUS_PREFIX + generateToolStatusEvent(event, toolContentOffset)));
                        }

                        // 2. 最后添加工具执行结果
                        toolEventsList.add(Map.of("type", "tool_result", "toolName", toolName, "result",
                                toolResult.length() > 2000 ? toolResult.substring(0, 2000) + "..." : toolResult,
                                "contentOffset", toolContentOffset));
                        toolResponses.add(new org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse(
                                firstTool.id(), toolName, toolResult));
                    }

                    // 将所有工具结果加入 messages，递归调用 LLM
                    messages.add(org.springframework.ai.chat.messages.ToolResponseMessage.builder()
                            .responses(toolResponses)
                            .build());

                    // 合并 metadata（工具执行后立即发送，不等到文本输出）
                    List<Map<String, Object>> kbResultsRef = kbResultsHolder;
                    Flux<String> afterTool = Flux.defer(() -> {
                        if (!kbResultsRef.isEmpty() || !toolEventsList.isEmpty()) {
                            Map<String, Object> metadataMap = new java.util.LinkedHashMap<>();
                            if (!toolEventsList.isEmpty()) {
                                metadataMap.put("toolEvents", toolEventsList);
                                // 提取工具块在文本中的插入位置（去重、保持顺序）
                                List<Integer> offsets = toolEventsList.stream()
                                        .map(e -> e.get("contentOffset"))
                                        .filter(java.util.Objects::nonNull)
                                        .map(o -> ((Number) o).intValue())
                                        .distinct()
                                        .sorted()
                                        .toList();
                                if (!offsets.isEmpty()) metadataMap.put("toolBlockOffsets", offsets);
                            }
                            if (!kbResultsRef.isEmpty()) {
                                List<RagReferenceVO> refs = kbResultsRef.stream().map(row -> {
                                    RagReferenceVO vo = new RagReferenceVO();
                                    vo.setDocumentName((String) row.get("document_name"));
                                    String content = (String) row.get("content");
                                    vo.setContentPreview(content != null && content.length() > 200
                                            ? content.substring(0, 200) + "..." : content);
                                    Object score = row.get("score");
                                    vo.setScore(score != null ? ((Number) score).doubleValue() : null);
                                    Object knowledgeId = row.get("knowledge_id");
                                    vo.setKnowledgeId(knowledgeId != null ? ((Number) knowledgeId).longValue() : null);
                                    Object documentId = row.get("document_id");
                                    vo.setDocumentId(documentId != null ? ((Number) documentId).longValue() : null);
                                    Object chunkId = row.get("chunk_id");
                                    vo.setChunkId(chunkId != null ? ((Number) chunkId).longValue() : null);
                                    return vo;
                                }).toList();
                                metadataMap.put("ragReferences", refs);
                            }
                            try {
                                ragMetadataHolder[0] = OBJECT_MAPPER.writeValueAsString(metadataMap);
                                // 立即发送 [METADATA]，不等到文本输出阶段
                                return Flux.just(METADATA_PREFIX + ragMetadataHolder[0]);
                            } catch (Exception e) {
                                log.warn("[Chat] 序列化metadata失败: {}", e.getMessage());
                            }
                        }
                        return Flux.empty();
                    });

                    // 发送工具状态 + 工具结果事件 + 递归调用LLM
                    long nextLlmStart = System.currentTimeMillis();
                    List<Flux<String>> toolResultEvents = new ArrayList<>();
                    final int resultContentOffset = toolContentOffset;
                    for (org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse tr : toolResponses) {
                        toolResultEvents.add(Flux.just(STATUS_PREFIX + generateToolResultEvent(tr.name(), tr.responseData(), resultContentOffset)));
                    }
                    Flux<String> toolEventFlux = Flux.concat(statusFluxes)
                            .concatWith(Flux.concat(toolResultEvents))
                            .concatWith(Flux.just(STATUS_PREFIX + generateToolCompleteEvent(resultContentOffset)))
                            .concatWith(afterTool);
                    return toolEventFlux.concatWith(processToolCallsRecursively(
                            chatModel, messages, toolOptions, toolCallbackMap,
                            agent, fullReply, ragMetadataHolder, toolCallCountHolder, inputTokenHolder, outputTokenHolder, toolEventsList, requestId, depth + 1, spans, nextLlmStart, configMap, reasoningContent));
                });
    }

    /**
     * 构建 ChatOptions，包含 Agent 绑定的工具回调
     * <p>有工具时使用 ToolCallingChatOptions，无工具时退化为普通 ChatOptions</p>
     */
    private ToolCallingChatOptions buildChatOptionsWithTools(Long providerId, Map<String, Object> configMap, Agent agent) {
        // 1. 基础模型配置
        ToolCallingChatOptions.Builder toolBuilder = ToolCallingChatOptions.builder();
        String modelId = configMap.containsKey("modelId") ? configMap.get("modelId").toString() : null;
        if (modelId != null) toolBuilder.model(modelId);
        if (configMap.containsKey("temperature")) {
            Object v = configMap.get("temperature");
            toolBuilder.temperature(v instanceof Number n ? n.doubleValue() : Double.parseDouble(v.toString()));
        }
        if (configMap.containsKey("topP")) {
            Object v = configMap.get("topP");
            toolBuilder.topP(v instanceof Number n ? n.doubleValue() : Double.parseDouble(v.toString()));
        }
        if (configMap.containsKey("maxTokens")) {
            Object v = configMap.get("maxTokens");
            toolBuilder.maxTokens(v instanceof Number n ? n.intValue() : Integer.parseInt(v.toString()));
        }

        // 2. 解析 Agent 绑定的工具
        if (agent != null) {
            List<Long> toolIds = agentService.getToolIds(agent.getId());
            if (!toolIds.isEmpty()) {
                List<ToolCallback> toolCallbacks = toolService.resolveToolCallbacksByIds(toolIds);
                if (!toolCallbacks.isEmpty()) {
                    toolBuilder.toolCallbacks(toolCallbacks);
                    toolBuilder.toolContext(Map.of("agentId", agent.getId()));
                    log.info("[Chat] 加载Agent工具: agentId={}, toolIds={}", agent.getId(), toolIds);
                }
            }
        }

        return toolBuilder.build();
    }

    /**
     * 从 ToolCallingChatOptions 中提取工具名→ToolCallback 映射
     */
    private Map<String, ToolCallback> buildToolCallbackMap(ToolCallingChatOptions options) {
        List<ToolCallback> callbacks = options.getToolCallbacks();
        if (callbacks == null || callbacks.isEmpty()) {
            return Map.of();
        }
        return callbacks.stream()
                .collect(Collectors.toMap(
                        cb -> cb.getToolDefinition().name(),
                        cb -> cb,
                        (a, b) -> b));
    }

    /**
     * 构建工具使用引导文本，追加到系统提示词开头
     * <p>列出可用工具名称和描述，引导AI主动调用工具并基于结果回答</p>
     */
    private String buildToolGuide(List<ToolCallback> toolCallbacks, Map<String, Object> agentConfigMap) {
        StringBuilder sb = new StringBuilder("## 可用工具\n");
        sb.append("你有以下工具可以使用，请根据用户问题主动调用合适的工具，并基于工具返回的结果来回答：\n\n");
        for (ToolCallback cb : toolCallbacks) {
            sb.append("- **").append(cb.getToolDefinition().name()).append("**: ")
              .append(cb.getToolDefinition().description()).append("\n");
        }

        boolean asyncEnabled = agentConfigMap != null && Boolean.TRUE.equals(agentConfigMap.get("asyncToolCalls"));
        sb.append("""

                **工具使用规则（必须严格遵守）**：
                """);
        if (asyncEnabled) {
            sb.append("1. 允许同时调用多个工具（并行调用），以提高回答效率\n");
        } else {
            sb.append("1. 每次回复只能调用一个工具，禁止并行调用多个工具\n");
        }
        sb.append("""
                2. 必须等待工具执行完成后，才能基于工具返回的结果生成最终回答
                3. 工具返回的结果必须作为你回答的主要依据，不得忽略或跳过工具返回的内容
                4. 如果工具返回了参考文献或搜索结果，必须在回答中引用这些内容
                5. 如果工具返回"未找到相关内容"，请基于自身知识回答并说明知识库中未找到相关信息
                6. 禁止在工具尚未返回结果时就提前结束对话
                """);

        sb.append("""

                **输出格式要求（必须严格遵守）**：
                - 使用 Markdown 格式输出，合理使用标题、列表、表格等结构化排版
                - 当回答包含多个要点时，必须使用有序列表（1. 2. 3.）或无序列表（- ）
                - 当回答涉及对比、参数、多维信息时，使用 Markdown 表格
                - 每个要点单独一行，不要将多个要点合并到同一段落
                - 重点内容使用 **加粗** 标记
                - 确保标题层级清晰：一级标题用 #，二级用 ##，三级用 ###
                """);
        return sb.toString();
    }

    /**
     * 生成工具调用状态事件 JSON
     */
    private String generateToolCallEvent(String toolName, String args, int contentOffset) {
        try {
            return OBJECT_MAPPER.writeValueAsString(Map.of(
                    "type", "tool_call",
                    "toolName", toolName,
                    "args", args != null ? args : "",
                    "contentOffset", contentOffset));
        } catch (Exception e) {
            return "{\"type\":\"tool_call\",\"toolName\":\"" + toolName + "\",\"contentOffset\":" + contentOffset + "}";
        }
    }

    /**
     * 生成工具结果状态事件 JSON
     */
    private String generateToolResultEvent(String toolName, String result, int contentOffset) {
        try {
            // 截断过长的结果
            String truncated = result.length() > 2000 ? result.substring(0, 2000) + "..." : result;
            return OBJECT_MAPPER.writeValueAsString(Map.of(
                    "type", "tool_result",
                    "toolName", toolName,
                    "result", truncated,
                    "contentOffset", contentOffset));
        } catch (Exception e) {
            return "{\"type\":\"tool_result\",\"toolName\":\"" + toolName + "\",\"contentOffset\":" + contentOffset + "}";
        }
    }

    /**
     * 生成工具中间状态事件 JSON（如知识库检索进度）
     */
    private String generateToolStatusEvent(String message, int contentOffset) {
        try {
            return OBJECT_MAPPER.writeValueAsString(Map.of(
                    "type", "tool_status",
                    "message", message,
                    "contentOffset", contentOffset));
        } catch (Exception e) {
            return "{\"type\":\"tool_status\",\"message\":\"" + message + "\",\"contentOffset\":" + contentOffset + "}";
        }
    }

    /**
     * 生成工具调用完成标记事件 JSON
     */
    private String generateToolCompleteEvent(int contentOffset) {
        try {
            return OBJECT_MAPPER.writeValueAsString(Map.of(
                    "type", "tool_complete",
                    "contentOffset", contentOffset));
        } catch (Exception e) {
            return "{\"type\":\"tool_complete\",\"contentOffset\":" + contentOffset + "}";
        }
    }

    /**
     * 生成思考过程内容事件 JSON
     */
    private String generateReasoningEvent(String content) {
        try {
            String truncated = content.length() > 8000 ? content.substring(0, 8000) + "..." : content;
            return OBJECT_MAPPER.writeValueAsString(Map.of(
                    "type", "reasoning_content",
                    "content", truncated));
        } catch (Exception e) {
            return "{\"type\":\"reasoning_content\",\"content\":\"\"}";
        }
    }

    /**
     * 构建调用链Span对象
     */
    private LlmTraceSpan buildSpan(String spanId, String parentSpanId, String name,
                                    long startTime, long durationMs, String status,
                                    Map<String, Object> attributes) {
        LlmTraceSpan span = new LlmTraceSpan();
        span.setSpanId(spanId);
        span.setParentSpanId(parentSpanId);
        span.setName(name);
        span.setStartTime(startTime);
        span.setDurationMs(durationMs);
        span.setStatus(status);
        span.setAttributes(attributes != null ? attributes : Map.of());
        return span;
    }

    /**
     * 仅过滤thinking标签内容（<think>/</think>等），
     * 不要过度过滤自然语言文本（如"让我查一下"等是正常回复，不是thinking）
     */
    private String stripThinkingTags(String text) {
        if (text == null || text.isEmpty()) return text;

        // 过滤 <think>...</think> 标签
        text = text.replaceAll("<think>[\\s\\S]*?</think>", "").trim();

        // 过滤 <thinking>...</thinking> 标签
        text = text.replaceAll("<thinking>[\\s\\S]*?</thinking>", "").trim();

        return text;
    }

    /**
     * 加载Agent配置。
     * agentId非空时加载指定Agent；为空时查询用户的默认Agent。
     */
    private Agent loadAgent(Long agentId) {
        // 1. 指定了agentId，直接加载
        if (agentId != null) {
            Agent agent = agentService.getById(agentId);
            if (agent == null) {
                log.warn("[Chat] Agent不存在，agentId={}", agentId);
            }
            return agent;
        }

        // 2. 未指定agentId，查询用户的默认Agent
        long userId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong();
        return agentService.getDefaultAgent(userId);
    }

    /**
     * 解析config JSONB字符串为Map
     */
    private Map<String, Object> parseConfig(String config) {
        if (config == null || config.isBlank()) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(config, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("[Chat] 解析Agent config失败: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * 从config Map中获取providerId。
     * 若Agent未配置providerId，自动使用第一个可用的模型提供商。
     */
    private Long getProviderId(Map<String, Object> configMap) {
        Object providerId = configMap.get(ConfigKeys.Agent.PROVIDER_ID);
        if (providerId != null) {
            return providerId instanceof Number ? ((Number) providerId).longValue() : Long.parseLong(providerId.toString());
        }

        // 自动使用第一个可用的模型提供商
        var providers = modelFactory.getAvailableProviderIds();
        if (providers.isEmpty()) {
            throw new IllegalArgumentException("请先在「模型提供商管理」中配置至少一个模型提供商");
        }
        log.info("[Chat] Agent未配置providerId，使用默认提供商: id={}", providers.get(0));
        return providers.get(0);
    }

    /**
     * 构建消息列表：系统提示词 + 工具使用引导 + 历史消息 + 当前用户消息
     */
    private List<org.springframework.ai.chat.messages.Message> buildMessages(Long sessionId, String userMessage, Agent agent) {
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();

        // 1. 解析Agent配置，获取上下文条数
        Map<String, Object> agentConfigMap = parseConfig(agent != null ? agent.getConfig() : null);
        int maxContextMessages = 20;
        if (agentConfigMap.containsKey("maxContextMessages")) {
            Object v = agentConfigMap.get("maxContextMessages");
            maxContextMessages = v instanceof Number ? ((Number) v).intValue() : Integer.parseInt(v.toString());
        }

        // 2. 系统提示词：优先使用Agent的systemPrompt
        String systemPrompt = (agent != null && agent.getSystemPrompt() != null && !agent.getSystemPrompt().isBlank())
                ? agent.getSystemPrompt()
                : DEFAULT_SYSTEM_PROMPT;

        // 3. 如果Agent绑定了工具，追加工具使用引导到系统提示词
        if (agent != null) {
            List<Long> toolIds = agentService.getToolIds(agent.getId());
            if (!toolIds.isEmpty()) {
                List<ToolCallback> toolCallbacks = toolService.resolveToolCallbacksByIds(toolIds);
                if (!toolCallbacks.isEmpty()) {
                    systemPrompt = buildToolGuide(toolCallbacks, agentConfigMap) + "\n\n" + systemPrompt;
                }
            }
        }

        messages.add(new SystemMessage(systemPrompt));

        // 4. 加载历史消息（当前用户消息已保存到DB，需排除避免重复）
        List<Message> history = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getSessionId, sessionId)
                        .orderByAsc(Message::getCreateTime)
                        .last("LIMIT " + (maxContextMessages + 1)));

        // 排除最后一条如果就是当前用户消息（已保存到DB但会单独添加）
        if (!history.isEmpty()) {
            Message lastMsg = history.get(history.size() - 1);
            if (lastMsg.getRole() == MessageRole.USER && userMessage.equals(lastMsg.getContent())) {
                history.remove(history.size() - 1);
            }
        }
        // 确保不超过配置的上下文条数
        if (history.size() > maxContextMessages) {
            history = history.subList(history.size() - maxContextMessages, history.size());
        }

        for (Message msg : history) {
            if (msg.getRole() == MessageRole.USER) {
                messages.add(new UserMessage(msg.getContent()));
            } else if (msg.getRole() == MessageRole.ASSISTANT) {
                messages.add(new AssistantMessage(msg.getContent()));
            }
        }

        // 4. 当前用户消息
        messages.add(new UserMessage(userMessage));
        return messages;
    }

    /**
     * 文本向量化
     */
    private float[] embedText(String text) {
        EmbeddingResponse response = embeddingModel.call(
                new EmbeddingRequest(List.of(text), null));
        return response.getResult().getOutput();
    }

    /**
     * 解析会话ID：有则复用，无则新建
     */
    private Long resolveSessionId(Long sessionId, Long agentId) {
        if (sessionId != null) {
            return sessionId;
        }
        return chatSessionService.createSession(agentId).getId();
    }

    /**
     * 持久化消息并更新会话统计
     */
    private void saveMessage(Long sessionId, MessageRole role, String content) {
        saveMessage(sessionId, role, content, null);
    }

    /**
     * 持久化消息并更新会话统计（含metadata）
     */
    private void saveMessage(Long sessionId, MessageRole role, String content, String metadata) {
        saveMessage(sessionId, role, content, metadata, 0);
    }

    /**
     * 持久化消息并更新会话统计（含metadata和tokenCount）
     */
    private void saveMessage(Long sessionId, MessageRole role, String content, String metadata, int tokenCount) {
        Message msg = new Message();
        msg.setSessionId(sessionId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setContentType(ContentType.TEXT);
        msg.setTokenCount(tokenCount);
        msg.setMetadata(metadata);
        messageMapper.insert(msg);

        chatSessionService.updateStats(sessionId, tokenCount);
    }

    /**
     * 异步生成对话标题：标题仍为"新对话"且消息数>=2时，调用AI生成简短标题
     */
    private void generateTitle(Long sessionId) {
        try {
            // 1. 检查会话是否存在且标题仍为默认值
            ChatSession session = chatSessionService.getById(sessionId);
            if (session == null || !"新对话".equals(session.getTitle())) {
                return;
            }

            // 2. 获取前4条消息
            List<Message> messages = messageMapper.selectList(
                    new LambdaQueryWrapper<Message>()
                            .eq(Message::getSessionId, sessionId)
                            .orderByAsc(Message::getCreateTime)
                            .last("LIMIT 4"));

            if (messages.size() < 2) {
                return;
            }

            // 3. 拼接对话文本
            StringBuilder conversationText = new StringBuilder();
            for (Message msg : messages) {
                String role = msg.getRole() == MessageRole.USER ? "用户" : "助手";
                conversationText.append(role).append("：").append(msg.getContent()).append("\n");
            }

            // 4. 使用会话绑定的Agent模型生成标题，未绑定则使用默认提供商
            Long providerId = resolveTitleProviderId(session.getAgentId());
            List<org.springframework.ai.chat.messages.Message> promptMessages = new ArrayList<>();
            promptMessages.add(new SystemMessage("你是一个标题生成助手，只输出标题，不要任何其他内容。"));
            promptMessages.add(new UserMessage("请根据以下对话内容生成一个简短的标题（不超过20个字，不要加引号）：\n" + conversationText));

            ChatResponse response = modelFactory.getChatModel(providerId).call(new Prompt(promptMessages));
            String title = response.getResult().getOutput().getText().trim();

            // 5. 清理标题
            title = title.replaceAll("^[\"'「」『』]+|[\"'「」『』]+$", "");
            if (title.length() > 30) {
                title = title.substring(0, 30);
            }

            // 6. 更新会话标题
            if (!title.isBlank()) {
                chatSessionService.updateTitle(sessionId, title);
                log.info("[Chat] 会话标题已生成: sessionId={}, title={}", sessionId, title);
            }
        } catch (Exception e) {
            log.warn("[Chat] 标题生成失败: sessionId={}, error={}", sessionId, e.getMessage());
        }
    }

    /**
     * 解析标题生成使用的providerId：优先使用会话绑定Agent的模型，未绑定则降级为默认提供商
     */
    private Long resolveTitleProviderId(Long agentId) {
        if (agentId != null) {
            Agent agent = agentService.getById(agentId);
            if (agent != null) {
                Map<String, Object> configMap = parseConfig(agent.getConfig());
                Long providerId = getProviderId(configMap);
                if (providerId != null) {
                    return providerId;
                }
            }
        }
        return getDefaultProviderId();
    }

    /**
     * 获取默认providerId（兜底方案）
     * 优先使用第一个可用的 ModelProvider
     */
    private Long getDefaultProviderId() {
        var providers = modelFactory.getAvailableProviderIds();
        if (providers.isEmpty()) {
            throw new IllegalStateException("没有可用的模型提供商，请先在模型提供商管理页面配置");
        }
        return providers.get(0);
    }

    @Override
    public List<RagReferenceVO> getRagReferences(Long sessionId, Long agentId, String question) {
        // 1. 加载Agent配置
        Agent agent = loadAgent(agentId);
        if (agent == null) {
            return List.of();
        }

        // 2. 获取RAG检索结果
        List<Map<String, Object>> searchResults = getRagSearchResults(agent.getId(), question);

        // 3. 转换为VO
        return searchResults.stream().map(row -> {
            RagReferenceVO vo = new RagReferenceVO();
            vo.setDocumentName((String) row.get("document_name"));
            String content = (String) row.get("content");
            vo.setContentPreview(content != null && content.length() > 200
                    ? content.substring(0, 200) + "..."
                    : content);
            Object score = row.get("score");
            vo.setScore(score != null ? ((Number) score).doubleValue() : null);
            Object knowledgeId = row.get("knowledge_id");
            vo.setKnowledgeId(knowledgeId != null ? ((Number) knowledgeId).longValue() : null);
            Object documentId = row.get("document_id");
            vo.setDocumentId(documentId != null ? ((Number) documentId).longValue() : null);
            Object chunkId = row.get("chunk_id");
            vo.setChunkId(chunkId != null ? ((Number) chunkId).longValue() : null);
            return vo;
        }).toList();
    }

    /**
     * 执行RAG检索，返回原始结果（用于获取引用信息）
     */
    private List<Map<String, Object>> getRagSearchResults(Long agentId, String question) {
        List<Long> knowledgeIds = agentService.getKnowledgeIds(agentId);
        if (knowledgeIds.isEmpty()) {
            return List.of();
        }

        try {
            float[] queryVector = embedText(question);
            List<Map<String, Object>> allResults = new ArrayList<>();

            // 并行检索（使用各知识库配置的 topK 和 threshold）
            List<CompletableFuture<List<Map<String, Object>>>> futures = knowledgeIds.stream()
                    .map(knowledgeId -> CompletableFuture.supplyAsync(() -> {
                        try {
                            Knowledge knowledge = knowledgeService.getById(knowledgeId);
                            int topK = parseRagTopK(knowledge);
                            double threshold = parseRagThreshold(knowledge);
                            return embeddingService.searchSimilar(knowledgeId, queryVector, topK, threshold);
                        } catch (Exception e) {
                            log.warn("[Chat] 知识库检索失败: knowledgeId={}, error={}", knowledgeId, e.getMessage());
                            return List.<Map<String, Object>>of();
                        }
                    }, RAG_EXECUTOR))
                    .toList();

            futures.forEach(f -> allResults.addAll(f.join()));

            return allResults;
        } catch (Exception e) {
            log.warn("[Chat] RAG检索失败: {}", e.getMessage());
            return List.of();
        }
    }

    private static final int DEFAULT_RAG_TOP_K = 5;
    private static final double DEFAULT_RAG_THRESHOLD = 0.5;

    /**
     * 从知识库配置中解析 RAG Top K
     */
    private int parseRagTopK(Knowledge knowledge) {
        if (knowledge == null) {
            return DEFAULT_RAG_TOP_K;
        }
        Map<String, Object> config = parseConfig(knowledge.getConfig());
        Object val = config.get("ragTopK");
        return val instanceof Number ? ((Number) val).intValue() : DEFAULT_RAG_TOP_K;
    }

    /**
     * 从知识库配置中解析 RAG 相似度阈值
     */
    private double parseRagThreshold(Knowledge knowledge) {
        if (knowledge == null) {
            return DEFAULT_RAG_THRESHOLD;
        }
        Map<String, Object> config = parseConfig(knowledge.getConfig());
        Object val = config.get("ragThreshold");
        return val instanceof Number ? ((Number) val).doubleValue() : DEFAULT_RAG_THRESHOLD;
    }
}
