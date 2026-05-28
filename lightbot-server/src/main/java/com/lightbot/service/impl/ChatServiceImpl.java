package com.lightbot.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.constant.ConfigKeys;
import com.lightbot.dto.ChatRequest;
import com.lightbot.dto.RagReferenceVO;
import com.lightbot.util.ChatDocumentMessageUtil;
import com.lightbot.util.SensitiveWordFilter;
import com.lightbot.util.ToolArgsSanitizer;
import com.lightbot.entity.Agent;
import com.lightbot.entity.ModelProvider;
import com.lightbot.enums.ModelProviderType;
import com.lightbot.model.MimoChatClient;
import com.lightbot.subagent.DelegateSubAgentTool;
import com.lightbot.tool.ToolEventEmitter;
import com.lightbot.tool.systemtool.QueryKnowledgeTool;
import com.lightbot.entity.Knowledge;
import com.lightbot.dto.LlmTraceSpan;
import com.lightbot.enums.MessageRole;
import com.lightbot.service.*;
import com.lightbot.service.chat.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.lightbot.service.chat.ToolEventGenerator.*;

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

    private final AgentService agentService;
    private final EmbeddingService embeddingService;
    private final KnowledgeService knowledgeService;
    private final EmbeddingModel embeddingModel;
    private final TaskExecutor taskExecutor;

    // 中间件
    private final InitMiddleware initMiddleware;
    private final UserSensitiveMiddleware userSensitiveMiddleware;
    private final WorkflowMiddleware workflowMiddleware;
    private final SkillPrepMiddleware skillPrepMiddleware;
    private final MessageMiddleware messageMiddleware;
    private final ToolPrepMiddleware toolPrepMiddleware;
    private final TraceMiddleware traceMiddleware;
    private final MimoChatClient mimoChatClient;
    private final ModelProviderService modelProviderService;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 并行检索线程池 */
    private static final ExecutorService RAG_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "rag-search");
        t.setDaemon(true);
        return t;
    });

    @Override
    public String chat(ChatRequest request) {
        // 1. 初始化上下文
        ChatContext ctx = ChatContext.of(request);
        initMiddleware.init(ctx);
        Long agentId = ctx.getAgent() != null ? ctx.getAgent().getId() : null;
        SensitiveWordFilter.FilterResult userCheck = SensitiveWordFilter.checkUserInput(
                request.getMessage(), ctx.getConfigMap(), agentId, ctx.getSessionId());
        if (userCheck.blocked()) {
            messageMiddleware.saveMessage(ctx.getSessionId(), MessageRole.ASSISTANT, userCheck.text());
            return userCheck.text();
        }
        skillPrepMiddleware.prepare(ctx);
        messageMiddleware.prepare(ctx);
        toolPrepMiddleware.prepare(ctx);

        log.info("[Chat] 用户消息: sessionId={}, agentId={}, message={}", ctx.getSessionId(),
                agentId, request.getMessage());

        // 2. 调用模型获取回复
        ChatResponse response = ctx.getChatModel().call(new Prompt(ctx.getMessages(), ctx.getToolOptions()));
        String reply = response.getResult().getOutput().getText();
        reply = SensitiveWordFilter.filterAiOutput(reply, ctx.getConfigMap(), agentId, ctx.getSessionId()).text();

        log.info("[Chat] AI回复: sessionId={}, length={}", ctx.getSessionId(), reply != null ? reply.length() : 0);

        // 3. 持久化AI回复（用户消息已在 messageMiddleware.prepare 中保存）
        messageMiddleware.saveMessage(ctx.getSessionId(), MessageRole.ASSISTANT, reply);

        // 4. 异步生成标题
        taskExecutor.execute(() -> traceMiddleware.generateTitle(ctx.getSessionId(), ctx.getAgent(), ctx.getConfigMap()));

        return reply;
    }

    @Override
    public Flux<String> chatStream(ChatRequest request) {
        ChatContext ctx = ChatContext.of(request);
        ctx.setRequestId(String.valueOf(System.nanoTime()));

        // Init → 用户敏感词 → Workflow → SkillPrep → Message → ToolPrep → Trace → [core]
        List<ChatMiddleware> middlewares = List.of(
                initMiddleware, userSensitiveMiddleware, workflowMiddleware,
                skillPrepMiddleware, messageMiddleware, toolPrepMiddleware, traceMiddleware);
        ChatServiceCore core = this::streamCore;

        return Flux.just(REQUEST_ID_PREFIX + ctx.getRequestId())
                .concatWith(ChatMiddlewareChain.of(middlewares, core).proceed(ctx))
                .concatWith(Flux.just(DONE_PREFIX));
    }

    /**
     * 流式核心：递归工具调用循环
     */
    private Flux<String> streamCore(ChatContext ctx) {
        ctx.setStartTime(System.currentTimeMillis());
        Long agentId = ctx.getAgent() != null ? ctx.getAgent().getId() : null;
        ctx.setSensitiveStreamState(new SensitiveWordFilter.StreamState(
                ctx.getConfigMap(), agentId, ctx.getSessionId()));
        return processToolCallsRecursively(ctx, 0, System.currentTimeMillis());
    }

    /**
     * 递归处理工具调用：调用LLM → 检测工具 → 执行 → 重新调用LLM
     *
     * @param ctx          管道上下文
     * @param depth        递归深度（防止无限循环）
     * @param llmCallStart 本轮LLM调用开始时间
     * @return Flux<String> 流式输出片段
     */
    private Flux<String> processToolCallsRecursively(ChatContext ctx, int depth, long llmCallStart) {
        if (depth >= 10) {
            log.warn("[Chat][Trace] 工具调用递归深度达到上限({})，停止循环", depth);
            return Flux.just("\n[工具调用轮次已达上限，请简化问题后重试]");
        }

        ChatModel chatModel = ctx.getChatModel();
        List<org.springframework.ai.chat.messages.Message> messages = ctx.getMessages();
        ToolCallingChatOptions toolOptions = ctx.getToolOptions();
        Map<String, ToolCallback> toolCallbackMap = ctx.getToolCallbackMap();
        Agent agent = ctx.getAgent();
        StringBuilder fullReply = ctx.getFullReply();
        String[] ragMetadataHolder = ctx.getRagMetadataHolder();
        int[] toolCallCountHolder = ctx.getToolCallCountHolder();
        int[] inputTokenHolder = ctx.getInputTokenHolder();
        int[] outputTokenHolder = ctx.getOutputTokenHolder();
        List<Map<String, Object>> toolEventsList = ctx.getToolEventsList();
        String requestId = ctx.getRequestId();
        List<LlmTraceSpan> spans = ctx.getSpans();
        Map<String, Object> configMap = ctx.getConfigMap();
        StringBuilder reasoningContent = ctx.getReasoningContent();

        if (!isStreamOutputEnabled(configMap)) {
            return processBlockingRound(ctx, depth, llmCallStart);
        }

        // MiMo 直连：联网搜索 / 视频等多模态
        ModelProvider provider = ctx.getProviderId() != null
                ? modelProviderService.getById(ctx.getProviderId()) : null;
        if (provider != null && provider.getType() == ModelProviderType.MIMO
                && mimoChatClient.shouldUseDirectApi(configMap, ctx.getRequest().getAttachments())
                && depth == 0) {
            return streamMimoDirect(ctx, depth, llmCallStart, provider, messages);
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
                        // 先累加 Token（usage 常在最后一个空文本 chunk，不能因 stripped 为空而跳过）
                        accumulateStreamUsage(response, inputTokenHolder, outputTokenHolder);

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
                                    return Flux.just(STATUS_PREFIX + reasoningEvent(reasoning));
                                }
                            }
                        }

                        String stripped = stripThinkingTags(text);
                        if (stripped.isEmpty()) return Flux.empty();
                        String delta = ctx.getSensitiveStreamState() != null
                                ? ctx.getSensitiveStreamState().processChunk(stripped)
                                : SensitiveWordFilter.filterAiOutput(stripped, ctx.getConfigMap(), agent.getId(), ctx.getSessionId()).text();
                        // AI 输出命中 block 策略：中断正常文本流，发送 sensitive_block 事件
                        if (ctx.getSensitiveStreamState() != null && ctx.getSensitiveStreamState().isBlocked()) {
                            fullReply.setLength(0);
                            fullReply.append(SensitiveWordFilter.AI_BLOCK_MESSAGE);
                            return Flux.just(STATUS_PREFIX + ToolEventGenerator.sensitiveBlockEvent("ai_output", SensitiveWordFilter.AI_BLOCK_MESSAGE));
                        }
                        if (delta.isEmpty()) {
                            return Flux.empty();
                        }

                        fullReply.append(delta);
                        if (!llmSpanAdded[0]) {
                            spans.add(traceMiddleware.buildSpan(llmSpanId, "s1", "llm_call", llmCallStart,
                                    System.currentTimeMillis() - llmCallStart, "OK",
                                    Map.of("depth", depth, "model", configMap.getOrDefault("modelId", ""),
                                            "inputTokens", inputTokenHolder[0], "outputTokens", outputTokenHolder[0],
                                            "replyPreview", fullReply.length() > 500 ? fullReply.substring(0, 500) + "..." : fullReply.toString())));
                            llmSpanAdded[0] = true;
                        }
                        return Flux.just(delta);
                    }

                    // 3. 有工具调用 → 执行工具
                    messages.add(assistantMsg);

                    accumulateStreamUsage(response, inputTokenHolder, outputTokenHolder);

                    List<AssistantMessage.ToolCall> toolCalls = assistantMsg.getToolCalls();
                    boolean asyncEnabled = Boolean.TRUE.equals(configMap.get("asyncToolCalls"));

                    if (!llmSpanAdded[0]) {
                        spans.add(traceMiddleware.buildSpan(llmSpanId, "s1", "llm_call", llmCallStart,
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
                            appendToolCallStart(ctx, toolEventsList, statusFluxes, tc.name(), tcArgs, toolContentOffset);
                            toolCallCountHolder[0]++;
                            final String tcName = tc.name();
                            final String safeTcArgs = ToolArgsSanitizer.forChatCall(tcArgs);
                            futures.add(CompletableFuture.supplyAsync(() -> {
                                long tStart = System.currentTimeMillis();
                                String result;
                                ToolCallback cb = toolCallbackMap.get(tcName);
                                if (cb != null) {
                                    try {
                                        result = cb.call(safeTcArgs, new ToolContext(Map.of("agentId", agent.getId(), "requestId", requestId)));
                                    } catch (Exception e) {
                                        log.error("[Chat] 工具执行异常: name={}, error={}", tcName, e.getMessage(), e);
                                        result = "工具执行失败: " + e.getMessage();
                                    }
                                } else {
                                    result = "工具不存在: " + tcName;
                                }
                                long tEnd = System.currentTimeMillis();
                                log.info("[Chat][Trace] 工具执行结果: name={}, 耗时={}ms, resultLength={}", tcName, tEnd - tStart, result.length());
                                spans.add(traceMiddleware.buildSpan("tool_" + toolCallCountHolder[0], llmSpanId, "tool_execute",
                                        tStart, tEnd - tStart, "OK",
                                        Map.of("toolName", tcName, "args", tcArgs, "resultLength", result.length())));
                                if ("query_knowledge".equals(tcName)) {
                                    List<Map<String, Object>> kbResults = QueryKnowledgeTool.getSearchResults(requestId);
                                    synchronized (kbResultsHolder) { kbResultsHolder.addAll(kbResults); }
                                }
                                appendToolCallResult(toolEventsList, statusFluxes, tcName, tcArgs, result, toolContentOffset);
                                return result;
                            }, RAG_EXECUTOR));
                        }
                        for (int i = 0; i < toolCalls.size(); i++) {
                            AssistantMessage.ToolCall tc = toolCalls.get(i);
                            String result = futures.get(i).join();
                            toolResponses.add(new org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse(
                                    tc.id(), tc.name(), result));
                        }
                    } else {
                        // 串行执行：只执行第一个工具
                        AssistantMessage.ToolCall firstTool = toolCalls.get(0);
                        log.info("[Chat][Trace] 工具调用(depth={}): {}个工具, 只执行第一个: {}",
                                depth, toolCalls.size(), firstTool.name());
                        String toolName = firstTool.name();
                        String toolArgs = firstTool.arguments();
                        toolCallCountHolder[0]++;

                        String safeArgs = toolArgs != null ? toolArgs : "";
                        String callArgs = ToolArgsSanitizer.forChatCall(safeArgs);
                        appendToolCallStart(ctx, toolEventsList, statusFluxes, toolName, safeArgs, toolContentOffset);

                        long tToolStart = System.currentTimeMillis();
                        String toolResult;
                        ToolCallback callback = toolCallbackMap.get(toolName);
                        if (callback != null) {
                            try {
                                toolResult = callback.call(callArgs, new ToolContext(Map.of(
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

                        spans.add(traceMiddleware.buildSpan("tool_" + toolCallCountHolder[0], llmSpanId, "tool_execute",
                                tToolStart, tToolEnd - tToolStart, "OK",
                                Map.of("toolName", toolName, "args", safeArgs, "resultLength", toolResult.length())));

                        if ("query_knowledge".equals(toolName)) {
                            List<Map<String, Object>> kbResults = QueryKnowledgeTool.getSearchResults(requestId);
                            if (!kbResults.isEmpty()) kbResultsHolder.addAll(kbResults);
                        }

                        List<String> emittedEvents = ToolEventEmitter.drain();
                        for (String event : emittedEvents) {
                            toolEventsList.add(Map.of("type", "tool_status", "message", event,
                                    "contentOffset", toolContentOffset));
                            statusFluxes.add(Flux.just(STATUS_PREFIX + toolStatusEvent(event, toolContentOffset)));
                        }

                        appendToolCallResult(toolEventsList, statusFluxes, toolName, safeArgs, toolResult, toolContentOffset);
                        toolResponses.add(new org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse(
                                firstTool.id(), toolName, toolResult));
                    }

                    messages.add(org.springframework.ai.chat.messages.ToolResponseMessage.builder()
                            .responses(toolResponses)
                            .build());

                    List<Map<String, Object>> kbResultsRef = kbResultsHolder;
                    Flux<String> afterTool = Flux.defer(() -> {
                        if (!kbResultsRef.isEmpty() || !toolEventsList.isEmpty()) {
                            Map<String, Object> metadataMap = new java.util.LinkedHashMap<>();
                            if (!toolEventsList.isEmpty()) {
                                metadataMap.put("toolEvents", toolEventsList);
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
                                return Flux.just(METADATA_PREFIX + ragMetadataHolder[0]);
                            } catch (Exception e) {
                                log.warn("[Chat] 序列化metadata失败: {}", e.getMessage());
                            }
                        }
                        return Flux.empty();
                    });

                    long nextLlmStart = System.currentTimeMillis();
                    List<Flux<String>> toolResultEvents = new ArrayList<>();
                    final int resultContentOffset = toolContentOffset;
                    for (org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse tr : toolResponses) {
                        if (!DelegateSubAgentTool.TOOL_NAME.equals(tr.name())) {
                            toolResultEvents.add(Flux.just(STATUS_PREFIX + toolResultEvent(tr.name(), tr.responseData(), resultContentOffset)));
                        }
                    }
                    Flux<String> toolEventFlux = Flux.concat(statusFluxes)
                            .concatWith(Flux.concat(toolResultEvents))
                            .concatWith(Flux.just(STATUS_PREFIX + toolCompleteEvent(resultContentOffset)))
                            .concatWith(afterTool);
                    return toolEventFlux.concatWith(processToolCallsRecursively(ctx, depth + 1, nextLlmStart));
                });
    }

    /**
     * 非流式 LLM 轮次：call() 获取完整回复后一次性输出
     */
    private Flux<String> processBlockingRound(ChatContext ctx, int depth, long llmCallStart) {
        if (depth >= 10) {
            log.warn("[Chat][Trace] 工具调用递归深度达到上限({})，停止循环", depth);
            return Flux.just("\n[工具调用轮次已达上限，请简化问题后重试]");
        }

        ChatModel chatModel = ctx.getChatModel();
        List<org.springframework.ai.chat.messages.Message> messages = ctx.getMessages();
        ToolCallingChatOptions toolOptions = ctx.getToolOptions();
        Map<String, ToolCallback> toolCallbackMap = ctx.getToolCallbackMap();
        Agent agent = ctx.getAgent();
        StringBuilder fullReply = ctx.getFullReply();
        int[] toolCallCountHolder = ctx.getToolCallCountHolder();
        int[] inputTokenHolder = ctx.getInputTokenHolder();
        int[] outputTokenHolder = ctx.getOutputTokenHolder();
        List<Map<String, Object>> toolEventsList = ctx.getToolEventsList();
        String requestId = ctx.getRequestId();
        List<LlmTraceSpan> spans = ctx.getSpans();
        Map<String, Object> configMap = ctx.getConfigMap();
        String[] ragMetadataHolder = ctx.getRagMetadataHolder();

        String llmSpanId = "llm_" + depth;
        Prompt prompt = new Prompt(new ArrayList<>(messages), toolOptions);

        ChatResponse response = chatModel.call(prompt);
        accumulateStreamUsage(response, inputTokenHolder, outputTokenHolder);

        Generation gen = response.getResult();
        AssistantMessage assistantMsg = (gen != null) ? gen.getOutput() : null;

        // 无工具调用 → 一次性输出完整文本
        if (assistantMsg == null || !assistantMsg.hasToolCalls()) {
            if (gen != null && gen.getOutput() != null) {
                var metadata = gen.getOutput().getMetadata();
                if (metadata != null) {
                    Object reasoningObj = metadata.get("reasoningContent");
                    if (reasoningObj != null && !reasoningObj.toString().isBlank()) {
                        return Flux.just(STATUS_PREFIX + reasoningEvent(reasoningObj.toString()));
                    }
                }
            }

            String text = (assistantMsg != null) ? assistantMsg.getText() : "";
            if (text == null) {
                text = "";
            }
            String stripped = stripThinkingTags(text);
            if (stripped.isEmpty()) {
                return Flux.empty();
            }
            SensitiveWordFilter.FilterResult filtered = SensitiveWordFilter.filterAiOutput(
                    stripped, configMap, agent.getId(), ctx.getSessionId());
            if (filtered.blocked()) {
                fullReply.setLength(0);
                fullReply.append(filtered.text());
                spans.add(traceMiddleware.buildSpan(llmSpanId, "s1", "llm_call", llmCallStart,
                        System.currentTimeMillis() - llmCallStart, "OK",
                        Map.of("depth", depth, "model", configMap.getOrDefault("modelId", ""),
                                "inputTokens", inputTokenHolder[0], "outputTokens", outputTokenHolder[0],
                                "streamOutput", false)));
                return Flux.just(STATUS_PREFIX + ToolEventGenerator.sensitiveBlockEvent("ai_output", filtered.text()));
            }
            fullReply.append(filtered.text());
            spans.add(traceMiddleware.buildSpan(llmSpanId, "s1", "llm_call", llmCallStart,
                    System.currentTimeMillis() - llmCallStart, "OK",
                    Map.of("depth", depth, "model", configMap.getOrDefault("modelId", ""),
                            "inputTokens", inputTokenHolder[0], "outputTokens", outputTokenHolder[0],
                            "streamOutput", false,
                            "replyPreview", fullReply.length() > 500 ? fullReply.substring(0, 500) + "..." : fullReply.toString())));
            return Flux.just(filtered.text());
        }

        // 有工具调用 → 执行工具后继续递归
        messages.add(assistantMsg);
        List<AssistantMessage.ToolCall> toolCalls = assistantMsg.getToolCalls();
        boolean asyncEnabled = Boolean.TRUE.equals(configMap.get("asyncToolCalls"));

        spans.add(traceMiddleware.buildSpan(llmSpanId, "s1", "llm_call", llmCallStart,
                System.currentTimeMillis() - llmCallStart, "OK",
                Map.of("depth", depth, "model", configMap.getOrDefault("modelId", ""),
                        "toolCount", toolCalls.size(),
                        "toolNames", toolCalls.stream().map(AssistantMessage.ToolCall::name).toList().toString(),
                        "streamOutput", false)));

        List<Flux<String>> statusFluxes = new ArrayList<>();
        List<Map<String, Object>> kbResultsHolder = new ArrayList<>();
        List<org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();
        int toolContentOffset = fullReply.length();

        if (asyncEnabled && toolCalls.size() > 1) {
            log.info("[Chat][Trace] 工具调用(depth={}): {}个工具, 并行执行", depth, toolCalls.size());
            List<CompletableFuture<String>> futures = new ArrayList<>();
            for (AssistantMessage.ToolCall tc : toolCalls) {
                String tcArgs = tc.arguments() != null ? tc.arguments() : "";
                // 按需推送 skill_active
                Flux<String> skFlux = emitSkillActiveIfNeeded(ctx, tc.name(), toolEventsList, toolContentOffset);
                if (skFlux != null) {
                    statusFluxes.add(skFlux);
                }
                toolEventsList.add(Map.of("type", "tool_call", "toolName", tc.name(), "args",
                        tcArgs, "contentOffset", toolContentOffset));
                statusFluxes.add(Flux.just(STATUS_PREFIX + toolCallEvent(tc.name(), tcArgs, toolContentOffset)));
                toolCallCountHolder[0]++;
                final String tcName = tc.name();
                final String safeTcArgs = ToolArgsSanitizer.forChatCall(tcArgs);
                futures.add(CompletableFuture.supplyAsync(() -> {
                    long tStart = System.currentTimeMillis();
                    String result = executeToolCallback(toolCallbackMap, tcName, safeTcArgs, agent.getId(), requestId);
                    long tEnd = System.currentTimeMillis();
                    spans.add(traceMiddleware.buildSpan("tool_" + toolCallCountHolder[0], llmSpanId, "tool_execute",
                            tStart, tEnd - tStart, "OK",
                            Map.of("toolName", tcName, "args", tcArgs, "resultLength", result.length())));
                    if ("query_knowledge".equals(tcName)) {
                        List<Map<String, Object>> kbResults = QueryKnowledgeTool.getSearchResults(requestId);
                        synchronized (kbResultsHolder) {
                            kbResultsHolder.addAll(kbResults);
                        }
                    }
                    toolEventsList.add(Map.of("type", "tool_result", "toolName", tcName, "result",
                            result.length() > 2000 ? result.substring(0, 2000) + "..." : result,
                            "contentOffset", toolContentOffset));
                    return result;
                }, RAG_EXECUTOR));
            }
            for (int i = 0; i < toolCalls.size(); i++) {
                AssistantMessage.ToolCall tc = toolCalls.get(i);
                String result = futures.get(i).join();
                toolResponses.add(new org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse(
                        tc.id(), tc.name(), result));
            }
        } else {
            AssistantMessage.ToolCall firstTool = toolCalls.get(0);
            log.info("[Chat][Trace] 工具调用(depth={}): {}个工具, 只执行第一个: {}",
                    depth, toolCalls.size(), firstTool.name());
            String toolName = firstTool.name();
            String toolArgs = firstTool.arguments();
            toolCallCountHolder[0]++;

            String safeArgs = toolArgs != null ? toolArgs : "";
            String callArgs = ToolArgsSanitizer.forChatCall(safeArgs);
            // 按需推送 skill_active
            Flux<String> skillFlux = emitSkillActiveIfNeeded(ctx, toolName, toolEventsList, toolContentOffset);
            if (skillFlux != null) {
                statusFluxes.add(skillFlux);
            }
            toolEventsList.add(Map.of("type", "tool_call", "toolName", toolName, "args",
                    safeArgs, "contentOffset", toolContentOffset));
            statusFluxes.add(Flux.just(STATUS_PREFIX + toolCallEvent(toolName, safeArgs, toolContentOffset)));

            long tToolStart = System.currentTimeMillis();
            String toolResult = executeToolCallback(toolCallbackMap, toolName, callArgs, agent.getId(), requestId);
            long tToolEnd = System.currentTimeMillis();
            spans.add(traceMiddleware.buildSpan("tool_" + toolCallCountHolder[0], llmSpanId, "tool_execute",
                    tToolStart, tToolEnd - tToolStart, "OK",
                    Map.of("toolName", toolName, "args", safeArgs, "resultLength", toolResult.length())));

            if ("query_knowledge".equals(toolName)) {
                List<Map<String, Object>> kbResults = QueryKnowledgeTool.getSearchResults(requestId);
                if (!kbResults.isEmpty()) {
                    kbResultsHolder.addAll(kbResults);
                }
            }

            List<String> emittedEvents = ToolEventEmitter.drain();
            for (String event : emittedEvents) {
                toolEventsList.add(Map.of("type", "tool_status", "message", event,
                        "contentOffset", toolContentOffset));
                statusFluxes.add(Flux.just(STATUS_PREFIX + toolStatusEvent(event, toolContentOffset)));
            }

            toolEventsList.add(Map.of("type", "tool_result", "toolName", toolName, "result",
                    toolResult.length() > 2000 ? toolResult.substring(0, 2000) + "..." : toolResult,
                    "contentOffset", toolContentOffset));
            toolResponses.add(new org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse(
                    firstTool.id(), toolName, toolResult));
        }

        messages.add(org.springframework.ai.chat.messages.ToolResponseMessage.builder()
                .responses(toolResponses)
                .build());

        List<Map<String, Object>> kbResultsRef = kbResultsHolder;
        Flux<String> afterTool = buildToolMetadataFlux(kbResultsRef, toolEventsList, ragMetadataHolder);

        List<Flux<String>> toolResultEvents = new ArrayList<>();
        final int resultContentOffset = toolContentOffset;
        for (org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse tr : toolResponses) {
            toolResultEvents.add(Flux.just(STATUS_PREFIX + toolResultEvent(tr.name(), tr.responseData(), resultContentOffset)));
        }
        Flux<String> toolEventFlux = Flux.concat(statusFluxes)
                .concatWith(Flux.concat(toolResultEvents))
                .concatWith(Flux.just(STATUS_PREFIX + toolCompleteEvent(resultContentOffset)))
                .concatWith(afterTool);
        return toolEventFlux.concatWith(processToolCallsRecursively(ctx, depth + 1, System.currentTimeMillis()));
    }

    private String executeToolCallback(Map<String, ToolCallback> toolCallbackMap, String toolName,
                                       String callArgs, Long agentId, String requestId) {
        ToolCallback callback = toolCallbackMap.get(toolName);
        if (callback != null) {
            try {
                return callback.call(callArgs, new ToolContext(Map.of(
                        "agentId", agentId,
                        "requestId", requestId)));
            } catch (Exception e) {
                log.error("[Chat] 工具执行异常: name={}, error={}", toolName, e.getMessage(), e);
                return "工具执行失败: " + e.getMessage();
            }
        }
        log.warn("[Chat][Trace] 工具不存在: name={}, 可用工具={}", toolName, toolCallbackMap.keySet());
        return "工具不存在: " + toolName;
    }

    private Flux<String> buildToolMetadataFlux(List<Map<String, Object>> kbResultsRef,
                                               List<Map<String, Object>> toolEventsList,
                                               String[] ragMetadataHolder) {
        return Flux.defer(() -> {
            if (!kbResultsRef.isEmpty() || !toolEventsList.isEmpty()) {
                Map<String, Object> metadataMap = new java.util.LinkedHashMap<>();
                if (!toolEventsList.isEmpty()) {
                    metadataMap.put("toolEvents", toolEventsList);
                    List<Integer> offsets = toolEventsList.stream()
                            .map(e -> e.get("contentOffset"))
                            .filter(java.util.Objects::nonNull)
                            .map(o -> ((Number) o).intValue())
                            .distinct()
                            .sorted()
                            .toList();
                    if (!offsets.isEmpty()) {
                        metadataMap.put("toolBlockOffsets", offsets);
                    }
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
                    return Flux.just(METADATA_PREFIX + ragMetadataHolder[0]);
                } catch (Exception e) {
                    log.warn("[Chat] 序列化metadata失败: {}", e.getMessage());
                }
            }
            return Flux.empty();
        });
    }

    /**
     * MiMo 直连流式（联网搜索 / 视频理解等）
     */
    private Flux<String> streamMimoDirect(ChatContext ctx, int depth, long llmCallStart,
                                          ModelProvider provider,
                                          List<org.springframework.ai.chat.messages.Message> messages) {
        StringBuilder fullReply = ctx.getFullReply();
        Map<String, Object> configMap = ctx.getConfigMap();
        SensitiveWordFilter.StreamState sensitiveState = ctx.getSensitiveStreamState();

        var mediaAttachments = ChatDocumentMessageUtil.filterMedia(ctx.getRequest().getAttachments());
        return mimoChatClient.streamChat(provider, configMap, messages, mediaAttachments)
                .concatMap(chunk -> {
                    if (chunk.startsWith(STATUS_PREFIX)) {
                        return Flux.just(chunk);
                    }
                    String delta = sensitiveState != null ? sensitiveState.processChunk(chunk) : chunk;
                    if (delta.isEmpty()) {
                        return Flux.empty();
                    }
                    fullReply.append(delta);
                    return Flux.just(delta);
                })
                .doOnComplete(() -> {
                    long elapsed = System.currentTimeMillis() - llmCallStart;
                    log.info("[Chat][MiMo] 直连完成: depth={}, elapsed={}ms, length={}",
                            depth, elapsed, fullReply.length());
                    if (fullReply.length() == 0) {
                        log.warn("[Chat][MiMo] 直连返回空内容: modelId={}, webSearch={}",
                                configMap.get("modelId"), configMap.get(ConfigKeys.Agent.ENABLE_WEB_SEARCH));
                    }
                })
                .doOnError(e -> log.error("[Chat][MiMo] 直连失败: {}", e.getMessage()));
    }

    private boolean isStreamOutputEnabled(Map<String, Object> configMap) {
        if (configMap == null) {
            return true;
        }
        Object val = configMap.get(ConfigKeys.Agent.STREAM_OUTPUT);
        if (val == null) {
            return true;
        }
        if (val instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(val.toString());
    }

    /**
     * 仅过滤thinking标签内容
     */
    private String stripThinkingTags(String text) {
        if (text == null || text.isEmpty()) return text;
        text = text.replaceAll("<think>[\\s\\S]*?</think>", "").trim();
        text = text.replaceAll("<thinking>[\\s\\S]*?</thinking>", "").trim();
        return text;
    }

    private float[] embedText(String text) {
        EmbeddingResponse response = embeddingModel.call(
                new EmbeddingRequest(List.of(text), null));
        return response.getResult().getOutput();
    }

    @Override
    public List<RagReferenceVO> getRagReferences(Long sessionId, Long agentId, String question) {
        Agent agent = initMiddleware.loadAgent(agentId);
        if (agent == null) {
            return List.of();
        }
        List<Map<String, Object>> searchResults = getRagSearchResults(agent.getId(), question);
        return searchResults.stream().map(row -> {
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
    }

    private List<Map<String, Object>> getRagSearchResults(Long agentId, String question) {
        List<Long> knowledgeIds = agentService.getKnowledgeIds(agentId);
        if (knowledgeIds.isEmpty()) {
            return List.of();
        }
        try {
            float[] queryVector = embedText(question);
            List<Map<String, Object>> allResults = new ArrayList<>();
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

    private int parseRagTopK(Knowledge knowledge) {
        if (knowledge == null) {
            return DEFAULT_RAG_TOP_K;
        }
        Map<String, Object> config = initMiddleware.parseConfig(knowledge.getConfig());
        Object val = config.get("ragTopK");
        return val instanceof Number ? ((Number) val).intValue() : DEFAULT_RAG_TOP_K;
    }

    private double parseRagThreshold(Knowledge knowledge) {
        if (knowledge == null) {
            return DEFAULT_RAG_THRESHOLD;
        }
        Map<String, Object> config = initMiddleware.parseConfig(knowledge.getConfig());
        Object val = config.get("ragThreshold");
        return val instanceof Number ? ((Number) val).doubleValue() : DEFAULT_RAG_THRESHOLD;
    }

    /**
     * 累加流式响应中的 Token 用量（OpenAI 兼容 API 通常在最后一个空 choices chunk 返回 usage）
     */
    /**
     * 按需推送 skill_active 事件：当工具调用属于某个 Skill 时，推送该 Skill 的 metadata。
     * 同一 Skill 只推送一次。
     */
    private Flux<String> emitSkillActiveIfNeeded(ChatContext ctx, String toolName,
                                                  List<Map<String, Object>> toolEventsList, int contentOffset) {
        Map<String, Map<String, Object>> mapping = ctx.getToolNameToSkillDetail();
        if (mapping == null || mapping.isEmpty()) {
            return Flux.empty();
        }
        Map<String, Object> skillDetail = mapping.get(toolName);
        if (skillDetail == null) {
            return Flux.empty();
        }
        String skillName = (String) skillDetail.get("name");
        // 同一 Skill 只推送一次
        boolean alreadyEmitted = toolEventsList.stream()
                .filter(e -> "skill_active".equals(e.get("type")))
                .flatMap(e -> {
                    Object skills = e.get("skills");
                    if (skills instanceof List<?> list) {
                        return list.stream();
                    }
                    return java.util.stream.Stream.empty();
                })
                .anyMatch(s -> {
                    if (s instanceof Map<?, ?> m) {
                        return skillName.equals(m.get("name"));
                    }
                    return false;
                });
        if (alreadyEmitted) {
            return Flux.empty();
        }
        List<Map<String, Object>> singleSkill = List.of(skillDetail);
        Map<String, Object> evt = new HashMap<>();
        evt.put("type", "skill_active");
        evt.put("skills", singleSkill);
        evt.put("contentOffset", contentOffset);
        toolEventsList.add(evt);
        try {
            return Flux.just(STATUS_PREFIX + OBJECT_MAPPER.writeValueAsString(evt));
        } catch (Exception e) {
            return Flux.empty();
        }
    }

    private void appendToolCallStart(ChatContext ctx, List<Map<String, Object>> toolEventsList,
                                     List<Flux<String>> statusFluxes,
                                     String toolName, String args, int contentOffset) {
        // 按需推送 skill_active（工具属于某个 Skill 时）
        Flux<String> skillFlux = emitSkillActiveIfNeeded(ctx, toolName, toolEventsList, contentOffset);
        if (skillFlux != null) {
            statusFluxes.add(skillFlux);
        }

        if (DelegateSubAgentTool.TOOL_NAME.equals(toolName)) {
            Map<String, String> parsed = parseSubagentArgs(args);
            String subName = parsed.get("subagentName");
            String displayName = parsed.get("displayName");
            String task = parsed.get("task");
            Map<String, Object> evt = new HashMap<>();
            evt.put("type", "subagent_call");
            evt.put("subagentName", subName);
            evt.put("displayName", displayName);
            evt.put("task", task);
            evt.put("contentOffset", contentOffset);
            toolEventsList.add(evt);
            statusFluxes.add(Flux.just(STATUS_PREFIX + subagentCallEvent(subName, displayName, task, contentOffset)));
            return;
        }
        toolEventsList.add(Map.of("type", "tool_call", "toolName", toolName, "args", args, "contentOffset", contentOffset));
        statusFluxes.add(Flux.just(STATUS_PREFIX + toolCallEvent(toolName, args, contentOffset)));
    }

    private void appendToolCallResult(List<Map<String, Object>> toolEventsList, List<Flux<String>> statusFluxes,
                                    String toolName, String args, String result, int contentOffset) {
        String truncated = result != null && result.length() > 2000 ? result.substring(0, 2000) + "..." : (result != null ? result : "");
        if (DelegateSubAgentTool.TOOL_NAME.equals(toolName)) {
            Map<String, String> parsed = parseSubagentArgs(args);
            String subName = parsed.get("subagentName");
            String displayName = parsed.get("displayName");
            Map<String, Object> evt = new HashMap<>();
            evt.put("type", "subagent_result");
            evt.put("subagentName", subName);
            evt.put("displayName", displayName);
            evt.put("result", truncated);
            evt.put("contentOffset", contentOffset);
            toolEventsList.add(evt);
            statusFluxes.add(Flux.just(STATUS_PREFIX + subagentResultEvent(subName, displayName, truncated, contentOffset)));
            return;
        }
        toolEventsList.add(Map.of("type", "tool_result", "toolName", toolName, "result", truncated, "contentOffset", contentOffset));
    }

    private Map<String, String> parseSubagentArgs(String args) {
        Map<String, String> out = new HashMap<>();
        out.put("subagentName", "");
        out.put("displayName", "");
        out.put("task", "");
        if (args == null || args.isBlank()) {
            return out;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = OBJECT_MAPPER.readValue(args, Map.class);
            Object nameObj = map.get("subagent_name");
            if (nameObj == null) {
                nameObj = map.get("subagentName");
            }
            String name = nameObj != null ? nameObj.toString() : "";
            out.put("subagentName", name);
            out.put("displayName", name);
            Object taskObj = map.get("task");
            if (taskObj != null) {
                out.put("task", taskObj.toString());
            }
        } catch (Exception e) {
            log.warn("[Chat] 解析 SubAgent 参数失败: {}", e.getMessage());
        }
        return out;
    }

    private void accumulateStreamUsage(ChatResponse response, int[] inputTokenHolder, int[] outputTokenHolder) {
        if (response == null || response.getMetadata() == null) {
            return;
        }
        org.springframework.ai.chat.metadata.Usage usage = response.getMetadata().getUsage();
        if (usage == null) {
            return;
        }
        Integer promptTokens = usage.getPromptTokens();
        Integer completionTokens = usage.getCompletionTokens();
        if (promptTokens != null) {
            inputTokenHolder[0] += promptTokens;
        }
        if (completionTokens != null) {
            outputTokenHolder[0] += completionTokens;
        }
    }
}
