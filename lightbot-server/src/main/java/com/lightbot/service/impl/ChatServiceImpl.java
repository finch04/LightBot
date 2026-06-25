package com.lightbot.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.constant.ConfigKeys;
import com.lightbot.constant.RagResultType;
import com.lightbot.constant.ToolResultPrefixes;
import com.lightbot.dto.ChatRequest;
import com.lightbot.dto.RagReferenceVO;
import com.lightbot.util.ChatDocumentMessageUtil;
import com.lightbot.util.RagParamResolver;
import com.lightbot.util.SensitiveWordFilter;
import com.lightbot.util.ToolArgsSanitizer;
import com.lightbot.entity.Agent;
import com.lightbot.entity.ModelProvider;
import com.lightbot.entity.ToolCall;
import com.lightbot.enums.ModelProviderType;
import com.lightbot.model.MimoChatClient;
import com.lightbot.subagent.DelegateSubAgentTool;
import com.lightbot.tool.ToolEventEmitter;
import com.lightbot.tool.builtin.AskUserTool;
import com.lightbot.tool.builtin.QueryKnowledgeTool;
import com.lightbot.entity.Knowledge;
import com.lightbot.dto.LlmTraceSpan;
import com.lightbot.enums.MessageRole;
import com.lightbot.service.*;
import com.lightbot.service.chat.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import com.lightbot.service.chat.ToolEventGenerator;

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
    private final ToolCallService toolCallService;

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
    private final TokenBudgetService tokenBudgetService;
    private final ObjectMapper objectMapper;
    private final ToolEventGenerator toolEventGenerator;
    private final ToolArgsSanitizer toolArgsSanitizer;
    private final RagParamResolver ragParamResolver;

    @Autowired
    @Qualifier("lightBotExecutor")
    private Executor lightBotExecutor;

    @Override
    public String chat(ChatRequest request) {
        // 1. 初始化上下文
        ChatContext ctx = ChatContext.of(request);
        ctx.setRequestId(String.valueOf(System.nanoTime()));
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

        // 2. 调用模型获取回复（带工具调用循环）
        String reply = processChatWithToolCalls(ctx);

        log.info("[Chat] AI回复: sessionId={}, length={}", ctx.getSessionId(), reply != null ? reply.length() : 0);

        // 3. 构建metadata并持久化AI回复
        String metadataStr = buildChatMetadata(ctx);
        int totalTokens = ctx.getInputTokenHolder()[0] + ctx.getOutputTokenHolder()[0];
        Long messageId = messageMiddleware.saveMessage(ctx.getSessionId(), MessageRole.ASSISTANT,
                reply, metadataStr, totalTokens);
        ctx.setAssistantMessageId(messageId);

        // 3.0 记录 Token 消耗
        if (ctx.getUserId() != null) {
            tokenBudgetService.recordUsage(ctx.getUserId(), ctx.getInputTokenHolder()[0], ctx.getOutputTokenHolder()[0]);
        }

        // 3.1 批量写入工具调用记录
        if (!ctx.getPendingToolCalls().isEmpty()) {
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            for (ToolCall tc : ctx.getPendingToolCalls()) {
                tc.setMessageId(messageId);
                if (tc.getCreatedAt() == null) {
                    tc.setCreatedAt(now);
                }
            }
            toolCallService.saveBatch(ctx.getPendingToolCalls());
        }

        // 4. 异步生成标题
        taskExecutor.execute(() -> traceMiddleware.generateTitle(ctx.getSessionId(), ctx.getAgent(), ctx.getConfigMap()));

        return reply;
    }

    /**
     * 非流式对话：处理带工具调用的多轮对话
     */
    private String processChatWithToolCalls(ChatContext ctx) {
        int maxSteps = resolveMaxExecutionSteps(ctx.getConfigMap());
        int retryTimes = resolveModelRetryTimes(ctx.getConfigMap());
        StringBuilder fullReply = ctx.getFullReply();
        List<Map<String, Object>> toolEventsList = ctx.getToolEventsList();
        String requestId = ctx.getRequestId();
        Map<String, ToolCallback> toolCallbackMap = ctx.getToolCallbackMap();
        Agent agent = ctx.getAgent();

        for (int depth = 0; depth < maxSteps; depth++) {
            ChatResponse response = callModelWithRetry(ctx, retryTimes);
            if (response == null) {
                return fullReply.toString();
            }

            accumulateStreamUsage(response, ctx.getInputTokenHolder(), ctx.getOutputTokenHolder());
            Generation gen = response.getResult();
            AssistantMessage assistantMsg = (gen != null) ? gen.getOutput() : null;

            // 检查reasoningContent
            if (gen != null && gen.getOutput() != null && gen.getOutput().getMetadata() != null) {
                Object reasoningObj = gen.getOutput().getMetadata().get("reasoningContent");
                if (reasoningObj != null && !reasoningObj.toString().isBlank()) {
                    ctx.getReasoningContent().append(reasoningObj.toString());
                }
            }

            // 无工具调用 → 直接返回结果
            if (assistantMsg == null || !assistantMsg.hasToolCalls()) {
                String text = (assistantMsg != null) ? assistantMsg.getText() : "";
                String stripped = stripThinkingTags(text);
                String filtered = SensitiveWordFilter.filterAiOutput(
                        stripped, ctx.getConfigMap(), agent.getId(), ctx.getSessionId()).text();
                fullReply.append(filtered);
                return fullReply.toString();
            }

            // 有工具调用 → 执行工具并继续循环
            ctx.getMessages().add(assistantMsg);
            List<AssistantMessage.ToolCall> toolCalls = assistantMsg.getToolCalls();

            List<org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();
            int toolContentOffset = fullReply.length();

            // 目前非流式只处理第一个工具调用（简化处理）
            AssistantMessage.ToolCall firstTool = toolCalls.get(0);
            String toolName = firstTool.name();
            String toolArgs = firstTool.arguments();
            ctx.getToolCallCountHolder()[0]++;

            String safeArgs = toolArgs != null ? toolArgs : "";

            // 记录工具调用开始
            String dnSync = getToolDisplayName(ctx, toolName);
            Map<String, Object> callEvtSync = new java.util.LinkedHashMap<>();
            callEvtSync.put("type", "tool_call");
            callEvtSync.put("toolName", toolName);
            if (dnSync != null) callEvtSync.put("displayName", dnSync);
            callEvtSync.put("args", safeArgs);
            callEvtSync.put("contentOffset", toolContentOffset);
            toolEventsList.add(callEvtSync);

            // 执行工具
            String toolResult = executeToolCallback(toolCallbackMap, toolName, safeArgs, agent.getId(), ctx.getSessionId(), requestId, null);

            // 暂存工具调用记录
            ToolCall toolCallLog = new ToolCall();
            toolCallLog.setToolName(toolName);
            toolCallLog.setToolInput(safeArgs);
            toolCallLog.setToolOutput(toolResult);
            toolCallLog.setStatus(ToolResultPrefixes.isError(toolResult) ? "error" : "success");
            toolCallLog.setErrorMessage(ToolResultPrefixes.isError(toolResult) ? toolResult : null);
            ctx.getPendingToolCalls().add(toolCallLog);

            // 记录知识库检索结果
            if ("query_knowledge".equals(toolName)) {
                List<Map<String, Object>> kbResults = QueryKnowledgeTool.getSearchResults(requestId);
                if (!kbResults.isEmpty()) {
                    ctx.getRagMetadataHolder()[0] = buildRagMetadataJson(kbResults);
                }
            }

            // 记录工具结果（JSON结果不截断，纯文本限制2000字符）
            String sseResult = truncateForSse(toolResult);
            Map<String, Object> resEvtSync = new java.util.LinkedHashMap<>();
            resEvtSync.put("type", "tool_result");
            resEvtSync.put("toolName", toolName);
            if (dnSync != null) resEvtSync.put("displayName", dnSync);
            resEvtSync.put("result", sseResult);
            resEvtSync.put("contentOffset", toolContentOffset);
            toolEventsList.add(resEvtSync);

            toolResponses.add(new org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse(
                    firstTool.id(), toolName, toolResult));

            ctx.getMessages().add(org.springframework.ai.chat.messages.ToolResponseMessage.builder()
                    .responses(toolResponses)
                    .build());

            // ask_user 工具执行后中断循环，等待用户回复
            boolean hasAskUser = toolResponses.stream()
                    .anyMatch(r -> AskUserTool.TOOL_NAME.equals(r.name()));
            if (hasAskUser) {
                log.info("[Chat][Trace] ask_user 工具调用，中断工具循环，等待用户回复");
                break;
            }
        }

        return fullReply.toString();
    }

    /**
     * 带重试的模型调用
     */
    private ChatResponse callModelWithRetry(ChatContext ctx, int retryTimes) {
        Exception lastException = null;
        for (int attempt = 0; attempt <= retryTimes; attempt++) {
            try {
                return ctx.getChatModel().call(new Prompt(ctx.getMessages(), ctx.getToolOptions()));
            } catch (Exception e) {
                lastException = e;
                if (attempt < retryTimes) {
                    long delayMs = (long) Math.pow(2, attempt) * 1000;
                    log.warn("[Chat] 模型调用失败，第{}次重试，等待{}ms: {}", attempt + 1, delayMs, e.getMessage());
                    try { Thread.sleep(delayMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                }
            }
        }
        if (lastException != null) {
            log.error("[Chat] 模型调用最终失败: {}", lastException.getMessage());
        }
        return null;
    }

    /**
     * 构建知识库检索结果的metadata JSON
     */
    private String buildRagMetadataJson(List<Map<String, Object>> kbResults) {
        try {
            Map<String, Object> metadataMap = new LinkedHashMap<>();
            List<RagReferenceVO> refs = kbResults.stream().map(row -> {
                RagReferenceVO vo = new RagReferenceVO();
                String resultType = (String) row.get("result_type");
                if (RagResultType.QA_PAIR.equals(resultType)) {
                    vo.setSourceType(RagResultType.QA_PAIR);
                    vo.setDocumentName("问答对");
                    Object qaPairId = row.get("id");
                    vo.setQaPairId(qaPairId != null ? ((Number) qaPairId).longValue() : null);
                    String q = (String) row.get("question");
                    String a = (String) row.get("answer");
                    vo.setContentPreview("问题：" + q + "\n答案：" + a);
                } else {
                    vo.setSourceType(RagResultType.CHUNK);
                    vo.setDocumentName((String) row.get("document_name"));
                    String content = (String) row.get("content");
                    vo.setContentPreview(content != null && content.length() > 200
                            ? content.substring(0, 200) + "..." : content);
                }
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
            return objectMapper.writeValueAsString(metadataMap);
        } catch (Exception e) {
            log.warn("[Chat] 构建RAG metadata失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 构建非流式对话的metadata
     */
    private String buildChatMetadata(ChatContext ctx) {
        try {
            Map<String, Object> meta = new LinkedHashMap<>();

            // 1. 添加RAG检索结果
            String ragMeta = ctx.getRagMetadataHolder()[0];
            if (ragMeta != null && !ragMeta.isBlank()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> existing = objectMapper.readValue(ragMeta, Map.class);
                meta.putAll(existing);
            }

            // 2. 添加工具事件
            List<Map<String, Object>> toolEventsList = ctx.getToolEventsList();
            if (!toolEventsList.isEmpty()) {
                meta.put("toolEvents", toolEventsList);
                // 计算toolBlockOffsets
                List<Integer> offsets = toolEventsList.stream()
                        .map(e -> e.get("contentOffset"))
                        .filter(Objects::nonNull)
                        .map(o -> ((Number) o).intValue())
                        .distinct()
                        .sorted()
                        .toList();
                if (!offsets.isEmpty()) {
                    meta.put("toolBlockOffsets", offsets);
                }
            }

            // 3. 添加reasoningContent
            if (ctx.getReasoningContent().length() > 0) {
                meta.put("reasoningContent", ctx.getReasoningContent().toString());
            }

            // 4. 添加requestId
            if (ctx.getRequestId() != null && !ctx.getRequestId().isBlank()) {
                meta.put("requestId", ctx.getRequestId());
            }

            return meta.isEmpty() ? null : objectMapper.writeValueAsString(meta);
        } catch (Exception e) {
            log.warn("[Chat] 构建chat metadata失败: {}", e.getMessage());
            return null;
        }
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
                .concatWith(Mono.fromCallable(() -> buildDoneEvent(ctx)));
    }

    /**
     * 构建 [DONE] 事件：先持久化 AI 回复，再返回带消息ID的完成标记
     * <p>此方法在 Mono.fromCallable 中执行（Flux 最后一个元素），此时流式内容已全部累加。
     * Trace 记录、标题生成等后置操作仍由 TraceMiddleware.doOnComplete 处理。</p>
     */
    private String buildDoneEvent(ChatContext ctx) {
        try {
            long totalTokens = ctx.getInputTokenHolder()[0] + ctx.getOutputTokenHolder()[0];
            Long agentId = ctx.getAgent() != null ? ctx.getAgent().getId() : null;

            // 0. 记录 Token 消耗到预算服务
            if (ctx.getUserId() != null) {
                tokenBudgetService.recordUsage(ctx.getUserId(), ctx.getInputTokenHolder()[0], ctx.getOutputTokenHolder()[0]);
            }

            // 1. 持久化 AI 回复
            String replyToSave = SensitiveWordFilter.filterAiOutput(
                    ctx.getFullReply().toString(), ctx.getConfigMap(), agentId, ctx.getSessionId()).text();
            String metadataStr = buildPersistMetadata(ctx);
            Long assistantMessageId = messageMiddleware.saveMessage(
                    ctx.getSessionId(), MessageRole.ASSISTANT,
                    replyToSave, metadataStr, (int) totalTokens);
            ctx.setAssistantMessageId(assistantMessageId);

            // 1.1 批量写入工具调用记录
            if (!ctx.getPendingToolCalls().isEmpty()) {
                java.time.LocalDateTime now = java.time.LocalDateTime.now();
                for (ToolCall tc : ctx.getPendingToolCalls()) {
                    tc.setMessageId(assistantMessageId);
                    if (tc.getCreatedAt() == null) {
                        tc.setCreatedAt(now);
                    }
                }
                toolCallService.saveBatch(ctx.getPendingToolCalls());
            }
            ctx.getFullReply().setLength(0);
            ctx.getFullReply().append(replyToSave);

            // 2. 返回带消息ID和Token数的 [DONE] 事件
            return toolEventGenerator.doneWithMetadata(ctx.getUserMessageId(), assistantMessageId, totalTokens);
        } catch (Exception e) {
            log.error("[Chat] 构建[DONE]事件异常: {}", e.getMessage(), e);
            return DONE_PREFIX;
        }
    }

    /**
     * 构建持久化 metadata：合并 ragMetadata + reasoningContent + sensitiveBlock + requestId
     */
    private String buildPersistMetadata(ChatContext ctx) {
        try {
            Map<String, Object> meta = new java.util.LinkedHashMap<>();
            String ragMeta = ctx.getRagMetadataHolder()[0];
            if (ragMeta != null && !ragMeta.isBlank()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> existing = objectMapper.readValue(ragMeta, Map.class);
                meta.putAll(existing);
            }
            if (ctx.getReasoningContent().length() > 0) {
                meta.put("reasoningContent", ctx.getReasoningContent().toString());
            }
            if (ctx.getSensitiveStreamState() != null && ctx.getSensitiveStreamState().isBlocked()) {
                meta.put("sensitiveBlock", "ai_output");
            }
            if (ctx.getRequestId() != null && !ctx.getRequestId().isBlank()) {
                meta.put("requestId", ctx.getRequestId());
            }
            return meta.isEmpty() ? null : objectMapper.writeValueAsString(meta);
        } catch (Exception e) {
            log.warn("[Chat] 构建持久化metadata失败: {}", e.getMessage());
            return ctx.getRagMetadataHolder()[0];
        }
    }

    /**
     * 流式核心：递归工具调用循环
     * <p>创建 Sinks.Many 用于工具执行期间的实时状态推送。
     * 工具内部通过 {@code ToolEventEmitter.emit()} 写入 Sink，
     * 此处订阅 Sink 将 tool_status 事件实时发送给前端。</p>
     */
    private Flux<String> streamCore(ChatContext ctx) {
        ctx.setStartTime(System.currentTimeMillis());
        Long agentId = ctx.getAgent() != null ? ctx.getAgent().getId() : null;
        ctx.setSensitiveStreamState(new SensitiveWordFilter.StreamState(
                ctx.getConfigMap(), agentId, ctx.getSessionId()));

        Sinks.Many<String> eventSink = Sinks.many().multicast().onBackpressureBuffer();
        Flux<String> toolStatusFlux = eventSink.asFlux()
                .map(msg -> STATUS_PREFIX + toolEventGenerator.toolStatusEvent(msg, 0));

        return toolStatusFlux.mergeWith(
                processToolCallsRecursively(ctx, 0, System.currentTimeMillis(), eventSink)
                        .doFinally(signal -> eventSink.tryEmitComplete()));
    }

    /**
     * 递归处理工具调用：调用LLM → 检测工具 → 执行 → 重新调用LLM
     *
     * @param ctx          管道上下文
     * @param depth        递归深度（防止无限循环）
     * @param llmCallStart 本轮LLM调用开始时间
     * @param eventSink    工具状态事件实时推送通道
     * @return Flux<String> 流式输出片段
     */
    private Flux<String> processToolCallsRecursively(ChatContext ctx, int depth, long llmCallStart,
                                                      Sinks.Many<String> eventSink) {
        int maxSteps = resolveMaxExecutionSteps(ctx.getConfigMap());
        if (depth >= maxSteps) {
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
            return processBlockingRound(ctx, depth, llmCallStart, eventSink);
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
                                    return Flux.just(STATUS_PREFIX + toolEventGenerator.reasoningEvent(reasoning));
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
                            return Flux.just(STATUS_PREFIX + toolEventGenerator.sensitiveBlockEvent("ai_output", SensitiveWordFilter.AI_BLOCK_MESSAGE));
                        }
                        if (delta.isEmpty()) {
                            return Flux.empty();
                        }

                        fullReply.append(delta);
                        if (!llmSpanAdded[0]) {
                            spans.add(LlmTraceSpan.of(llmSpanId, "s1", "llm_call", llmCallStart,
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
                        spans.add(LlmTraceSpan.of(llmSpanId, "s1", "llm_call", llmCallStart,
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
                            final String safeTcArgs = toolArgsSanitizer.forChatCall(tcArgs);
                            final Sinks.Many<String> sink = eventSink;
                            futures.add(CompletableFuture.supplyAsync(() -> {
                                long tStart = System.currentTimeMillis();
                                // 绑定 Sink 到当前 worker 线程，使 emit() 实时推送
                                if (sink != null) {
                                    ToolEventEmitter.setupSink(sink);
                                }
                                String result;
                                try {
                                    result = executeToolCallback(toolCallbackMap, tcName, safeTcArgs,
                                            agent.getId(), ctx.getSessionId(), requestId, sink);
                                } finally {
                                    if (sink != null) {
                                        ToolEventEmitter.teardownSink();
                                    }
                                }
                                long tEnd = System.currentTimeMillis();
                                log.info("[Chat][Trace] 工具执行结果: name={}, 耗时={}ms, resultLength={}", tcName, tEnd - tStart, result.length());
                                spans.add(LlmTraceSpan.of("tool_" + toolCallCountHolder[0], llmSpanId, "tool_execute",
                                        tStart, tEnd - tStart, "OK",
                                        Map.of("toolName", tcName, "args", tcArgs, "resultLength", result.length())));
                                if ("query_knowledge".equals(tcName)) {
                                    List<Map<String, Object>> kbResults = QueryKnowledgeTool.getSearchResults(requestId);
                                    synchronized (kbResultsHolder) { kbResultsHolder.addAll(kbResults); }
                                }
                                // 暂存工具调用记录（assistant 消息保存后批量写入）
                                ToolCall toolCallLog = new ToolCall();
                                toolCallLog.setToolName(tcName);
                                toolCallLog.setToolInput(safeTcArgs);
                                toolCallLog.setToolOutput(result);
                                toolCallLog.setStatus(result.startsWith(ToolResultPrefixes.FAILURE) || result.startsWith(ToolResultPrefixes.NOT_FOUND) ? "error" : "success");
                                toolCallLog.setErrorMessage(result.startsWith(ToolResultPrefixes.FAILURE) ? result : null);
                                synchronized (ctx.getPendingToolCalls()) {
                                    ctx.getPendingToolCalls().add(toolCallLog);
                                }

                                appendToolCallResult(ctx, toolEventsList, statusFluxes, tcName, tcArgs, result, toolContentOffset);
                                return result;
                            }, lightBotExecutor));
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
                        String callArgs = toolArgsSanitizer.forChatCall(safeArgs);
                        appendToolCallStart(ctx, toolEventsList, statusFluxes, toolName, safeArgs, toolContentOffset);

                        long tToolStart = System.currentTimeMillis();
                        // 流式模式：绑定 Sink 使工具内部 emit() 实时推送给前端
                        ToolEventEmitter.setupSink(eventSink);
                        String toolResult;
                        try {
                            toolResult = executeToolCallback(toolCallbackMap, toolName, callArgs,
                                    agent.getId(), ctx.getSessionId(), requestId, eventSink);
                        } finally {
                            ToolEventEmitter.teardownSink();
                        }
                        long tToolEnd = System.currentTimeMillis();
                        log.info("[Chat][Trace] 工具执行结果: name={}, 耗时={}ms, resultLength={}", toolName, tToolEnd - tToolStart, toolResult.length());

                        spans.add(LlmTraceSpan.of("tool_" + toolCallCountHolder[0], llmSpanId, "tool_execute",
                                tToolStart, tToolEnd - tToolStart, "OK",
                                Map.of("toolName", toolName, "args", safeArgs, "resultLength", toolResult.length())));

                        if ("query_knowledge".equals(toolName)) {
                            List<Map<String, Object>> kbResults = QueryKnowledgeTool.getSearchResults(requestId);
                            if (!kbResults.isEmpty()) kbResultsHolder.addAll(kbResults);
                        }

                        // 暂存工具调用记录（assistant 消息保存后批量写入）
                        ToolCall toolCallLog = new ToolCall();
                        toolCallLog.setToolName(toolName);
                        toolCallLog.setToolInput(safeArgs);
                        toolCallLog.setToolOutput(toolResult);
                        toolCallLog.setStatus(ToolResultPrefixes.isError(toolResult) ? "error" : "success");
                        toolCallLog.setErrorMessage(ToolResultPrefixes.isError(toolResult) ? toolResult : null);
                        ctx.getPendingToolCalls().add(toolCallLog);

                        appendToolCallResult(ctx, toolEventsList, statusFluxes, toolName, safeArgs, toolResult, toolContentOffset);
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
                                    String resultType = (String) row.get("result_type");
                                    if (RagResultType.QA_PAIR.equals(resultType)) {
                                        vo.setSourceType(RagResultType.QA_PAIR);
                                        vo.setDocumentName("问答对");
                                        Object qaPairId = row.get("id");
                                        vo.setQaPairId(qaPairId != null ? ((Number) qaPairId).longValue() : null);
                                        String q = (String) row.get("question");
                                        String a = (String) row.get("answer");
                                        vo.setContentPreview("问题：" + q + "\n答案：" + a);
                                    } else {
                                        vo.setSourceType(RagResultType.CHUNK);
                                        vo.setDocumentName((String) row.get("document_name"));
                                        String content = (String) row.get("content");
                                        vo.setContentPreview(content != null && content.length() > 200
                                                ? content.substring(0, 200) + "..." : content);
                                    }
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
                                ragMetadataHolder[0] = objectMapper.writeValueAsString(metadataMap);
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
                            String dn = getToolDisplayName(ctx, tr.name());
                            toolResultEvents.add(Flux.just(STATUS_PREFIX + toolEventGenerator.toolResultEvent(tr.name(), dn, tr.responseData(), resultContentOffset)));
                        }
                    }
                    Flux<String> toolEventFlux = Flux.concat(statusFluxes)
                            .concatWith(Flux.concat(toolResultEvents))
                            .concatWith(Flux.just(STATUS_PREFIX + toolEventGenerator.toolCompleteEvent(resultContentOffset)))
                            .concatWith(afterTool);

                    // ask_user 工具执行后中断循环，等待用户回复
                    boolean hasAskUser = toolResponses.stream()
                            .anyMatch(r -> AskUserTool.TOOL_NAME.equals(r.name()));
                    if (hasAskUser) {
                        log.info("[Chat][Trace] ask_user 工具调用，中断工具循环，等待用户回复");
                        return toolEventFlux;
                    }

                    return toolEventFlux.concatWith(processToolCallsRecursively(ctx, depth + 1, nextLlmStart, eventSink));
                });
    }

    /**
     * 非流式 LLM 轮次：call() 获取完整回复后一次性输出
     */
    private Flux<String> processBlockingRound(ChatContext ctx, int depth, long llmCallStart,
                                               Sinks.Many<String> eventSink) {
        int maxSteps = resolveMaxExecutionSteps(ctx.getConfigMap());
        if (depth >= maxSteps) {
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
        int retryTimes = resolveModelRetryTimes(configMap);

        ChatResponse response = null;
        Exception lastException = null;
        for (int attempt = 0; attempt <= retryTimes; attempt++) {
            try {
                response = chatModel.call(prompt);
                break;
            } catch (Exception e) {
                lastException = e;
                if (attempt < retryTimes) {
                    long delayMs = (long) Math.pow(2, attempt) * 1000;
                    log.warn("[Chat] 非流式模型调用失败，第{}次重试，等待{}ms: depth={}, error={}", attempt + 1, delayMs, depth, e.getMessage());
                    try { Thread.sleep(delayMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                }
            }
        }
        if (response == null) {
            log.error("[Chat] 非流式模型调用最终失败: depth={}", depth, lastException);
            return Flux.just("\n[模型调用失败，请稍后重试]");
        }
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
                        return Flux.just(STATUS_PREFIX + toolEventGenerator.reasoningEvent(reasoningObj.toString()));
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
                spans.add(LlmTraceSpan.of(llmSpanId, "s1", "llm_call", llmCallStart,
                        System.currentTimeMillis() - llmCallStart, "OK",
                        Map.of("depth", depth, "model", configMap.getOrDefault("modelId", ""),
                                "inputTokens", inputTokenHolder[0], "outputTokens", outputTokenHolder[0],
                                "streamOutput", false)));
                return Flux.just(STATUS_PREFIX + toolEventGenerator.sensitiveBlockEvent("ai_output", filtered.text()));
            }
            fullReply.append(filtered.text());
            spans.add(LlmTraceSpan.of(llmSpanId, "s1", "llm_call", llmCallStart,
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

        spans.add(LlmTraceSpan.of(llmSpanId, "s1", "llm_call", llmCallStart,
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
                String dn = getToolDisplayName(ctx, tc.name());
                Map<String, Object> callEvt = new java.util.LinkedHashMap<>();
                callEvt.put("type", "tool_call");
                callEvt.put("toolName", tc.name());
                if (dn != null) callEvt.put("displayName", dn);
                callEvt.put("args", tcArgs);
                callEvt.put("contentOffset", toolContentOffset);
                toolEventsList.add(callEvt);
                statusFluxes.add(Flux.just(STATUS_PREFIX + toolEventGenerator.toolCallEvent(tc.name(), dn, tcArgs, toolContentOffset)));
                toolCallCountHolder[0]++;
                final String tcName = tc.name();
                final String safeTcArgs = toolArgsSanitizer.forChatCall(tcArgs);
                final String dnFinal = dn;
                futures.add(CompletableFuture.supplyAsync(() -> {
                    long tStart = System.currentTimeMillis();
                    String result = executeToolCallback(toolCallbackMap, tcName, safeTcArgs, agent.getId(), ctx.getSessionId(), requestId, null);
                    long tEnd = System.currentTimeMillis();
                    spans.add(LlmTraceSpan.of("tool_" + toolCallCountHolder[0], llmSpanId, "tool_execute",
                            tStart, tEnd - tStart, "OK",
                            Map.of("toolName", tcName, "args", tcArgs, "resultLength", result.length())));
                    if ("query_knowledge".equals(tcName)) {
                        List<Map<String, Object>> kbResults = QueryKnowledgeTool.getSearchResults(requestId);
                        synchronized (kbResultsHolder) {
                            kbResultsHolder.addAll(kbResults);
                        }
                    }
                    // 暂存工具调用记录（assistant 消息保存后批量写入）
                    ToolCall toolCallLog = new ToolCall();
                    toolCallLog.setToolName(tcName);
                    toolCallLog.setToolInput(safeTcArgs);
                    toolCallLog.setToolOutput(result);
                    toolCallLog.setStatus(result.startsWith(ToolResultPrefixes.FAILURE) || result.startsWith(ToolResultPrefixes.NOT_FOUND) ? "error" : "success");
                    toolCallLog.setErrorMessage(result.startsWith(ToolResultPrefixes.FAILURE) ? result : null);
                    synchronized (ctx.getPendingToolCalls()) {
                        ctx.getPendingToolCalls().add(toolCallLog);
                    }

                    Map<String, Object> resEvt = new java.util.LinkedHashMap<>();
                    resEvt.put("type", "tool_result");
                    resEvt.put("toolName", tcName);
                    if (dnFinal != null) resEvt.put("displayName", dnFinal);
                    resEvt.put("result", truncateForSse(result));
                    resEvt.put("contentOffset", toolContentOffset);
                    toolEventsList.add(resEvt);
                    return result;
                }, lightBotExecutor));
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
            String callArgs = toolArgsSanitizer.forChatCall(safeArgs);
            // 按需推送 skill_active
            Flux<String> skillFlux = emitSkillActiveIfNeeded(ctx, toolName, toolEventsList, toolContentOffset);
            if (skillFlux != null) {
                statusFluxes.add(skillFlux);
            }
            String dnSeq = getToolDisplayName(ctx, toolName);
            Map<String, Object> callEvtSeq = new java.util.LinkedHashMap<>();
            callEvtSeq.put("type", "tool_call");
            callEvtSeq.put("toolName", toolName);
            if (dnSeq != null) callEvtSeq.put("displayName", dnSeq);
            callEvtSeq.put("args", safeArgs);
            callEvtSeq.put("contentOffset", toolContentOffset);
            toolEventsList.add(callEvtSeq);
            statusFluxes.add(Flux.just(STATUS_PREFIX + toolEventGenerator.toolCallEvent(toolName, dnSeq, safeArgs, toolContentOffset)));

            long tToolStart = System.currentTimeMillis();
            String toolResult = executeToolCallback(toolCallbackMap, toolName, callArgs, agent.getId(), ctx.getSessionId(), requestId, null);
            long tToolEnd = System.currentTimeMillis();
            spans.add(LlmTraceSpan.of("tool_" + toolCallCountHolder[0], llmSpanId, "tool_execute",
                    tToolStart, tToolEnd - tToolStart, "OK",
                    Map.of("toolName", toolName, "args", safeArgs, "resultLength", toolResult.length())));

            if ("query_knowledge".equals(toolName)) {
                List<Map<String, Object>> kbResults = QueryKnowledgeTool.getSearchResults(requestId);
                if (!kbResults.isEmpty()) {
                    kbResultsHolder.addAll(kbResults);
                }
            }

            // 暂存工具调用记录（assistant 消息保存后批量写入）
            ToolCall toolCallLog = new ToolCall();
            toolCallLog.setToolName(toolName);
            toolCallLog.setToolInput(callArgs);
            toolCallLog.setToolOutput(toolResult);
            toolCallLog.setStatus(ToolResultPrefixes.isError(toolResult) ? "error" : "success");
            toolCallLog.setErrorMessage(ToolResultPrefixes.isError(toolResult) ? toolResult : null);
            ctx.getPendingToolCalls().add(toolCallLog);

            List<String> emittedEvents = ToolEventEmitter.drain();
            for (String event : emittedEvents) {
                toolEventsList.add(Map.of("type", "tool_status", "message", event,
                        "contentOffset", toolContentOffset));
                statusFluxes.add(Flux.just(STATUS_PREFIX + toolEventGenerator.toolStatusEvent(event, toolContentOffset)));
            }

            Map<String, Object> resEvtSeq = new java.util.LinkedHashMap<>();
            resEvtSeq.put("type", "tool_result");
            resEvtSeq.put("toolName", toolName);
            if (dnSeq != null) resEvtSeq.put("displayName", dnSeq);
            resEvtSeq.put("result", truncateForSse(toolResult));
            resEvtSeq.put("contentOffset", toolContentOffset);
            toolEventsList.add(resEvtSeq);
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
            String dnRes = getToolDisplayName(ctx, tr.name());
            toolResultEvents.add(Flux.just(STATUS_PREFIX + toolEventGenerator.toolResultEvent(tr.name(), dnRes, tr.responseData(), resultContentOffset)));
        }
        Flux<String> toolEventFlux = Flux.concat(statusFluxes)
                .concatWith(Flux.concat(toolResultEvents))
                .concatWith(Flux.just(STATUS_PREFIX + toolEventGenerator.toolCompleteEvent(resultContentOffset)))
                .concatWith(afterTool);
        return toolEventFlux.concatWith(processToolCallsRecursively(ctx, depth + 1, System.currentTimeMillis(), eventSink));
    }

    private String executeToolCallback(Map<String, ToolCallback> toolCallbackMap, String toolName,
                                       String callArgs, Long agentId, Long sessionId, String requestId,
                                       Sinks.Many<String> eventSink) {
        ToolCallback callback = toolCallbackMap.get(toolName);
        if (callback != null) {
            try {
                // 流式模式：绑定 Sink 使工具内部的 emit() 实时推送给前端
                if (eventSink != null) {
                    ToolEventEmitter.setupSink(eventSink);
                }
                try {
                    return callback.call(callArgs, new ToolContext(Map.of(
                            "agentId", agentId,
                            "sessionId", sessionId != null ? sessionId.toString() : "default",
                            "requestId", requestId)));
                } finally {
                    if (eventSink != null) {
                        ToolEventEmitter.teardownSink();
                    }
                }
            } catch (Exception e) {
                log.error("[Chat] 工具执行异常: name={}, error={}", toolName, e.getMessage(), e);
                return ToolResultPrefixes.failureJson(ToolResultPrefixes.FAILURE + ": " + e.getMessage());
            }
        }
        log.warn("[Chat][Trace] 工具不存在: name={}, 可用工具={}", toolName, toolCallbackMap.keySet());
        return ToolResultPrefixes.failureJson(ToolResultPrefixes.NOT_FOUND + ": " + toolName);
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
                        String resultType = (String) row.get("result_type");
                        if (RagResultType.QA_PAIR.equals(resultType)) {
                            vo.setSourceType(RagResultType.QA_PAIR);
                            vo.setDocumentName("问答对");
                            Object qaPairId = row.get("id");
                            vo.setQaPairId(qaPairId != null ? ((Number) qaPairId).longValue() : null);
                            String q = (String) row.get("question");
                            String a = (String) row.get("answer");
                            vo.setContentPreview("问题：" + q + "\n答案：" + a);
                        } else {
                            vo.setSourceType(RagResultType.CHUNK);
                            vo.setDocumentName((String) row.get("document_name"));
                            String content = (String) row.get("content");
                            vo.setContentPreview(content != null && content.length() > 200
                                    ? content.substring(0, 200) + "..." : content);
                        }
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
                    ragMetadataHolder[0] = objectMapper.writeValueAsString(metadataMap);
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
     * <p>MiMo 特有逻辑（reasoning 提取、多模态处理）已内聚在 MimoChatClient 中，
     * 此处仅处理通用关注点：敏感词过滤、回复累积、日志</p>
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
                    // MimoChatClient 已处理 reasoning 提取（emitReasoningContent），
                    // 此处直接透传 [STATUS] 事件，无需重复解析
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

    private int resolveMaxExecutionSteps(Map<String, Object> configMap) {
        if (configMap == null) return 10;
        Object val = configMap.get(ConfigKeys.Agent.MAX_EXECUTION_STEPS);
        if (val instanceof Number n) return Math.max(1, Math.min(100, n.intValue()));
        if (val != null) {
            try { return Math.max(1, Math.min(100, Integer.parseInt(val.toString()))); } catch (Exception ignored) {}
        }
        return 10;
    }

    private int resolveModelRetryTimes(Map<String, Object> configMap) {
        if (configMap == null) return 2;
        Object val = configMap.get(ConfigKeys.Agent.MODEL_RETRY_TIMES);
        if (val instanceof Number n) return Math.max(0, Math.min(10, n.intValue()));
        if (val != null) {
            try { return Math.max(0, Math.min(10, Integer.parseInt(val.toString()))); } catch (Exception ignored) {}
        }
        return 2;
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
            String resultType = (String) row.get("result_type");
            if (RagResultType.QA_PAIR.equals(resultType)) {
                vo.setSourceType(RagResultType.QA_PAIR);
                vo.setDocumentName("问答对");
                Object qaPairId = row.get("id");
                vo.setQaPairId(qaPairId != null ? ((Number) qaPairId).longValue() : null);
                String q = (String) row.get("question");
                String a = (String) row.get("answer");
                vo.setContentPreview("问题：" + q + "\n答案：" + a);
            } else {
                vo.setSourceType(RagResultType.CHUNK);
                vo.setDocumentName((String) row.get("document_name"));
                String content = (String) row.get("content");
                vo.setContentPreview(content != null && content.length() > 200
                        ? content.substring(0, 200) + "..." : content);
            }
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
                            int topK = ragParamResolver.resolveTopK(null, null, knowledge != null ? knowledge.getConfig() : null, RagParamResolver.DEFAULT_TOP_K);
                            double threshold = ragParamResolver.resolveThreshold(null, null, knowledge != null ? knowledge.getConfig() : null, RagParamResolver.DEFAULT_THRESHOLD);
                            return embeddingService.searchSimilar(knowledgeId, queryVector, topK, threshold);
                        } catch (Exception e) {
                            log.warn("[Chat] 知识库检索失败: knowledgeId={}, error={}", knowledgeId, e.getMessage());
                            return List.<Map<String, Object>>of();
                        }
                    }, lightBotExecutor))
                    .toList();
            futures.forEach(f -> allResults.addAll(f.join()));
            return allResults;
        } catch (Exception e) {
            log.warn("[Chat] RAG检索失败: {}", e.getMessage());
            return List.of();
        }
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
            return Flux.just(STATUS_PREFIX + objectMapper.writeValueAsString(evt));
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
            statusFluxes.add(Flux.just(STATUS_PREFIX + toolEventGenerator.subagentCallEvent(subName, displayName, task, contentOffset)));
            return;
        }
        String dn = getToolDisplayName(ctx, toolName);
        Map<String, Object> callEvt = new java.util.LinkedHashMap<>();
        callEvt.put("type", "tool_call");
        callEvt.put("toolName", toolName);
        if (dn != null) callEvt.put("displayName", dn);
        callEvt.put("args", args);
        callEvt.put("contentOffset", contentOffset);
        toolEventsList.add(callEvt);
        statusFluxes.add(Flux.just(STATUS_PREFIX + toolEventGenerator.toolCallEvent(toolName, dn, args, contentOffset)));
    }

    private void appendToolCallResult(ChatContext ctx, List<Map<String, Object>> toolEventsList, List<Flux<String>> statusFluxes,
                                    String toolName, String args, String result, int contentOffset) {
        String truncated = truncateForSse(result);
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
            statusFluxes.add(Flux.just(STATUS_PREFIX + toolEventGenerator.subagentResultEvent(subName, displayName, truncated, contentOffset)));
            return;
        }
        String dn = getToolDisplayName(ctx, toolName);
        Map<String, Object> resultEvt = new java.util.LinkedHashMap<>();
        resultEvt.put("type", "tool_result");
        resultEvt.put("toolName", toolName);
        if (dn != null) resultEvt.put("displayName", dn);
        resultEvt.put("result", truncated);
        resultEvt.put("contentOffset", contentOffset);
        toolEventsList.add(resultEvt);
    }

    private String getToolDisplayName(ChatContext ctx, String toolName) {
        if (ctx == null || ctx.getToolDisplayNameMap() == null) return null;
        return ctx.getToolDisplayNameMap().get(toolName);
    }

    /**
     * SSE 推送时截断工具结果：JSON 结果不截断（前端需解析），纯文本限制 2000 字符
     */
    private String truncateForSse(String result) {
        if (result == null) return "";
        // JSON 结果保持完整，避免截断导致解析失败
        if (result.startsWith("{") || result.startsWith("[")) return result;
        return result.length() > 2000 ? result.substring(0, 2000) + "..." : result;
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
            Map<String, Object> map = objectMapper.readValue(args, Map.class);
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
