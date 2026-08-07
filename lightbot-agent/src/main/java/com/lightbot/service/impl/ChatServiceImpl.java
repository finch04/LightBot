package com.lightbot.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.constant.ConfigKeys;
import com.lightbot.constant.RagResultType;
import com.lightbot.constant.ToolResultPrefixes;
import com.lightbot.dto.ChatRequestDTO;
import com.lightbot.dto.MemoryExtractDTO;
import com.lightbot.vo.RagReferenceVO;
import com.lightbot.util.ChatDocumentMessageUtil;
import com.lightbot.util.ChatMessageContextUtil;
import com.lightbot.util.InlineThinkingStreamParser;
import com.lightbot.util.RagParamResolver;
import com.lightbot.util.SensitiveWordFilter;
import com.lightbot.util.ToolEventCompactUtil;
import com.lightbot.util.ToolArgsSanitizer;
import com.lightbot.entity.Agent;
import com.lightbot.entity.ModelProvider;
import com.lightbot.entity.ToolCall;
import com.lightbot.enums.ModelProviderType;
import com.lightbot.model.MimoChatClient;
import com.lightbot.subagent.DelegateSubAgentTool;
import com.lightbot.tool.ToolEventEmitter;
import com.lightbot.tool.builtin.AskUserTool;
import com.lightbot.tool.builtin.PresentBusinessPageTool;
import com.lightbot.agent.tool.knowledge.QueryKnowledgeTool;
import com.lightbot.agent.tool.memory.UserMemoryToolCallbackFactory;
import com.lightbot.entity.Knowledge;
import com.lightbot.dto.LlmTraceSpanDTO;
import com.lightbot.enums.MessageRole;
import com.lightbot.enums.MessageType;
import com.lightbot.service.*;
import com.lightbot.service.chat.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
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
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
    private final ThreadPoolTaskExecutor taskExecutor;
    private final ToolCallService toolCallService;

    // 中间件
    private final InitMiddleware initMiddleware;
    private final MentionMiddleware mentionMiddleware;
    private final UserSensitiveMiddleware userSensitiveMiddleware;
    private final WorkflowMiddleware workflowMiddleware;
    private final SkillPrepMiddleware skillPrepMiddleware;
    private final MessageMiddleware messageMiddleware;
    private final ToolPrepMiddleware toolPrepMiddleware;
    private final TraceMiddleware traceMiddleware;
    private final MimoChatClient mimoChatClient;
    private final ModelProviderService modelProviderService;
    private final TokenBudgetService tokenBudgetService;
    private final ApiKeyService apiKeyService;
    private final ObjectMapper objectMapper;
    private final ToolEventGenerator toolEventGenerator;
    private final ToolArgsSanitizer toolArgsSanitizer;
    private final RagParamResolver ragParamResolver;
    private final SessionAttachmentRegistrar sessionAttachmentRegistrar;
    private final SubAgentService subAgentService;
    private final UserMemoryService userMemoryService;
    private final com.lightbot.service.LongMemoryPolicyService longMemoryPolicyService;
    private final ChatAbortRegistry chatAbortRegistry;
    private final com.lightbot.subagent.service.SubAgentTaskService subAgentTaskService;

    /** SSE 心跳注释行（SSE 协议：以冒号开头的行是注释，客户端应忽略） */
    private static final String HEARTBEAT_PREFIX = ":heartbeat";

    /** 工具执行超时时间（秒），与 {@link com.lightbot.constant.ChatConstants#TOOL_EXECUTION_TIMEOUT_SECONDS} 一致 */
    private static final long TOOL_EXECUTION_TIMEOUT_SECONDS = com.lightbot.constant.ChatConstants.TOOL_EXECUTION_TIMEOUT_SECONDS;

    /** 工具调用上下文裁剪阈值（字符数），超出时压缩早期工具调用轮次，约 15K tokens */
    private static final int MAX_TOOL_CONTEXT_CHARS = 60000;
    /** 裁剪时保留最近 N 轮工具调用，确保 LLM 有足够上下文 */
    private static final int TOOL_ROUNDS_TO_KEEP = 2;

    @Autowired
    @Qualifier("lightBotExecutor")
    private Executor lightBotExecutor;

    @Override
    public String chat(ChatRequestDTO request) {
        // 1. 初始化上下文
        ChatContext ctx = ChatContext.of(request);
        ctx.setRequestId(String.valueOf(System.nanoTime()));
        initMiddleware.init(ctx);
        mentionMiddleware.prepare(ctx);
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
        processChatWithToolCalls(ctx);
        ctx.finalizeInlineThinking();
        String reply = ctx.getFullReply().toString();

        log.info("[Chat] AI回复: sessionId={}, length={}", ctx.getSessionId(), reply != null ? reply.length() : 0);

        // 3. 构建metadata并持久化AI回复（toolEvents 单独写入 tool_events 列）
        String metadataStr = buildChatMetadata(ctx);
        String toolEventsStr = serializeToolEvents(
                ToolEventCompactUtil.compactForPersistence(ctx.getToolEventsList(), reply));
        int totalTokens = ctx.getInputTokenHolder()[0] + ctx.getOutputTokenHolder()[0];
        Long messageId = messageMiddleware.saveMessage(ctx.getSessionId(), MessageRole.ASSISTANT,
                reply, metadataStr, toolEventsStr, totalTokens, MessageType.TEXT, null, null);
        ctx.setAssistantMessageId(messageId);

        // 3.0 Token：API Key 走企业配额；平台调试走个人预算
        Long apiKeyId = ctx.getRequest() != null ? ctx.getRequest().getApiKeyId() : null;
        if (apiKeyId != null) {
            apiKeyService.checkAndConsumeQuota(apiKeyId, totalTokens);
        } else if (ctx.getUserId() != null) {
            tokenBudgetService.recordUsage(ctx.getUserId(), ctx.getInputTokenHolder()[0], ctx.getOutputTokenHolder()[0]);
        }

        // 3.1 写入工具调用记录（逐条 insert，避免 Reactor 线程 saveBatch SPI 失败）
        persistPendingToolCalls(ctx, messageId);

        // 4. 异步生成标题
        taskExecutor.execute(() -> traceMiddleware.generateTitle(ctx.getSessionId(), ctx.getAgent(), ctx.getConfigMap()));

        // 5. 长期记忆抽取（平台调试 / 带 externalUserId 的开放 API）
        if (shouldExtractUserMemory(ctx)) {
            try {
                userMemoryService.extractAsync(buildMemoryExtractRequest(ctx));
            } catch (Exception e) {
                log.warn("[Chat] 调度长期记忆抽取失败: {}", e.getMessage());
            }
        }

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
                    ctx.appendTraceMetadataReasoning(reasoningObj.toString());
                    ctx.appendReasoningContent(reasoningObj.toString());
                }
            }

            // 无工具调用 → 直接返回结果
            if (assistantMsg == null || !assistantMsg.hasToolCalls()) {
                String text = (assistantMsg != null) ? assistantMsg.getText() : "";
                if (text != null && !text.isEmpty()) {
                    ctx.appendTraceCompleteReply(text);
                }
                // 如果 metadata 没有 reasoningContent，尝试解析 inline thinking 标签
                if (ctx.getReasoningContent().length() == 0 && text != null && !text.isEmpty()) {
                    InlineThinkingStreamParser.ParseResult parsed = InlineThinkingStreamParser.parseComplete(text);
                    if (!parsed.reasoningDelta().isEmpty()) {
                        ctx.appendReasoningContent(parsed.reasoningDelta());
                    }
                    text = parsed.contentDelta();
                }
                String filtered = SensitiveWordFilter.filterAiOutput(
                        text != null ? text : "", ctx.getConfigMap(), agent.getId(), ctx.getSessionId()).text();
                fullReply.append(filtered);
                return fullReply.toString();
            }

            // 有工具调用 → 执行工具并继续循环
            ctx.getMessages().add(assistantMsg);
            List<AssistantMessage.ToolCall> toolCalls = assistantMsg.getToolCalls();

            List<org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();
            appendAssistantLeadingTextBeforeToolCall(ctx, agent, assistantMsg != null ? assistantMsg.getText() : null);
            int toolContentOffset = resolveToolBlockOffset(ctx);

            // 目前非流式只处理第一个工具调用（简化处理）
            AssistantMessage.ToolCall firstTool = toolCalls.get(0);
            String toolName = firstTool.name();
            String toolArgs = firstTool.arguments();
            ctx.getToolCallCountHolder()[0]++;

            String safeArgs = toolArgs != null ? toolArgs : "";

            // 记录工具调用开始（SubAgent 委派走专用 subagent_call 事件）
            long toolCallId = appendToolCallStart(ctx, toolEventsList, null, toolName, safeArgs, toolContentOffset);

            // 执行工具
            String toolResult = executeToolCallback(toolCallbackMap, toolName, safeArgs, agent.getId(), ctx.getSessionId(), requestId, null, ctx);

            // 暂存工具调用记录（复用 toolCallId 作为主键，前端按 id 拉取完整结果）
            ToolCall toolCallLog = new ToolCall();
            toolCallLog.setId(toolCallId);
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

            // 记录工具结果（SubAgent 委派走 subagent_result）
            appendToolCallResult(ctx, toolEventsList, null, toolName, safeArgs, toolResult, toolContentOffset, toolCallId);

            toolResponses.add(new org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse(
                    firstTool.id(), toolName, toolResult));

            ctx.getMessages().add(org.springframework.ai.chat.messages.ToolResponseMessage.builder()
                    .responses(toolResponses)
                    .build());

            // ask_user / present_business_page 等 wait_for_user 工具：中断循环，避免模型再灌一大段说明
            if (shouldBreakToolLoop(toolResponses)) {
                log.info("[Chat][Trace] wait_for_user 工具调用，中断工具循环，等待用户操作");
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
            List<RagReferenceVO> refs = kbResults.stream().map(this::mapToRagReference).toList();
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

            // 2. 添加工具事件 offset（toolEvents 本体已拆到 message.tool_events 独立列）
            List<Map<String, Object>> toolEventsList = ctx.getToolEventsList();
            if (!toolEventsList.isEmpty()) {
                List<Map<String, Object>> compactEvents = ToolEventCompactUtil.compactForPersistence(toolEventsList);
                List<Integer> offsets = ToolEventCompactUtil.extractToolBlockOffsets(compactEvents);
                if (!offsets.isEmpty()) {
                    meta.put("toolBlockOffsets", offsets);
                }
            }

            // 3. 添加reasoningContent
            if (ctx.getReasoningContent().length() > 0) {
                meta.put("reasoningContent", com.lightbot.util.TextNormalizeUtil.sanitizeForDatabase(
                        ctx.getReasoningContent().toString()));
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
    public Flux<String> chatStream(ChatRequestDTO request) {
        ChatContext ctx = ChatContext.of(request);
        ctx.setRequestId(String.valueOf(System.nanoTime()));
        chatAbortRegistry.register(ctx.getRequestId(), ctx);

        // Init → Mention → 用户敏感词 → Workflow → SkillPrep → Message → ToolPrep → Trace → [core]
        List<ChatMiddleware> middlewares = List.of(
                initMiddleware, mentionMiddleware, userSensitiveMiddleware, workflowMiddleware,
                skillPrepMiddleware, messageMiddleware, toolPrepMiddleware, traceMiddleware);
        ChatServiceCore core = this::streamCore;

        // requestId 可立即回传；sessionId 在 InitMiddleware 解析会话后发出（见 SESSION_ID 事件）
        return Flux.just(REQUEST_ID_PREFIX + ctx.getRequestId())
                .concatWith(ChatMiddlewareChain.of(middlewares, core).proceed(ctx))
                .concatWith(Mono.fromCallable(() -> buildDoneEvent(ctx)))
                .doOnCancel(() -> ctx.requestAbort("CLIENT_DISCONNECT"))
                .doFinally(signal -> {
                    chatAbortRegistry.remove(ctx.getRequestId());
                    if (signal == reactor.core.publisher.SignalType.CANCEL) {
                        ctx.requestAbort("CLIENT_DISCONNECT");
                        log.info("[Chat] stream cancelled: requestId={}, sessionId={}",
                                ctx.getRequestId(), ctx.getSessionId());
                    }
                });
    }

    @Override
    public void stopStream(String requestId, Long userId) {
        // 1. 中断主对话：置 aborted，in-flight LLM 轮次下一拍即停
        boolean aborted = chatAbortRegistry.abort(requestId, userId);
        if (!aborted) {
            log.info("[Chat] 停止对话未命中活跃流: requestId=[{}], userId=[{}]", requestId, userId);
        }
        // 2. 连带取消该请求下运行中的 SubAgent 子任务（taskContext.aborted 仅为快照，需显式置取消）
        int cancelled = subAgentTaskService.cancelByParentRequestId(requestId);
        if (cancelled > 0) {
            log.info("[Chat] 停止对话连带取消子任务: requestId=[{}], affected=[{}]", requestId, cancelled);
        }
    }

    /**
     * 构建 [DONE] 事件：先持久化 AI 回复，再返回带消息ID的完成标记
     * <p>此方法在 Mono.fromCallable 中执行（Flux 最后一个元素），此时流式内容已全部累加。
     * Trace 记录等后置操作由 TraceMiddleware.doOnComplete 处理；标题生成在本方法助手消息落库后触发。</p>
     */
    private String buildDoneEvent(ChatContext ctx) {
        long totalTokens = ctx.getInputTokenHolder()[0] + ctx.getOutputTokenHolder()[0];
        if (ctx.isStreamFailed()) {
            return toolEventGenerator.doneWithMetadata(ctx.getSessionId(), ctx.getUserMessageId(), null, totalTokens,
                    buildStreamFailureMetadata(ctx));
        }
        // 用户输入敏感词拦截：UserSensitiveMiddleware 已落库 USER + ASSISTANT 两条消息，
        // 直接返回带 IDs 的 [DONE]，跳过助手消息重复保存与标题/记忆抽取等后置流程
        if (ctx.isSensitiveUserBlocked()) {
            return toolEventGenerator.doneWithMetadata(
                    ctx.getSessionId(), ctx.getUserMessageId(), ctx.getAssistantMessageId(), totalTokens, null);
        }

        try {
            Long agentId = ctx.getAgent() != null ? ctx.getAgent().getId() : null;

            // 0. Token：API Key 走企业配额；平台调试走个人预算
            Long apiKeyId = ctx.getRequest() != null ? ctx.getRequest().getApiKeyId() : null;
            if (apiKeyId != null) {
                apiKeyService.checkAndConsumeQuota(apiKeyId, totalTokens);
            } else if (ctx.getUserId() != null) {
                tokenBudgetService.recordUsage(ctx.getUserId(), ctx.getInputTokenHolder()[0], ctx.getOutputTokenHolder()[0]);
            }

            // 1. 持久化 AI 回复
            // 注意：流式链路中 fullReply 已在过程中通过 SensitiveWordFilter 过滤（processChunk/filterAiOutput）
            // 此处直接使用，避免重复过滤导致内容不一致（替换策略下多次替换会改变内容）
            ctx.finalizeInlineThinking();
            String fullReplyText = ctx.getFullReply().toString();
            // 仅做数据库安全清理（非法字符），不做敏感词二次过滤
            String replyToSave = com.lightbot.util.TextNormalizeUtil.sanitizeForAiMessage(fullReplyText, 0);
            String metadataStr = buildPersistMetadata(ctx, replyToSave);
            // toolEvents 单独序列化到 message.tool_events 列（与 metadata 解耦）
            String toolEventsStr = serializeToolEvents(buildPersistToolEvents(ctx, replyToSave));
            Long assistantMessageId = messageMiddleware.saveMessage(
                    ctx.getSessionId(), MessageRole.ASSISTANT,
                    replyToSave, metadataStr, toolEventsStr,
                    (int) totalTokens, MessageType.TEXT, null, null);
            ctx.setAssistantMessageId(assistantMessageId);

            // 1.1 写入工具调用记录（与消息落库解耦：失败只打日志，不破坏 DONE）
            persistPendingToolCalls(ctx, assistantMessageId);
            ctx.getFullReply().setLength(0);
            ctx.getFullReply().append(replyToSave);

            // 1.2 助手消息已落库，异步生成会话标题（须晚于 TraceMiddleware.doOnComplete）
            scheduleTitleGeneration(ctx);

            // 1.3 助手消息已落库后再异步抽取长期记忆
            // 平台调试：按用户偏好；开放 API：仅当有 externalUserId；自动化：不抽取
            if (shouldExtractUserMemory(ctx)) {
                try {
                    userMemoryService.extractAsync(buildMemoryExtractRequest(ctx));
                } catch (Exception e) {
                    log.warn("[Chat] 调度长期记忆抽取失败: {}", e.getMessage());
                }
            }

            // 2. 返回带消息ID、Token数和完整metadata的 [DONE] 事件
            return toolEventGenerator.doneWithMetadata(
                    ctx.getSessionId(), ctx.getUserMessageId(), assistantMessageId, totalTokens, metadataStr);
        } catch (Exception e) {
            log.error("[Chat] 构建[DONE]事件异常: {}", e.getMessage(), e);
            return DONE_PREFIX;
        }
    }

    /**
     * 落库本轮暂存的 tool_calls。失败不影响助手消息与 DONE（历史曾因 saveBatch SPI 整段失败）。
     *
     * @param ctx       对话上下文
     * @param messageId 助手消息 ID
     */
    private void persistPendingToolCalls(ChatContext ctx, Long messageId) {
        if (ctx == null || messageId == null || ctx.getPendingToolCalls() == null
                || ctx.getPendingToolCalls().isEmpty()) {
            return;
        }
        try {
            for (ToolCall tc : ctx.getPendingToolCalls()) {
                tc.setMessageId(messageId);
            }
            toolCallService.persistPendingCalls(ctx.getPendingToolCalls());
            ctx.getPendingToolCalls().clear();
        } catch (Exception e) {
            log.error("[Chat] 写入 tool_calls 失败: messageId={}, count={}, err={}",
                    messageId, ctx.getPendingToolCalls().size(), e.getMessage(), e);
        }
    }

    /**
     * 构建长期记忆抽取入参：从对话上下文提取纯数据字段，并在编排层判定本轮是否已主动保存记忆。
     *
     * @param ctx 对话上下文
     * @return 记忆抽取入参
     */
    private MemoryExtractDTO buildMemoryExtractRequest(ChatContext ctx) {
        MemoryExtractDTO request = new MemoryExtractDTO();
        request.setUserId(ctx.getUserId());
        if (ctx.getRequest() != null) {
            request.setApiKeyId(ctx.getRequest().getApiKeyId());
            request.setExternalUserId(ctx.getRequest().getExternalUserId());
        }
        request.setSessionId(ctx.getSessionId());
        request.setAgentId(ctx.getAgent() != null ? ctx.getAgent().getId() : null);
        request.setSourceMessageId(ctx.getUserMessageId());
        request.setUserMessage(ctx.getRequest() != null ? ctx.getRequest().getMessage() : null);
        request.setAssistantReply(ctx.getFullReply() != null ? ctx.getFullReply().toString() : "");
        request.setMemorySaved(hasMemorySaveToolCall(ctx));
        return request;
    }

    /**
     * 是否调度长期记忆抽取：平台调试按登录用户；开放 API 需 externalUserId；自动化不抽取
     */
    private boolean shouldExtractUserMemory(ChatContext ctx) {
        if (ctx == null || ctx.getRequest() == null) {
            return false;
        }
        if (ctx.getRequest().getActorUserId() != null && ctx.getRequest().getApiKeyId() == null) {
            return false;
        }
        Long apiKeyId = ctx.getRequest().getApiKeyId();
        try {
            if (!Boolean.TRUE.equals(longMemoryPolicyService.resolveEffective(apiKeyId).getEnabled())) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
        // 开放 API：需 externalUserId；控制台：登录用户启用 debug_user_{userId} 调试记忆
        if (apiKeyId != null) {
            String externalUserId = ctx.getRequest().getExternalUserId();
            return externalUserId != null && !externalUserId.isBlank();
        }
        return ctx.getUserId() != null;
    }

    /**
     * 判定本轮对话是否已通过 memory_save 工具主动保存记忆。
     *
     * @param ctx 对话上下文
     * @return 已保存返回 true
     */
    private boolean hasMemorySaveToolCall(ChatContext ctx) {
        if (ctx.getToolEventsList() == null || ctx.getToolEventsList().isEmpty()) {
            return false;
        }
        return ctx.getToolEventsList().stream()
                .anyMatch(event -> UserMemoryToolCallbackFactory.SAVE_TOOL_NAME.equals(String.valueOf(event.get("toolName"))));
    }

    private String buildStreamFailureMetadata(ChatContext ctx) {
        try {
            Map<String, Object> meta = new java.util.LinkedHashMap<>();
            meta.put("error", Map.of(
                    "message", ctx.getStreamErrorMessage() != null ? ctx.getStreamErrorMessage() : "未知错误",
                    "code", ctx.getStreamErrorCode() != null ? ctx.getStreamErrorCode() : "UNKNOWN"));
            if (ctx.getRequestId() != null && !ctx.getRequestId().isBlank()) {
                meta.put("requestId", ctx.getRequestId());
            }
            return objectMapper.writeValueAsString(meta);
        } catch (Exception e) {
            return null;
        }
    }

    private void markStreamFailure(ChatContext ctx, Throwable e) {
        ctx.setStreamFailed(true);
        ctx.setStreamErrorMessage(classifyErrorMessage(e));
        ctx.setStreamErrorCode(classifyErrorCode(e));
    }

    /**
     * 异步生成会话标题：在 user + assistant 均已持久化后调度
     */
    private void scheduleTitleGeneration(ChatContext ctx) {
        if (ctx.getSessionId() == null) {
            return;
        }
        taskExecutor.execute(() -> traceMiddleware.generateTitle(
                ctx.getSessionId(), ctx.getAgent(), ctx.getConfigMap()));
    }

    /**
     * 构建持久化 metadata：合并 ragMetadata + reasoningContent + sensitiveBlock + requestId
     * <p>toolEvents 已拆到 message.tool_events 独立列，不再写入 metadata（避免 metadata 暴增），
     * 由 {@link #buildPersistToolEvents} 单独产出</p>
     *
     * @param ctx          对话上下文
     * @param finalContent 最终落库正文（用于对齐 toolEvents contentOffset）
     */
    private String buildPersistMetadata(ChatContext ctx, String finalContent) {
        try {
            Map<String, Object> meta = new java.util.LinkedHashMap<>();
            String ragMeta = ctx.getRagMetadataHolder()[0];
            if (ragMeta != null && !ragMeta.isBlank()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> existing = objectMapper.readValue(ragMeta, Map.class);
                meta.putAll(existing);
            }
            // toolEvents 拆到独立列存储（见 buildPersistToolEvents），metadata 仅保留 toolBlockOffsets
            // 用于前端按 contentOffset 切分正文与工具块；offsets 来自同一份 compactEvents。
            List<Map<String, Object>> compactEvents = buildPersistToolEvents(ctx, finalContent);
            if (!compactEvents.isEmpty()) {
                List<Integer> offsets = ToolEventCompactUtil.extractToolBlockOffsets(compactEvents);
                if (!offsets.isEmpty()) {
                    meta.put("toolBlockOffsets", offsets);
                }
            }
            // 敏感拦截时不再暴露 reasoningContent（拦截前累积的思考也不应透出）；
            // 仅保留 sensitiveBlock 标记与必要 ID 字段
            if (!ctx.isSensitiveAiBlocked() && ctx.getReasoningContent().length() > 0) {
                meta.put("reasoningContent", com.lightbot.util.TextNormalizeUtil.sanitizeForDatabase(
                        ctx.getReasoningContent().toString()));
            }
            if (ctx.isSensitiveAiBlocked()) {
                meta.put("sensitiveBlock", "ai_output");
            }
            if (ctx.getRequestId() != null && !ctx.getRequestId().isBlank()) {
                meta.put("requestId", ctx.getRequestId());
            }
            // 用户主动中止：落库标记，供历史加载渲染「输出已终止」样式
            if (ctx.isAborted()) {
                meta.put("aborted", true);
                if (ctx.getAbortReason() != null && !ctx.getAbortReason().isBlank()) {
                    meta.put("abortReason", ctx.getAbortReason());
                }
            }
            // 未完成待办告警：本轮结束时仍有 pending/in_progress 项时，前端在消息末尾渲染醒目提示
            // 用于 AI 违反 prompt 硬约束（必须完成所有 todos 才能结束）时的兜底告警
            List<Map<String, String>> incompleteTodos = collectIncompleteTodos(ctx);
            if (!incompleteTodos.isEmpty()) {
                meta.put("incompleteTodos", incompleteTodos);
            }
            return meta.isEmpty() ? null : objectMapper.writeValueAsString(meta);
        } catch (Exception e) {
            log.warn("[Chat] 构建持久化metadata失败: {}", e.getMessage());
            return ctx.getRagMetadataHolder()[0];
        }
    }

    /**
     * 构建持久化 toolEvents JSON：压缩 + 按 finalContent 对齐 contentOffset，
     * 写入 message.tool_events 独立列（与 metadata 解耦）。
     *
     * @param ctx          对话上下文
     * @param finalContent 最终落库正文（用于对齐 contentOffset）
     * @return 压缩后的事件列表；空时返回空列表
     */
    private List<Map<String, Object>> buildPersistToolEvents(ChatContext ctx, String finalContent) {
        List<Map<String, Object>> toolEventsList = ctx.getToolEventsList();
        if (toolEventsList == null || toolEventsList.isEmpty()) {
            return List.of();
        }
        return ToolEventCompactUtil.compactForPersistence(toolEventsList, finalContent);
    }

    /**
     * 序列化 toolEvents 列表为 JSON 字符串，用于 message.tool_events 落库。
     *
     * @param compactEvents 已压缩对齐的事件列表
     * @return JSON 字符串；空列表时返回 null
     */
    private String serializeToolEvents(List<Map<String, Object>> compactEvents) {
        if (compactEvents == null || compactEvents.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(compactEvents);
        } catch (Exception e) {
            log.warn("[Chat] 序列化 toolEvents 失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 收集本轮结束时仍未完成的 todos（pending / in_progress），供前端渲染「未完成」告警。
     * <p>读取 {@link ChatContext#getCurrentTodosSnapshot()} —— 该快照由 ToolPrepMiddleware 初始化、
     * 每次 write_todos 成功后由 executeToolCallback 回写，反映本轮最新状态</p>
     *
     * @param ctx 对话上下文
     * @return 未完成 todos 列表（每项含 id/content/status）；空列表表示全部完成
     */
    private List<Map<String, String>> collectIncompleteTodos(ChatContext ctx) {
        List<Map<String, String>> snapshot = ctx.getCurrentTodosSnapshot();
        if (snapshot == null || snapshot.isEmpty()) {
            return List.of();
        }
        List<Map<String, String>> incomplete = new java.util.ArrayList<>();
        for (Map<String, String> todo : snapshot) {
            String status = todo.get("status");
            if ("pending".equalsIgnoreCase(status) || "in_progress".equalsIgnoreCase(status)) {
                incomplete.add(todo);
            }
        }
        return incomplete;
    }

    /** SSE 心跳间隔（秒） */
    private static final int HEARTBEAT_INTERVAL_SECONDS = 15;

    /**
     * 流式核心：递归工具调用循环
     * <p>创建 Sinks.Many 用于工具执行期间的实时状态推送。
     * 工具内部通过 {@code ToolEventEmitter.emit()} 写入 Sink，
     * 此处订阅 Sink 将 tool_status 事件实时发送给前端。</p>
     * <p>合并心跳 Flux 防止代理/网关断连；doOnError 发送结构化错误事件。</p>
     */
    private Flux<String> streamCore(ChatContext ctx) {
        ctx.setStartTime(System.currentTimeMillis());
        Long agentId = ctx.getAgent() != null ? ctx.getAgent().getId() : null;
        ctx.setSensitiveStreamState(new SensitiveWordFilter.StreamState(
                ctx.getConfigMap(), agentId, ctx.getSessionId()));

        Sinks.Many<String> eventSink = Sinks.many().multicast().onBackpressureBuffer();
        ctx.setRealtimeStatusEmitter(json -> eventSink.tryEmitNext(STATUS_PREFIX + json));
        Flux<String> toolStatusFlux = eventSink.asFlux()
                .map(msg -> msg != null && msg.startsWith(STATUS_PREFIX)
                        ? msg
                        : STATUS_PREFIX + toolEventGenerator.toolStatusEvent(msg, 0));

        // 1.1 心跳保活：每 15 秒发送 SSE 注释行，防止代理/网关断连
        // 心跳随主内容流首条数据触发后持续发送，主内容流完成时自动停止（takeUntil）。
        // 不能直接 mergeWith 无限心跳流，否则 mergeWith 永不 complete，[DONE] 永远发不出去。
        Flux<String> heartbeatFlux = Flux.interval(Duration.ofSeconds(HEARTBEAT_INTERVAL_SECONDS))
                .map(tick -> HEARTBEAT_PREFIX);

        Flux<String> modelFlux = processToolCallsRecursively(ctx, 0, System.currentTimeMillis(), eventSink)
                .onErrorResume(e -> {
                    log.error("[Chat] 流式处理异常: {}", e.getMessage(), e);
                    markStreamFailure(ctx, e);
                    return Flux.just(STATUS_PREFIX
                            + toolEventGenerator.errorEvent(ctx.getStreamErrorMessage(), ctx.getStreamErrorCode()));
                })
                .doFinally(signal -> eventSink.tryEmitComplete());

        // 主内容流会被订阅两次：一次作为输出，一次被下方 takeUntilOther 当作完成信号。
        // modelFlux 是冷流，若不做热化，第二次订阅会整条流水线重跑一遍——工具真实执行两次，
        // 且其 tool_result 等事件经共享的 eventSink 泄漏进输出，导致结果回显两次、OCR 等慢工具耗时翻倍。
        // publish().autoConnect(2) 保证两个订阅者共享同一次上游执行，且等两者都订阅后才启动（不丢事件）。
        Flux<String> coreContent = toolStatusFlux.mergeWith(modelFlux)
                .publish()
                .autoConnect(2);

        // 心跳在主内容流完成时停止；mergeWith 取两者都完成的时间点
        return coreContent.mergeWith(heartbeatFlux.takeUntilOther(coreContent));
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
        if (ctx.isAborted()) {
            return Flux.empty();
        }
        // 上一轮或本轮已触发敏感拦截：跳过后续 LLM 调用，让 buildDoneEvent 立即收尾
        if (ctx.isSensitiveAiBlocked()) {
            return Flux.empty();
        }
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
        List<LlmTraceSpanDTO> spans = ctx.getSpans();
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

        return streamModelWithRetry(ctx, chatModel, prompt, depth, eventSink)
                .concatMap(response -> {
                    // 已触发 AI 输出敏感拦截：跳过后续 chunk（含 metadata reasoning、正文增量），
                    // 由首条 sensitive_block + buildDoneEvent 最小 metadata 收尾
                    if (ctx.isSensitiveAiBlocked()) {
                        return Flux.empty();
                    }
                    Generation gen = response.getResult();
                    AssistantMessage assistantMsg = (gen != null) ? gen.getOutput() : null;

                    // 2. 无工具调用 → 直接输出文本（结束递归）
                    if (assistantMsg == null || !assistantMsg.hasToolCalls()) {
                        // 先累加 Token（usage 常在最后一个空文本 chunk，不能因 stripped 为空而跳过）
                        accumulateStreamUsage(response, inputTokenHolder, outputTokenHolder);

                        String text = (assistantMsg != null) ? assistantMsg.getText() : "";
                        if (text == null) text = "";

                        List<String> streamItems = new ArrayList<>(2);

                        // metadata reasoning（MiMo 等）；与正文 inline thinking 可并存
                        if (gen != null && gen.getOutput() != null) {
                            var metadata = gen.getOutput().getMetadata();
                            if (metadata != null) {
                                Object reasoningObj = metadata.get("reasoningContent");
                                if (reasoningObj != null && !reasoningObj.toString().isBlank()) {
                                    ctx.appendTraceMetadataReasoning(reasoningObj.toString());
                                    String reasoning = ctx.appendReasoningContent(reasoningObj.toString());
                                    if (!reasoning.isEmpty()) {
                                        streamItems.add(STATUS_PREFIX + toolEventGenerator.reasoningEvent(reasoning));
                                    }
                                }
                            }
                        }

                        if (!text.isEmpty()) {
                            InlineThinkingStreamParser.ParseResult parsed = feedStreamTextChunk(ctx, text);
                            Flux<String> contentFlux = fluxFromInlineThinking(ctx, agent, parsed, () -> {
                                if (!llmSpanAdded[0]) {
                                    spans.add(LlmTraceSpanDTO.of(llmSpanId, "s1", "llm_call", llmCallStart,
                                            System.currentTimeMillis() - llmCallStart, "OK",
                                            Map.of("depth", depth, "model", configMap.getOrDefault("modelId", ""),
                                                    "inputTokens", inputTokenHolder[0], "outputTokens", outputTokenHolder[0],
                                                    "replyPreview", fullReply.length() > 500 ? fullReply.substring(0, 500) + "..." : fullReply.toString())));
                                    llmSpanAdded[0] = true;
                                }
                            });
                            return streamItems.isEmpty()
                                    ? contentFlux
                                    : Flux.fromIterable(streamItems).concatWith(contentFlux);
                        }

                        return streamItems.isEmpty() ? Flux.empty() : Flux.fromIterable(streamItems);
                    }

                    // 3. 有工具调用 → 执行工具
                    messages.add(assistantMsg);

                    accumulateStreamUsage(response, inputTokenHolder, outputTokenHolder);

                    // 3.0 先消费本 chunk 携带的正文（部分模型将正文与工具调用放在同一 chunk）。
                    //     必须在计算 toolContentOffset 之前完成，使 offset 精确反映"组件前已产出的正文长度"，
                    //     否则前端会按滞后的 offset 把正文从中间截断（如「好<组件>的」）。
                    Flux<String> leadingContentFlux = Flux.empty();
                    String assistantLeadingText = assistantMsg.getText();
                    boolean leadingContentAppended = false;
                    if (assistantLeadingText != null && !assistantLeadingText.isEmpty()) {
                        InlineThinkingStreamParser.ParseResult leadingParsed = feedStreamTextChunk(ctx, assistantLeadingText);
                        leadingContentAppended = appendInlineThinkingContentDelta(ctx, agent, leadingParsed);
                        leadingContentFlux = fluxFromInlineThinking(ctx, agent, leadingParsed, null, false);
                    }

                    List<AssistantMessage.ToolCall> toolCalls = assistantMsg.getToolCalls();
                    boolean asyncEnabled = Boolean.TRUE.equals(configMap.get("asyncToolCalls"));

                    if (!llmSpanAdded[0]) {
                        spans.add(LlmTraceSpanDTO.of(llmSpanId, "s1", "llm_call", llmCallStart,
                                System.currentTimeMillis() - llmCallStart, "OK",
                                Map.of("depth", depth, "model", configMap.getOrDefault("modelId", ""),
                                        "toolCount", toolCalls.size(),
                                        "toolNames", toolCalls.stream().map(AssistantMessage.ToolCall::name).toList().toString())));
                        llmSpanAdded[0] = true;
                    }

                    List<Flux<String>> statusFluxes = new ArrayList<>();
                    List<Map<String, Object>> kbResultsHolder = new ArrayList<>();
            List<org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();
            if (!leadingContentAppended) {
                appendAssistantLeadingTextBeforeToolCall(ctx, agent, assistantMsg.getText());
            }
            int toolContentOffset = resolveToolBlockOffset(ctx);

                    if (asyncEnabled && toolCalls.size() > 1) {
                        // 并行执行所有工具
                        log.info("[Chat][Trace] 工具调用(depth={}): {}个工具, 并行执行", depth, toolCalls.size());
                        List<CompletableFuture<String>> futures = new ArrayList<>();
                        for (AssistantMessage.ToolCall tc : toolCalls) {
                            String tcArgs = tc.arguments() != null ? tc.arguments() : "";
                            long tcToolCallId = appendToolCallStart(ctx, toolEventsList, statusFluxes, tc.name(), tcArgs, toolContentOffset);
                            toolCallCountHolder[0]++;
                            final String tcName = tc.name();
                            final String safeTcArgs = toolArgsSanitizer.forChatCall(tcArgs);
                            final Sinks.Many<String> sink = eventSink;
                            final long tcIdFinal = tcToolCallId;
                            futures.add(CompletableFuture.supplyAsync(() -> {
                                long tStart = System.currentTimeMillis();
                                // 绑定 Sink 到当前 worker 线程，使 emit() 实时推送
                                if (sink != null) {
                                    ToolEventEmitter.setupSink(sink);
                                }
                                String result;
                                try {
                                    result = executeToolCallback(toolCallbackMap, tcName, safeTcArgs,
                                            agent.getId(), ctx.getSessionId(), requestId, sink, ctx);
                                } finally {
                                    if (sink != null) {
                                        ToolEventEmitter.teardownSink();
                                    }
                                }
                                long tEnd = System.currentTimeMillis();
                                log.info("[Chat][Trace] 工具执行结果: name={}, 耗时={}ms, resultLength={}", tcName, tEnd - tStart, result.length());
                                spans.add(LlmTraceSpanDTO.of("tool_" + toolCallCountHolder[0], llmSpanId, "tool_execute",
                                        tStart, tEnd - tStart, "OK",
                                        buildToolTraceAttributes(tcName, tcArgs, result)));
                                appendSubAgentTraceSpans(spans, "tool_" + toolCallCountHolder[0], tcName, result, tStart);
                                if ("query_knowledge".equals(tcName)) {
                                    List<Map<String, Object>> kbResults = QueryKnowledgeTool.getSearchResults(requestId);
                                    synchronized (kbResultsHolder) { kbResultsHolder.addAll(kbResults); }
                                }
                                // 暂存工具调用记录（复用 tcIdFinal 作为主键，前端按 id 拉取完整结果）
                                ToolCall toolCallLog = new ToolCall();
                                toolCallLog.setId(tcIdFinal);
                                toolCallLog.setToolName(tcName);
                                toolCallLog.setToolInput(safeTcArgs);
                                toolCallLog.setToolOutput(result);
                                toolCallLog.setStatus(result.startsWith(ToolResultPrefixes.FAILURE) || result.startsWith(ToolResultPrefixes.NOT_FOUND) ? "error" : "success");
                                toolCallLog.setErrorMessage(result.startsWith(ToolResultPrefixes.FAILURE) ? result : null);
                                synchronized (ctx.getPendingToolCalls()) {
                                    ctx.getPendingToolCalls().add(toolCallLog);
                                }

                                appendToolCallResult(ctx, toolEventsList, statusFluxes, tcName, tcArgs, result, toolContentOffset, tcIdFinal);
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
                        long toolCallId = appendToolCallStart(ctx, toolEventsList, statusFluxes, toolName, safeArgs, toolContentOffset);

                        long tToolStart = System.currentTimeMillis();
                        // 流式模式：绑定 Sink 使工具内部 emit() 实时推送给前端
                        ToolEventEmitter.setupSink(eventSink);
                        String toolResult;
                        try {
                            toolResult = executeToolCallback(toolCallbackMap, toolName, callArgs,
                                    agent.getId(), ctx.getSessionId(), requestId, eventSink, ctx);
                        } finally {
                            ToolEventEmitter.teardownSink();
                        }
                        long tToolEnd = System.currentTimeMillis();
                        log.info("[Chat][Trace] 工具执行结果: name={}, 耗时={}ms, resultLength={}", toolName, tToolEnd - tToolStart, toolResult.length());

                        spans.add(LlmTraceSpanDTO.of("tool_" + toolCallCountHolder[0], llmSpanId, "tool_execute",
                                tToolStart, tToolEnd - tToolStart, "OK",
                                buildToolTraceAttributes(toolName, safeArgs, toolResult)));
                        appendSubAgentTraceSpans(spans, "tool_" + toolCallCountHolder[0], toolName, toolResult, tToolStart);

                        if ("query_knowledge".equals(toolName)) {
                            List<Map<String, Object>> kbResults = QueryKnowledgeTool.getSearchResults(requestId);
                            if (!kbResults.isEmpty()) kbResultsHolder.addAll(kbResults);
                        }

                        // 暂存工具调用记录（复用 toolCallId 作为主键，前端按 id 拉取完整结果）
                        ToolCall toolCallLog = new ToolCall();
                        toolCallLog.setId(toolCallId);
                        toolCallLog.setToolName(toolName);
                        toolCallLog.setToolInput(safeArgs);
                        toolCallLog.setToolOutput(toolResult);
                        toolCallLog.setStatus(ToolResultPrefixes.isError(toolResult) ? "error" : "success");
                        toolCallLog.setErrorMessage(ToolResultPrefixes.isError(toolResult) ? toolResult : null);
                        ctx.getPendingToolCalls().add(toolCallLog);

                        appendToolCallResult(ctx, toolEventsList, statusFluxes, toolName, safeArgs, toolResult, toolContentOffset, toolCallId);
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
                                // toolEvents 拆到 message.tool_events 独立列；中间 metadata 仅承载 toolBlockOffsets
                                List<Map<String, Object>> compactEvents = ToolEventCompactUtil.compactForPersistence(toolEventsList);
                                List<Integer> offsets = ToolEventCompactUtil.extractToolBlockOffsets(compactEvents);
                                if (!offsets.isEmpty()) metadataMap.put("toolBlockOffsets", offsets);
                            }
                            if (!kbResultsRef.isEmpty()) {
                                List<RagReferenceVO> refs = kbResultsRef.stream().map(this::mapToRagReference).toList();
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

                    // tool_result 已由 appendToolCallResult 写入 statusFluxes，此处不再重复推送，
                    // 否则前端会收到两次相同的 tool_result 事件（工具卡片渲染两份）。
                    long nextLlmStart = System.currentTimeMillis();
                    final int resultContentOffset = toolContentOffset;
                    Flux<String> toolEventFlux = Flux.concat(statusFluxes)
                            .concatWith(Flux.just(STATUS_PREFIX + toolEventGenerator.toolCompleteEvent(resultContentOffset)))
                            .concatWith(afterTool);
                    // 正文先于组件事件下发，确保组件插在完整正文之后，不腰斩已产出内容
                    toolEventFlux = leadingContentFlux.concatWith(toolEventFlux);

                    // ask_user / present_business_page 等 wait_for_user 工具：中断循环，避免再流式输出操作指南
                    if (shouldBreakToolLoop(toolResponses)) {
                        log.info("[Chat][Trace] wait_for_user 工具调用，中断工具循环，等待用户操作");
                        return toolEventFlux;
                    }

                    trimToolCallContext(messages);
                    return toolEventFlux.concatWith(processToolCallsRecursively(ctx, depth + 1, nextLlmStart, eventSink));
                });
    }

    /**
     * 工具结果要求等待用户时，停止后续 LLM 轮次（页面/表单已自解释，无需再生成长文）。
     */
    private boolean shouldBreakToolLoop(
            List<org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse> toolResponses) {
        if (toolResponses == null || toolResponses.isEmpty()) {
            return false;
        }
        for (var r : toolResponses) {
            if (r == null || r.name() == null) {
                continue;
            }
            if (AskUserTool.TOOL_NAME.equals(r.name())
                    || PresentBusinessPageTool.TOOL_NAME.equals(r.name())) {
                return true;
            }
            // 通用：结果 JSON 含 break_loop / wait_for_user
            String data = r.responseData();
            if (data != null && looksLikeWaitForUserResult(data)) {
                return true;
            }
        }
        return false;
    }

    private boolean looksLikeWaitForUserResult(String data) {
        String trimmed = data.trim();
        if (!trimmed.startsWith("{")) {
            return false;
        }
        try {
            JsonNode node = objectMapper.readTree(trimmed);
            return node.path("break_loop").asBoolean(false) || node.path("wait_for_user").asBoolean(false);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 流式模型调用重试
     *
     * @param ctx       对话上下文
     * @param chatModel 模型实例
     * @param prompt    模型输入
     * @param depth     工具调用轮次
     * @param eventSink SSE 事件通道
     * @return 模型流式响应
     */
    private Flux<ChatResponse> streamModelWithRetry(ChatContext ctx, ChatModel chatModel, Prompt prompt,
                                                     int depth, Sinks.Many<String> eventSink) {
        int retryTimes = resolveModelRetryTimes(ctx.getConfigMap());
        return streamModelAttempt(ctx, chatModel, prompt, depth, eventSink, 0, retryTimes);
    }

    private Flux<ChatResponse> streamModelAttempt(ChatContext ctx, ChatModel chatModel, Prompt prompt,
                                                   int depth, Sinks.Many<String> eventSink,
                                                   int attempt, int retryTimes) {
        // 记录本轮尝试前 fullReply 的长度，用于失败时回滚
        int fullReplyLengthBefore = ctx.getFullReply().length();
        boolean[] receivedResponse = {false};
        return chatModel.stream(prompt)
                .takeUntilOther(Mono.delay(Duration.ofMillis(200))
                        .repeat()
                        .filter(tick -> ctx.isAborted())
                        .next())
                .doOnSubscribe(sub -> ctx.resetStreamTextTracking())
                .doOnNext(response -> {
                    receivedResponse[0] = true;
                    // 尝试从 chunk 提取 tool_call args delta，节流推「正在生成 · 已输出 N 字」
                    handleToolCallArgsDelta(response, ctx);
                })
                .doFinally(signal -> {
                    // 本轮模型流结束，清理 args 累积器避免跨轮泄漏
                    if (ctx.getToolArgsAccumulators() != null) {
                        ctx.getToolArgsAccumulators().clear();
                    }
                })
                .onErrorResume(e -> {
                    if (!receivedResponse[0] && attempt < retryTimes) {
                        int retryNo = attempt + 1;
                        long delayMs = (long) Math.pow(2, attempt) * 1000;
                        log.warn("[Chat] 流式模型调用失败，第{}次重试，等待{}ms: depth={}, error={}", retryNo, delayMs, depth, e.getMessage());
                        eventSink.tryEmitNext(STATUS_PREFIX + toolEventGenerator.errorRetryEvent(
                                "AI连接异常，正在重试中 " + retryNo + "/" + retryTimes,
                                classifyErrorCode(e), retryNo, retryTimes));
                        // 重试前回滚 fullReply 到本轮尝试前的状态，避免内容重复累积
                        if (ctx.getFullReply().length() > fullReplyLengthBefore) {
                            ctx.getFullReply().setLength(fullReplyLengthBefore);
                        }
                        // 重置 SensitiveStreamState，避免增量过滤状态混乱
                        if (ctx.getSensitiveStreamState() != null) {
                            Long agentId = ctx.getAgent() != null ? ctx.getAgent().getId() : null;
                            ctx.setSensitiveStreamState(new SensitiveWordFilter.StreamState(
                                    ctx.getConfigMap(), agentId, ctx.getSessionId()));
                        }
                        return Mono.delay(Duration.ofMillis(delayMs))
                                .thenMany(streamModelAttempt(ctx, chatModel, prompt, depth, eventSink, attempt + 1, retryTimes));
                    }
                    return Flux.error(e);
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
        List<LlmTraceSpanDTO> spans = ctx.getSpans();
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
                    int retryNo = attempt + 1;
                    long delayMs = (long) Math.pow(2, attempt) * 1000;
                    log.warn("[Chat] 非流式模型调用失败，第{}次重试，等待{}ms: depth={}, error={}", retryNo, delayMs, depth, e.getMessage());
                    eventSink.tryEmitNext(STATUS_PREFIX + toolEventGenerator.errorRetryEvent(
                            "AI连接异常，正在重试中 " + retryNo + "/" + retryTimes,
                            classifyErrorCode(e), retryNo, retryTimes));
                    try { Thread.sleep(delayMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                }
            }
        }
        if (response == null) {
            markStreamFailure(ctx, lastException);
            return Flux.just(STATUS_PREFIX + toolEventGenerator.errorEvent(
                    ctx.getStreamErrorMessage(), ctx.getStreamErrorCode()));
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
                        ctx.appendTraceMetadataReasoning(reasoningObj.toString());
                        ctx.appendReasoningContent(reasoningObj.toString());
                    }
                }
            }

            String text = (assistantMsg != null) ? assistantMsg.getText() : "";
            if (text == null) {
                text = "";
            }
            if (!text.isEmpty()) {
                ctx.appendTraceCompleteReply(text);
            }
            // 解析 inline thinking 标签（Ollama deepseek-r1 等）
            if (ctx.getReasoningContent().length() == 0 && !text.isEmpty()) {
                InlineThinkingStreamParser.ParseResult parsed = InlineThinkingStreamParser.parseComplete(text);
                if (!parsed.reasoningDelta().isEmpty()) {
                    ctx.appendReasoningContent(parsed.reasoningDelta());
                }
                text = parsed.contentDelta();
            } else if (InlineThinkingStreamParser.containsThinkingTags(text)) {
                text = InlineThinkingStreamParser.stripTags(text);
            }
            if (text.isEmpty() && ctx.getReasoningContent().length() == 0) {
                return Flux.empty();
            }
            SensitiveWordFilter.FilterResult filtered = SensitiveWordFilter.filterAiOutput(
                    text, configMap, agent.getId(), ctx.getSessionId());
            if (filtered.blocked()) {
                fullReply.setLength(0);
                fullReply.append(filtered.text());
                // 非流式路径同样置标记位，让 buildPersistMetadata 跳过 reasoningContent 暴露
                ctx.setSensitiveAiBlocked(true);
                spans.add(LlmTraceSpanDTO.of(llmSpanId, "s1", "llm_call", llmCallStart,
                        System.currentTimeMillis() - llmCallStart, "OK",
                        Map.of("depth", depth, "model", configMap.getOrDefault("modelId", ""),
                                "inputTokens", inputTokenHolder[0], "outputTokens", outputTokenHolder[0],
                                "streamOutput", false)));
                return Flux.just(STATUS_PREFIX + toolEventGenerator.sensitiveBlockEvent("ai_output", filtered.text()));
            }
            // 如果有 reasoning 内容，发送 reasoning_content 事件
            String reasoningSaved = ctx.getReasoningContent().toString();
            if (!reasoningSaved.isEmpty()) {
                return Flux.just(
                        STATUS_PREFIX + toolEventGenerator.reasoningEvent(reasoningSaved),
                        filtered.text());
            }
            fullReply.append(filtered.text());
            spans.add(LlmTraceSpanDTO.of(llmSpanId, "s1", "llm_call", llmCallStart,
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

        spans.add(LlmTraceSpanDTO.of(llmSpanId, "s1", "llm_call", llmCallStart,
                System.currentTimeMillis() - llmCallStart, "OK",
                Map.of("depth", depth, "model", configMap.getOrDefault("modelId", ""),
                        "toolCount", toolCalls.size(),
                        "toolNames", toolCalls.stream().map(AssistantMessage.ToolCall::name).toList().toString(),
                        "streamOutput", false)));

        List<Flux<String>> statusFluxes = new ArrayList<>();
        List<Map<String, Object>> kbResultsHolder = new ArrayList<>();
        List<org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();
        appendAssistantLeadingTextBeforeToolCall(ctx, agent, assistantMsg != null ? assistantMsg.getText() : null);
        int toolContentOffset = resolveToolBlockOffset(ctx);

        if (asyncEnabled && toolCalls.size() > 1) {
            log.info("[Chat][Trace] 工具调用(depth={}): {}个工具, 并行执行", depth, toolCalls.size());
            List<CompletableFuture<String>> futures = new ArrayList<>();
            for (AssistantMessage.ToolCall tc : toolCalls) {
                String tcArgs = tc.arguments() != null ? tc.arguments() : "";
                long tcToolCallId = appendToolCallStart(ctx, toolEventsList, statusFluxes, tc.name(), tcArgs, toolContentOffset);
                toolCallCountHolder[0]++;
                final String tcName = tc.name();
                final String safeTcArgs = toolArgsSanitizer.forChatCall(tcArgs);
                final int offsetFinal = toolContentOffset;
                final long tcIdFinal = tcToolCallId;
                futures.add(CompletableFuture.supplyAsync(() -> {
                    long tStart = System.currentTimeMillis();
                    String result = executeToolCallback(toolCallbackMap, tcName, safeTcArgs, agent.getId(), ctx.getSessionId(), requestId, null, ctx);
                    long tEnd = System.currentTimeMillis();
                    spans.add(LlmTraceSpanDTO.of("tool_" + toolCallCountHolder[0], llmSpanId, "tool_execute",
                            tStart, tEnd - tStart, "OK",
                            buildToolTraceAttributes(tcName, tcArgs, result)));
                    appendSubAgentTraceSpans(spans, "tool_" + toolCallCountHolder[0], tcName, result, tStart);
                    if ("query_knowledge".equals(tcName)) {
                        List<Map<String, Object>> kbResults = QueryKnowledgeTool.getSearchResults(requestId);
                        synchronized (kbResultsHolder) {
                            kbResultsHolder.addAll(kbResults);
                        }
                    }
                    // 暂存工具调用记录（复用 tcIdFinal 作为主键，前端按 id 拉取完整结果）
                    ToolCall toolCallLog = new ToolCall();
                    toolCallLog.setId(tcIdFinal);
                    toolCallLog.setToolName(tcName);
                    toolCallLog.setToolInput(safeTcArgs);
                    toolCallLog.setToolOutput(result);
                    toolCallLog.setStatus(result.startsWith(ToolResultPrefixes.FAILURE) || result.startsWith(ToolResultPrefixes.NOT_FOUND) ? "error" : "success");
                    toolCallLog.setErrorMessage(result.startsWith(ToolResultPrefixes.FAILURE) ? result : null);
                    synchronized (ctx.getPendingToolCalls()) {
                        ctx.getPendingToolCalls().add(toolCallLog);
                    }

                    synchronized (toolEventsList) {
                        appendToolCallResult(ctx, toolEventsList, statusFluxes, tcName, tcArgs, result, offsetFinal, tcIdFinal);
                    }
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
            long toolCallId = appendToolCallStart(ctx, toolEventsList, statusFluxes, toolName, safeArgs, toolContentOffset);

            long tToolStart = System.currentTimeMillis();
            String toolResult = executeToolCallback(toolCallbackMap, toolName, callArgs, agent.getId(), ctx.getSessionId(), requestId, null, ctx);
            long tToolEnd = System.currentTimeMillis();
            spans.add(LlmTraceSpanDTO.of("tool_" + toolCallCountHolder[0], llmSpanId, "tool_execute",
                    tToolStart, tToolEnd - tToolStart, "OK",
                    buildToolTraceAttributes(toolName, safeArgs, toolResult)));
            appendSubAgentTraceSpans(spans, "tool_" + toolCallCountHolder[0], toolName, toolResult, tToolStart);

            if ("query_knowledge".equals(toolName)) {
                List<Map<String, Object>> kbResults = QueryKnowledgeTool.getSearchResults(requestId);
                if (!kbResults.isEmpty()) {
                    kbResultsHolder.addAll(kbResults);
                }
            }

            // 暂存工具调用记录（复用 toolCallId 作为主键，前端按 id 拉取完整结果）
            ToolCall toolCallLog = new ToolCall();
            toolCallLog.setId(toolCallId);
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

            appendToolCallResult(ctx, toolEventsList, statusFluxes, toolName, safeArgs, toolResult, toolContentOffset, toolCallId);
            toolResponses.add(new org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse(
                    firstTool.id(), toolName, toolResult));
        }

        messages.add(org.springframework.ai.chat.messages.ToolResponseMessage.builder()
                .responses(toolResponses)
                .build());

        List<Map<String, Object>> kbResultsRef = kbResultsHolder;
        Flux<String> afterTool = buildToolMetadataFlux(kbResultsRef, toolEventsList, ragMetadataHolder);

        // tool_result 已由 appendToolCallResult 写入 statusFluxes，此处不再重复推送，
        // 否则前端会收到两次相同的 tool_result 事件（工具卡片渲染两份）。
        final int resultContentOffset = toolContentOffset;
        Flux<String> toolEventFlux = Flux.concat(statusFluxes)
                .concatWith(Flux.just(STATUS_PREFIX + toolEventGenerator.toolCompleteEvent(resultContentOffset)))
                .concatWith(afterTool);
        trimToolCallContext(messages);
        return toolEventFlux.concatWith(processToolCallsRecursively(ctx, depth + 1, System.currentTimeMillis(), eventSink));
    }

    /**
     * 工具调用上下文裁剪：当消息列表总字符数超过阈值时，压缩早期工具调用轮次为摘要消息，
     * 防止多轮工具调用撑爆上下文窗口。
     *
     * @param messages 消息列表（会被原地修改）
     */
    private void trimToolCallContext(List<org.springframework.ai.chat.messages.Message> messages) {
        // 1. 压缩历史中的 write_file 大参数（对标 Yuxi L1），再按字符上限裁剪轮次
        ChatMessageContextUtil.normalizeMessagesForLlm(messages);
        ChatMessageContextUtil.trimToolCallContext(messages, MAX_TOOL_CONTEXT_CHARS, TOOL_ROUNDS_TO_KEEP);
    }

    private String executeToolCallback(Map<String, ToolCallback> toolCallbackMap, String toolName,
                                       String callArgs, Long agentId, Long sessionId, String requestId,
                                       Sinks.Many<String> eventSink, ChatContext chatContext) {
        ToolCallback callback = toolCallbackMap.get(toolName);
        if (callback != null) {
            try {
                if (chatContext != null && chatContext.isAborted()) {
                    return ToolResultPrefixes.failureJson("CLIENT_ABORTED");
                }
                // 参数可能因 maxTokens 在字符串中途被截断：写文件场景先尝试修复再执行
                String effectiveArgs = callArgs;
                if (isLikelyTruncatedJson(effectiveArgs)) {
                    String repaired = toolArgsSanitizer.tryRepairTruncatedWriteArgs(toolName, effectiveArgs);
                    if (repaired != null) {
                        effectiveArgs = stripInternalRepairFlags(repaired);
                        log.warn("[Chat] 工具参数疑似截断，已修复后执行: name={}, rawLen={}, repairedLen={}",
                                toolName, callArgs != null ? callArgs.length() : 0, effectiveArgs.length());
                    }
                }
                // 2.1 工具执行超时保护：CompletableFuture 包装 + get(timeout)，防止 MCP 工具卡死
                long timeoutSeconds = resolveToolExecutionTimeoutSeconds(toolName, effectiveArgs);
                Map<String, Object> ctxMap = buildExecutableToolContext(chatContext, agentId, sessionId, requestId, toolName);
                final String argsForCall = effectiveArgs != null ? effectiveArgs : "{}";
                String result = CompletableFuture.supplyAsync(() -> {
                    try {
                        if (chatContext != null && chatContext.isAborted()) {
                            return ToolResultPrefixes.failureJson("CLIENT_ABORTED");
                        }
                        // 流式模式：绑定 Sink 使工具内部的 emit() 实时推送给前端
                        if (eventSink != null) {
                            ToolEventEmitter.setupSink(eventSink);
                        }
                        return callback.call(argsForCall, new ToolContext(ctxMap));
                    } finally {
                        if (eventSink != null) {
                            ToolEventEmitter.teardownSink();
                        }
                    }
                }, lightBotExecutor).get(timeoutSeconds, TimeUnit.SECONDS);
                if (chatContext != null && chatContext.isAborted()) {
                    return ToolResultPrefixes.failureJson("CLIENT_ABORTED");
                }
                if (!ToolResultPrefixes.isError(result)) {
                    sessionAttachmentRegistrar.registerFromToolResult(sessionId, toolName, result);
                    // write_todos 成功后把合并结果回写到 ChatContext，保证下次调用拿到最新基准（防丢项核心）
                    if ("write_todos".equals(toolName) && chatContext != null) {
                        updateCurrentTodosSnapshot(chatContext, result);
                    }
                }
                return result;
            } catch (TimeoutException e) {
                long timeoutSeconds = resolveToolExecutionTimeoutSeconds(toolName, callArgs);
                log.error("[Chat] 工具执行超时: name={}, timeout={}s", toolName, timeoutSeconds);
                return ToolResultPrefixes.failureJson("工具执行超时（" + timeoutSeconds + "秒），请稍后重试");
            } catch (Exception e) {
                // 工具参数 JSON 不完整（多为模型输出被 maxTokens 截断，字符串未闭合）
                if (isToolArgsParseError(e)) {
                    String repaired = toolArgsSanitizer.tryRepairTruncatedWriteArgs(toolName, callArgs);
                    if (repaired != null) {
                        try {
                            String retryArgs = stripInternalRepairFlags(repaired);
                            log.warn("[Chat] 工具参数解析失败后二次修复重试: name={}", toolName);
                            // 避免递归死循环：直接再调一次 callback（同步）
                            Map<String, Object> ctxMap = buildExecutableToolContext(
                                    chatContext, agentId, sessionId, requestId, toolName);
                            String retryResult = callback.call(retryArgs, new ToolContext(ctxMap));
                            if (!ToolResultPrefixes.isError(retryResult)) {
                                sessionAttachmentRegistrar.registerFromToolResult(sessionId, toolName, retryResult);
                            }
                            return retryResult;
                        } catch (Exception retryEx) {
                            log.error("[Chat] 截断参数修复后仍失败: name={}, error={}", toolName, retryEx.getMessage());
                        }
                    }
                    log.error("[Chat] 工具参数解析失败(疑似模型输出被截断): name={}, argsLen={}, error={}",
                            toolName, callArgs != null ? callArgs.length() : 0, e.getMessage());
                    return ToolResultPrefixes.failureJson("工具参数不完整，请重新调用并完整传入所需参数后重试。");
                }
                log.error("[Chat] 工具执行异常: name={}, error={}", toolName, e.getMessage(), e);
                return ToolResultPrefixes.failureJson(ToolResultPrefixes.FAILURE + ": " + e.getMessage());
            }
        }
        log.warn("[Chat][Trace] 工具不存在: name={}, 可用工具={}", toolName, toolCallbackMap.keySet());
        return ToolResultPrefixes.failureJson(ToolResultPrefixes.NOT_FOUND + ": " + toolName);
    }

    /**
     * 构建工具执行时的 ToolContext：合并 ToolPrep 预置可序列化键 + 本轮核心键。
     * <p>MCP 与内置工具同源（MCP 经 _meta 透传）；MCP 不注入不可序列化的 chatContext。</p>
     *
     * @param chatContext 对话上下文
     * @param agentId     Agent ID
     * @param sessionId   会话 ID
     * @param requestId   请求 ID
     * @param toolName    工具名（用于判断是否 MCP）
     * @return ToolContext Map
     */
    private Map<String, Object> buildExecutableToolContext(ChatContext chatContext, Long agentId,
                                                          Long sessionId, String requestId, String toolName) {
        Map<String, Object> ctxMap = new java.util.HashMap<>();
        // 1. 合并 ToolPrep 预置（callerContext / apiKeyId / allowedBusinessPages 等）
        if (chatContext != null && chatContext.getToolOptions() != null
                && chatContext.getToolOptions().getToolContext() != null) {
            for (Map.Entry<String, Object> e : chatContext.getToolOptions().getToolContext().entrySet()) {
                if (e.getKey() == null || "chatContext".equals(e.getKey()) || "exchange".equals(e.getKey())) {
                    continue;
                }
                if (e.getValue() != null) {
                    ctxMap.put(e.getKey(), e.getValue());
                }
            }
        }
        // 2. 覆盖核心运行时键
        ctxMap.put("agentId", agentId);
        ctxMap.put("sessionId", sessionId != null ? sessionId.toString() : "default");
        ctxMap.put("requestId", requestId);
        ctxMap.put("parentThreadId", sessionId != null ? sessionId.toString() : "default");
        if (chatContext != null) {
            if (chatContext.getUserId() != null) {
                ctxMap.put("userId", chatContext.getUserId());
            }
            if (chatContext.getRequest() != null) {
                if (chatContext.getRequest().getApiKeyId() != null) {
                    ctxMap.put("apiKeyId", chatContext.getRequest().getApiKeyId());
                }
                if (chatContext.getRequest().getExternalUserId() != null) {
                    ctxMap.put("externalUserId", chatContext.getRequest().getExternalUserId());
                }
                if (chatContext.getRequest().getCallerContext() != null
                        && !chatContext.getRequest().getCallerContext().isEmpty()) {
                    Map<String, Object> callerMap = chatContext.getRequest().getCallerContext().toMap();
                    ctxMap.put("callerContext", callerMap);
                    Object regionId = callerMap.get("regionId");
                    if (regionId != null) {
                        ctxMap.put("regionId", regionId);
                    }
                    Object enterpriseId = callerMap.get("enterpriseId");
                    if (enterpriseId != null) {
                        ctxMap.put("enterpriseId", enterpriseId);
                    }
                }
            }
            if (chatContext.getCurrentTodosSnapshot() != null) {
                ctxMap.put("currentTodos", chatContext.getCurrentTodosSnapshot());
            }
        }
        boolean mcpTool = chatContext != null && chatContext.getMcpToolNames() != null
                && chatContext.getMcpToolNames().contains(toolName);
        // 3. 非 MCP 保留 chatContext 回退（记忆工具等）；MCP 仅可序列化 map → _meta
        if (chatContext != null && !mcpTool) {
            ctxMap.put("chatContext", chatContext);
        }
        return ctxMap;
    }

    /**
     * 解析 write_todos 工具结果，把合并后的 todos 回写到 ChatContext.currentTodosSnapshot。
     * <p>下次 write_todos 调用时，WriteTodosTool.loadHistoryTodos 拿到的就是本次合并结果，
     * 避免同一轮内多次调用因基准过期导致丢项或重复新增</p>
     *
     * @param chatContext 对话上下文
     * @param toolResult  write_todos 返回的 JSON 字符串，格式：{"success":true,"todos":[{id,content,status}]}
     */
    private void updateCurrentTodosSnapshot(ChatContext chatContext, String toolResult) {
        if (toolResult == null || toolResult.isBlank()) {
            return;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(toolResult);
            if (!root.path("success").asBoolean(false)) {
                return;
            }
            com.fasterxml.jackson.databind.JsonNode todosNode = root.path("todos");
            if (!todosNode.isArray()) {
                return;
            }
            // 用 ArrayList 包装保证可变（loadCurrentTodos 返回的可能不可变）
            List<Map<String, String>> snapshot = new java.util.ArrayList<>();
            for (com.fasterxml.jackson.databind.JsonNode item : todosNode) {
                Map<String, String> m = new java.util.LinkedHashMap<>();
                m.put("id", item.path("id").asText(""));
                m.put("content", item.path("content").asText(""));
                m.put("status", item.path("status").asText("pending"));
                snapshot.add(m);
            }
            chatContext.setCurrentTodosSnapshot(snapshot);
        } catch (Exception e) {
            log.warn("[Chat] 回写 todos 快照失败: error={}", e.getMessage());
        }
    }

    /** 粗判 JSON 是否因截断而不完整（无法 parse 或括号/引号不平衡） */
    private static boolean isLikelyTruncatedJson(String args) {
        if (args == null || args.isBlank()) {
            return false;
        }
        String trimmed = args.trim();
        if (!trimmed.startsWith("{")) {
            return true;
        }
        try {
            new com.fasterxml.jackson.databind.ObjectMapper().readTree(trimmed);
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    private static String stripInternalRepairFlags(String json) {
        if (json == null) {
            return "{}";
        }
        // 去掉内部标记字段，避免 MethodToolCallback 因未知参数失败
        return json.replaceAll(",\\s*\"_repairedFromTruncation\"\\s*:\\s*true", "")
                .replaceAll("\"_repairedFromTruncation\"\\s*:\\s*true\\s*,?", "");
    }

    private long resolveToolExecutionTimeoutSeconds(String toolName, String callArgs) {
        if (!DelegateSubAgentTool.TOOL_NAME.equals(toolName)) {
            return TOOL_EXECUTION_TIMEOUT_SECONDS;
        }
        long timeoutSeconds = TOOL_EXECUTION_TIMEOUT_SECONDS;
        try {
            List<String> subNames = parseSubagentNames(callArgs);
            if (subNames.isEmpty()) {
                return timeoutSeconds;
            }
            long maxReadTimeout = timeoutSeconds;
            // 一次 IN 查询所有 SubAgent，替代循环内 N 次 getByName（v3.1 2.2.3）
            List<com.lightbot.entity.SubAgent> subAgents = subAgentService.listByNameIn(subNames);
            for (com.lightbot.entity.SubAgent subAgent : subAgents) {
                int readTimeout = subAgent.getReadTimeoutSeconds() != null
                        ? Math.max(10, Math.min(300, subAgent.getReadTimeoutSeconds()))
                        : (int) TOOL_EXECUTION_TIMEOUT_SECONDS;
                maxReadTimeout = Math.max(maxReadTimeout, readTimeout);
            }
            timeoutSeconds = Math.max(30L, maxReadTimeout + 30L);
        } catch (Exception e) {
            log.warn("[Chat] SubAgent tool timeout resolve failed, fallback to default: {}", e.getMessage());
        }
        return Math.min(timeoutSeconds, 360L);
    }

    private Flux<String> buildToolMetadataFlux(List<Map<String, Object>> kbResultsRef,
                                               List<Map<String, Object>> toolEventsList,
                                               String[] ragMetadataHolder) {
        return Flux.defer(() -> {
            if (!kbResultsRef.isEmpty() || !toolEventsList.isEmpty()) {
                Map<String, Object> metadataMap = new java.util.LinkedHashMap<>();
                if (!toolEventsList.isEmpty()) {
                    // toolEvents 拆到 message.tool_events 独立列；中间 metadata 仅承载 toolBlockOffsets
                    List<Map<String, Object>> compactEvents = ToolEventCompactUtil.compactForPersistence(toolEventsList);
                    List<Integer> offsets = ToolEventCompactUtil.extractToolBlockOffsets(compactEvents);
                    if (!offsets.isEmpty()) {
                        metadataMap.put("toolBlockOffsets", offsets);
                    }
                }
                if (!kbResultsRef.isEmpty()) {
                    List<RagReferenceVO> refs = kbResultsRef.stream().map(this::mapToRagReference).toList();
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
                    // 已触发敏感拦截：丢弃后续 chunk，避免重复发 sensitive_block 与正文增量
                    if (ctx.isSensitiveAiBlocked()) {
                        return Flux.empty();
                    }
                    // MimoChatClient 已处理 reasoning 提取（emitReasoningContent），
                    // 此处直接透传 [STATUS] 事件，无需重复解析
                    if (chunk.startsWith(STATUS_PREFIX)) {
                        return Flux.just(chunk);
                    }
                    String delta = sensitiveState != null ? sensitiveState.processChunk(chunk) : chunk;
                    if (sensitiveState != null && sensitiveState.isBlocked()) {
                        // MiMo 直连首次命中敏感词：清空已累积正文写入拦截文案，置标记位短路后续 chunk
                        fullReply.setLength(0);
                        fullReply.append(SensitiveWordFilter.AI_BLOCK_MESSAGE);
                        ctx.setSensitiveAiBlocked(true);
                        return Flux.just(STATUS_PREFIX + toolEventGenerator.sensitiveBlockEvent("ai_output", SensitiveWordFilter.AI_BLOCK_MESSAGE));
                    }
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
        if (configMap == null) return 20;
        Object val = configMap.get(ConfigKeys.Agent.MAX_EXECUTION_STEPS);
        if (val instanceof Number n) return Math.max(1, Math.min(200, n.intValue()));
        if (val != null) {
            try { return Math.max(1, Math.min(200, Integer.parseInt(val.toString()))); } catch (Exception ignored) {}
        }
        return 20;
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
     * 从流式 getText() 提取增量后送入 inline thinking 解析器。
     */
    private InlineThinkingStreamParser.ParseResult feedStreamTextChunk(ChatContext ctx, String currentText) {
        String delta = ctx.consumeStreamTextDelta(currentText);
        if (delta.isEmpty()) {
            return InlineThinkingStreamParser.ParseResult.empty();
        }
        ctx.appendRawLlmStreamText(delta);
        return ctx.computeInlineThinkingStreamDelta();
    }

    /**
     * 将 inline thinking 解析结果转为 SSE：reasoning_content + 正文 chunk，并写入 ctx.reasoningContent / fullReply。
     */
    private Flux<String> fluxFromInlineThinking(ChatContext ctx, Agent agent,
                                                InlineThinkingStreamParser.ParseResult parsed,
                                                Runnable onContentAppended) {
        return fluxFromInlineThinking(ctx, agent, parsed, onContentAppended, true);
    }

    private Flux<String> fluxFromInlineThinking(ChatContext ctx, Agent agent,
                                                InlineThinkingStreamParser.ParseResult parsed,
                                                Runnable onContentAppended,
                                                boolean appendToFullReply) {
        // 已触发敏感拦截：丢弃后续 chunk（reasoning/正文均不再累积、不再重复发 sensitive_block），
        // 只让首条 sensitive_block 事件下发，DONE 由 buildDoneEvent 按最小 metadata 输出
        if (ctx.isSensitiveAiBlocked()) {
            return Flux.empty();
        }
        if (parsed.isEmpty()) {
            return Flux.empty();
        }
        List<String> items = new ArrayList<>(2);
        String reasoningDelta = parsed.reasoningDelta();
        String contentDelta = parsed.contentDelta();
        if (!reasoningDelta.isEmpty()) {
            String reasoning = ctx.appendReasoningContent(reasoningDelta);
            if (!reasoning.isEmpty()) {
                items.add(STATUS_PREFIX + toolEventGenerator.reasoningEvent(reasoning));
            }
        }
        if (!contentDelta.isEmpty()) {
            String delta = ctx.getSensitiveStreamState() != null
                    ? ctx.getSensitiveStreamState().processChunk(contentDelta)
                    : SensitiveWordFilter.filterAiOutput(contentDelta, ctx.getConfigMap(), agent.getId(), ctx.getSessionId()).text();
            if (ctx.getSensitiveStreamState() != null && ctx.getSensitiveStreamState().isBlocked()) {
                // 首次命中：清空已累积正文写入拦截文案，置标记位让后续 chunk 全部短路
                ctx.getFullReply().setLength(0);
                ctx.getFullReply().append(SensitiveWordFilter.AI_BLOCK_MESSAGE);
                ctx.setSensitiveAiBlocked(true);
                return Flux.just(STATUS_PREFIX + toolEventGenerator.sensitiveBlockEvent("ai_output", SensitiveWordFilter.AI_BLOCK_MESSAGE));
            }
            if (!delta.isEmpty()) {
                if (appendToFullReply) {
                    ctx.getFullReply().append(delta);
                }
                if (onContentAppended != null) {
                    onContentAppended.run();
                }
                items.add(delta);
            }
        }
        return items.isEmpty() ? Flux.empty() : Flux.fromIterable(items);
    }

    /** 同步写入 leading 正文（用于工具 offset 计算，避免与后续 Flux 重复 append） */
    private boolean appendInlineThinkingContentDelta(ChatContext ctx, Agent agent,
                                                     InlineThinkingStreamParser.ParseResult parsed) {
        if (parsed == null || parsed.isEmpty() || parsed.contentDelta().isEmpty()) {
            return false;
        }
        String delta = ctx.getSensitiveStreamState() != null
                ? ctx.getSensitiveStreamState().processChunk(parsed.contentDelta())
                : SensitiveWordFilter.filterAiOutput(parsed.contentDelta(), ctx.getConfigMap(), agent.getId(), ctx.getSessionId()).text();
        if (delta.isEmpty()) {
            return false;
        }
        ctx.getFullReply().append(delta);
        return true;
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
        return searchResults.stream().map(this::mapToRagReference).toList();
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

    /**
     * 工具调用前记录正文前缀锚点，入库时据此重新对齐 contentOffset。
     */
    private void putContentPrefixAnchor(ChatContext ctx, Map<String, Object> evt, int contentOffset) {
        if (ctx == null || evt == null || contentOffset <= 0) {
            return;
        }
        String reply = ctx.getFullReply().toString();
        int splitAt = ToolEventCompactUtil.resolveToolBlockSplitOffset(reply, null, contentOffset);
        evt.put("contentOffset", splitAt);
        if (splitAt > 0) {
            evt.put("contentPrefixAnchor", reply.substring(0, splitAt));
        }
    }

    /** 按句末标点对齐工具块切分点 */
    private int resolveToolBlockOffset(ChatContext ctx) {
        String reply = ctx.getFullReply().toString();
        return ToolEventCompactUtil.resolveToolBlockSplitOffset(reply, null, reply.length());
    }

    /**
     * 非流式 / 阻塞路径：同一轮 assistant 消息若携带正文，须先写入 fullReply 再计算 tool offset。
     */
    private void appendAssistantLeadingTextBeforeToolCall(ChatContext ctx, Agent agent, String assistantText) {
        if (ctx == null || assistantText == null || assistantText.isEmpty()) {
            return;
        }
        String text = assistantText;
        ctx.appendTraceCompleteReply(text);
        if (ctx.getReasoningContent().length() == 0) {
            InlineThinkingStreamParser.ParseResult parsed = InlineThinkingStreamParser.parseComplete(text);
            if (!parsed.reasoningDelta().isEmpty()) {
                ctx.appendReasoningContent(parsed.reasoningDelta());
            }
            text = parsed.contentDelta();
        } else if (InlineThinkingStreamParser.containsThinkingTags(text)) {
            text = InlineThinkingStreamParser.stripTags(text);
        }
        if (text == null || text.isEmpty()) {
            return;
        }
        Map<String, Object> configMap = ctx.getConfigMap();
        Long agentId = agent != null ? agent.getId() : null;
        String filtered = SensitiveWordFilter.filterAiOutput(
                text, configMap, agentId, ctx.getSessionId()).text();
        if (!filtered.isEmpty()) {
            ctx.getFullReply().append(filtered);
        }
    }

    /** SSE/落库展示用：写文件大参数改为短摘要，避免前端与 metadata 膨胀 */
    private String compactArgsForEvent(String toolName, String args) {
        return toolArgsSanitizer.compactForHistory(toolName, args);
    }

    private long appendToolCallStart(ChatContext ctx, List<Map<String, Object>> toolEventsList,
                                     List<Flux<String>> statusFluxes,
                                     String toolName, String args, int contentOffset) {
        // 按需推送 skill_active（工具属于某个 Skill 时）；同步对话 statusFluxes 为 null，仅写入 toolEventsList
        Flux<String> skillFlux = emitSkillActiveIfNeeded(ctx, toolName, toolEventsList, contentOffset);
        if (skillFlux != null && statusFluxes != null) {
            statusFluxes.add(skillFlux);
        }

        if (DelegateSubAgentTool.TOOL_NAME.equals(toolName)) {
            int delegationIndex = ctx != null ? ctx.assignSubAgentDelegationIndex() : 0;
            if (ctx != null) {
                // 批次事件由 SubAgentTaskService 统一发布；这里只提供本轮插入位置和委派序号。
                // 与普通 tool_call 一致：记录句末对齐后的切分点与正文前缀锚点，避免前端按滞后 offset 截断正文。
                String reply = ctx.getFullReply().toString();
                int splitAt = ToolEventCompactUtil.resolveToolBlockSplitOffset(reply, null, contentOffset);
                ctx.setSubAgentContentOffset(splitAt);
                ctx.setSubAgentContentPrefixAnchor(splitAt > 0 ? reply.substring(0, splitAt) : null);
                ctx.setSubAgentDelegationIndex(delegationIndex);
            }
            // 委派类工具不入 tool_calls 表，返回 0 表示无 toolCallId
            return 0L;
        }
        // 预生成 toolCallId：tool_call/tool_result 事件 + tool_calls 表主键共用同一 id
        long toolCallId = com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
        String dn = getToolDisplayName(ctx, toolName);
        String icon = getToolIcon(ctx, toolName);
        Map<String, Object> callEvt = new java.util.LinkedHashMap<>();
        callEvt.put("type", "tool_call");
        callEvt.put("toolName", toolName);
        if (dn != null) callEvt.put("displayName", dn);
        if (icon != null) callEvt.put("icon", icon);
        callEvt.put("args", compactArgsForEvent(toolName, args));
        callEvt.put("contentOffset", contentOffset);
        callEvt.put("toolCallId", String.valueOf(toolCallId));
        putContentPrefixAnchor(ctx, callEvt, contentOffset);
        int normalizedOffset = callEvt.get("contentOffset") instanceof Number n ? n.intValue() : contentOffset;
        toolEventsList.add(callEvt);
        String callJson = toolEventGenerator.toolCallEvent(toolName, dn, icon, compactArgsForEvent(toolName, args), normalizedOffset, toolCallId);
        if (ctx != null && ctx.getRealtimeStatusEmitter() != null) {
            ctx.emitRealtimeStatus(callJson);
        } else if (statusFluxes != null) {
            statusFluxes.add(Flux.just(STATUS_PREFIX + callJson));
        }
        // 文件写入类工具立即推「正在生成 xxx...」起始状态，替代裸 spinner 让用户感知到 AI 正在写哪个文件
        emitFileWritingStatus(ctx, statusFluxes, toolName, args);
        return toolCallId;
    }

    /**
     * 从 SpringAI 流式 chunk 中提取 tool_call arguments 增量，节流推送文件写入进度
     * <p>若适配器在中间 chunk 不暴露 args delta（仅在最终 chunk 给完整 args），
     * 本方法无中间态可推，退化为 B-3「正在生成 xxx...」兜底。</p>
     *
     * @param response 模型流式响应块
     * @param ctx      对话上下文
     */
    private void handleToolCallArgsDelta(ChatResponse response, ChatContext ctx) {
        if (response == null || ctx == null || ctx.getToolArgsAccumulators() == null) {
            return;
        }
        try {
            List<Generation> results = response.getResults();
            if (results == null || results.isEmpty()) {
                return;
            }
            AssistantMessage assistantMsg = results.get(0).getOutput();
            if (assistantMsg == null) {
                return;
            }
            List<AssistantMessage.ToolCall> toolCalls = assistantMsg.getToolCalls();
            if (toolCalls == null || toolCalls.isEmpty()) {
                return;
            }
            for (AssistantMessage.ToolCall tc : toolCalls) {
                String key = (tc.id() != null && !tc.id().isBlank())
                        ? tc.id()
                        : ("name:" + (tc.name() != null ? tc.name() : "unknown"));
                ToolCallAccumulator acc = ctx.getToolArgsAccumulators().computeIfAbsent(key, k -> {
                    ToolCallAccumulator created = new ToolCallAccumulator();
                    created.setToolCallKey(k);
                    created.setToolName(tc.name());
                    return created;
                });
                if (tc.name() != null && !tc.name().isBlank()) {
                    acc.setToolName(tc.name());
                }
                // 仅文件写入类工具推进度，避免其它工具 args 噪音
                if (!ToolCallAccumulator.isFileWritingTool(acc.getToolName())) {
                    continue;
                }
                acc.acceptArgsFragment(tc.arguments());
                acc.tryExtractPath();
                if (!acc.shouldPush()) {
                    continue;
                }
                String basename = acc.basename();
                if (basename == null) {
                    continue;
                }
                String msg = "正在生成 " + basename + " · 已输出 " + acc.getArgsLen() + " 字";
                ctx.emitRealtimeStatus(toolEventGenerator.toolStatusEvent(msg, 0));
                acc.markPushed();
            }
        } catch (Exception e) {
            log.debug("[Chat] tool_call args delta 处理跳过: {}", e.getMessage());
        }
    }

    /**
     * 对 sandbox_write_file / sandbox_append_file 推送「正在生成 {filename}...」起始状态
     * <p>替代原先工具执行期间前端只能看到裸 spinner 的体验，让用户立刻知道 AI 正在写哪个文件</p>
     * <p>仅在工具属于文件写入类时推送，其他工具直接跳过</p>
     *
     * @param ctx 对话上下文（提供实时推送通道）
     * @param statusFluxes 非流式模式下的批量状态收集容器
     * @param toolName 工具名称
     * @param args 工具入参 JSON（含 path 字段）
     */
    private void emitFileWritingStatus(ChatContext ctx, List<Flux<String>> statusFluxes,
                                       String toolName, String args) {
        // 仅文件写入类工具推起始状态，其他工具跳过避免噪音
        if (!"sandbox_write_file".equals(toolName) && !"sandbox_append_file".equals(toolName)) {
            return;
        }
        String basename = extractFileBasename(args);
        if (basename == null) {
            return;
        }
        String msg = "正在生成 " + basename + "...";
        String statusJson = toolEventGenerator.toolStatusEvent(msg, 0);
        if (ctx != null && ctx.getRealtimeStatusEmitter() != null) {
            ctx.emitRealtimeStatus(statusJson);
        } else if (statusFluxes != null) {
            statusFluxes.add(Flux.just(STATUS_PREFIX + statusJson));
        }
    }

    /**
     * 从工具入参 JSON 中提取 path 字段的 basename（仅文件名部分）
     *
     * @param args 工具入参 JSON
     * @return basename；args 非法或无 path 字段时返回 null
     */
    private String extractFileBasename(String args) {
        if (args == null || args.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(args);
            JsonNode pathNode = node.get("path");
            if (pathNode == null || pathNode.isNull()) {
                return null;
            }
            String path = pathNode.asText("");
            if (path.isEmpty()) {
                return null;
            }
            // 取最后一段作为文件名（path 可能是 outputs/reports/report.md）
            int slash = path.lastIndexOf('/');
            return slash >= 0 ? path.substring(slash + 1) : path;
        } catch (Exception e) {
            return null;
        }
    }

    private void appendToolCallResult(ChatContext ctx, List<Map<String, Object>> toolEventsList, List<Flux<String>> statusFluxes,
                                    String toolName, String args, String result, int contentOffset, long toolCallId) {
        String truncated = toolEventGenerator.truncateForSse(result);
        if (DelegateSubAgentTool.TOOL_NAME.equals(toolName)
                || DelegateSubAgentTool.RESULT_TOOL_NAME.equals(toolName)
                || DelegateSubAgentTool.CANCEL_TOOL_NAME.equals(toolName)) {
            // 委派、查询、取消均回填同一个批次面板，禁止落入普通 ToolCallsGroup。
            Map<String, Object> update = parseSubAgentToolResult(truncated);
            update.put("type", "subagent_batch_update");
            update.put("contentOffset", contentOffset);
            if (ctx != null && ctx.getSubAgentDelegationIndex() != null) {
                update.put("delegationIndex", ctx.getSubAgentDelegationIndex());
            }
            toolEventsList.add(update);
            try {
                String updateJson = objectMapper.writeValueAsString(update);
                if (ctx != null && ctx.getRealtimeStatusEmitter() != null) ctx.emitRealtimeStatus(updateJson);
                else if (statusFluxes != null) statusFluxes.add(Flux.just(STATUS_PREFIX + updateJson));
            } catch (Exception ignored) {
                // 前端展示事件失败不影响工具结果回填。
            }
            if (ctx != null) {
                ctx.setSubAgentContentOffset(null);
            }
            return;
        }
        String dn = getToolDisplayName(ctx, toolName);
        String icon = getToolIcon(ctx, toolName);
        Map<String, Object> resultEvt = new java.util.LinkedHashMap<>();
        resultEvt.put("type", "tool_result");
        resultEvt.put("toolName", toolName);
        if (dn != null) resultEvt.put("displayName", dn);
        if (icon != null) resultEvt.put("icon", icon);
        resultEvt.put("result", truncated);
        resultEvt.put("contentOffset", contentOffset);
        if (toolCallId > 0) {
            resultEvt.put("toolCallId", String.valueOf(toolCallId));
        }
        toolEventsList.add(resultEvt);
        String resultJson = toolEventGenerator.toolResultEvent(toolName, dn, icon, truncated, contentOffset, toolCallId);
        if (ctx != null && ctx.getRealtimeStatusEmitter() != null) {
            ctx.emitRealtimeStatus(resultJson);
        } else if (statusFluxes != null) {
            statusFluxes.add(Flux.just(STATUS_PREFIX + resultJson));
        }
        // write_todos 落库后额外推流 todos_updated：前端状态栏据此实时刷新，无需等 5s 轮询
        if ("write_todos".equals(toolName)) {
            emitTodosUpdated(ctx, statusFluxes, truncated, contentOffset);
        }
    }

    /**
     * 解析 write_todos 工具结果，向 SSE 推送 todos_updated 事件。
     * <p>不进 toolEventsList（避免在消息气泡里二次展示）；仅作为运行时事件给状态栏消费。</p>
     */
    private void emitTodosUpdated(ChatContext ctx, List<Flux<String>> statusFluxes,
                                   String toolResult, int contentOffset) {
        try {
            JsonNode resultNode = objectMapper.readTree(toolResult);
            if (!resultNode.path("success").asBoolean(false)) {
                return;
            }
            JsonNode todosNode = resultNode.path("todos");
            if (!todosNode.isArray()) {
                return;
            }
            Map<String, Object> todoEvt = new LinkedHashMap<>();
            todoEvt.put("type", "todos_updated");
            todoEvt.put("todos", objectMapper.convertValue(todosNode, List.class));
            todoEvt.put("contentOffset", contentOffset);
            String json = objectMapper.writeValueAsString(todoEvt);
            if (ctx != null && ctx.getRealtimeStatusEmitter() != null) {
                ctx.emitRealtimeStatus(json);
            } else if (statusFluxes != null) {
                statusFluxes.add(Flux.just(STATUS_PREFIX + json));
            }
        } catch (Exception ignored) {
            // todos_updated 推流失败不影响主流程。
        }
    }

    private String getToolDisplayName(ChatContext ctx, String toolName) {
        if (ctx == null || ctx.getToolDisplayNameMap() == null) return null;
        return ctx.getToolDisplayNameMap().get(toolName);
    }

    private String getToolIcon(ChatContext ctx, String toolName) {
        if (ctx == null || ctx.getToolIconMap() == null) return null;
        return ctx.getToolIconMap().get(toolName);
    }

    /**
     * 将 RAG 检索单行结果映射为 RagReferenceVO（QA_PAIR vs CHUNK 分支）
     */
    private RagReferenceVO mapToRagReference(Map<String, Object> row) {
        RagReferenceVO vo = new RagReferenceVO();
        String resultType = (String) row.get("result_type");
        if (RagResultType.QA_PAIR.equals(resultType)) {
            vo.setSourceType(RagResultType.QA_PAIR);
            vo.setDocumentName("问答对");
            vo.setQaPairId(parseLongObj(row.get("id")));
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
        vo.setScore(row.get("score") != null ? ((Number) row.get("score")).doubleValue() : null);
        vo.setKnowledgeId(parseLongObj(row.get("knowledge_id")));
        vo.setDocumentId(parseLongObj(row.get("document_id")));
        vo.setChunkId(parseLongObj(row.get("chunk_id")));
        return vo;
    }

    /**
     * 追加并推送单条 SubAgent 流式中间事件
     */
    private void appendSubAgentStreamEvent(ChatContext ctx, List<Map<String, Object>> toolEventsList,
                                           List<Flux<String>> statusFluxes,
                                           ChatContext.SubAgentEvent se, int contentOffset) {
        Integer delegationIndex = ctx != null ? ctx.getSubAgentDelegationIndex() : null;
        String json;
        Map<String, Object> evt = new HashMap<>();
        switch (se.type()) {
            case "token" -> {
                evt.put("type", "subagent_token");
                evt.put("subagentName", se.subagentName());
                evt.put("content", se.content());
                evt.put("contentOffset", contentOffset);
                if (delegationIndex != null) evt.put("delegationIndex", delegationIndex);
                json = toolEventGenerator.enrichSubagentJson(
                        toolEventGenerator.subagentTokenEvent(se.subagentName(), se.content(), contentOffset),
                        delegationIndex);
            }
            case "tool_call" -> {
                String toolName = se.content();
                String subIcon = resolveSubAgentIcon(se.subagentName());
                evt.put("type", "subagent_tool_call");
                evt.put("subagentName", se.subagentName());
                evt.put("displayName", resolveSubAgentDisplayName(se.subagentName()));
                if (subIcon != null) evt.put("icon", subIcon);
                evt.put("toolName", toolName);
                evt.put("args", "{}");
                evt.put("contentOffset", contentOffset);
                if (delegationIndex != null) evt.put("delegationIndex", delegationIndex);
                json = toolEventGenerator.enrichSubagentJson(
                        toolEventGenerator.subagentToolCallEvent(
                                se.subagentName(), resolveSubAgentDisplayName(se.subagentName()),
                                toolName, toolName, "{}", contentOffset),
                        delegationIndex);
            }
            case "tool_result" -> {
                String subIcon = resolveSubAgentIcon(se.subagentName());
                evt.put("type", "subagent_tool_result");
                evt.put("subagentName", se.subagentName());
                evt.put("displayName", resolveSubAgentDisplayName(se.subagentName()));
                if (subIcon != null) evt.put("icon", subIcon);
                evt.put("toolName", "");
                evt.put("result", se.content());
                evt.put("contentOffset", contentOffset);
                if (delegationIndex != null) evt.put("delegationIndex", delegationIndex);
                json = toolEventGenerator.enrichSubagentJson(
                        toolEventGenerator.subagentToolResultEvent(
                                se.subagentName(), resolveSubAgentDisplayName(se.subagentName()),
                                "", "", se.content(), contentOffset),
                        delegationIndex);
            }
            default -> {
                return;
            }
        }
        toolEventsList.add(evt);
        if (ctx != null && ctx.getRealtimeStatusEmitter() != null) {
            ctx.emitRealtimeStatus(json);
        } else if (statusFluxes != null) {
            statusFluxes.add(Flux.just(STATUS_PREFIX + json));
        }
    }

    private String resolveSubAgentDisplayName(String subagentName) {
        if (subagentName == null || subagentName.isBlank()) {
            return subagentName;
        }
        com.lightbot.entity.SubAgent subAgent = subAgentService.getByName(subagentName);
        if (subAgent != null && subAgent.getDisplayName() != null && !subAgent.getDisplayName().isBlank()) {
            return subAgent.getDisplayName();
        }
        return subagentName;
    }

    private String resolveSubAgentIcon(String subagentName) {
        if (subagentName == null || subagentName.isBlank()) {
            return null;
        }
        com.lightbot.entity.SubAgent subAgent = subAgentService.getByName(subagentName);
        if (subAgent != null && subAgent.getIcon() != null && !subAgent.getIcon().isBlank()) {
            return subAgent.getIcon();
        }
        return null;
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseSubAgentToolResult(String result) {
        try {
            return new LinkedHashMap<>(objectMapper.readValue(result, Map.class));
        } catch (Exception ignored) {
            return new LinkedHashMap<>(Map.of("status", "failed", "error", result));
        }
    }

    /**
     * 为 SubAgent 工具调用补充 batch/task 元数据，供可观测调用树消费。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> buildToolTraceAttributes(String toolName, String args, String result) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("toolName", toolName);
        attributes.put("args", args);
        attributes.put("resultLength", result != null ? result.length() : 0);
        if (!isSubAgentTool(toolName) || result == null || result.isBlank()) {
            return attributes;
        }
        try {
            Map<String, Object> output = objectMapper.readValue(result, Map.class);
            Object batchId = output.get("batch_id");
            if (batchId != null) attributes.put("batchId", batchId.toString());
            Object taskId = output.get("task_id");
            if (taskId != null) attributes.put("taskId", taskId.toString());
            if (output.get("mode") != null) attributes.put("subagentMode", output.get("mode"));
            if (output.get("status") != null) attributes.put("subagentStatus", output.get("status"));
            if (output.get("results") instanceof List<?> results) {
                attributes.put("subagentTaskCount", results.size());
                attributes.put("subagentTaskIds", results.stream()
                        .filter(Map.class::isInstance)
                        .map(Map.class::cast)
                        .map(item -> item.get("task_id"))
                        .filter(Objects::nonNull)
                        .map(Object::toString)
                        .toList());
            }
        } catch (Exception ignored) {
            // 工具结果非 JSON 时维持通用工具 span。
        }
        return attributes;
    }

    /**
     * 在通用 tool_execute span 下补充 SubAgent 批次和任务子 span，形成可观测调用树。
     */
    @SuppressWarnings("unchecked")
    private void appendSubAgentTraceSpans(List<LlmTraceSpanDTO> spans, String toolSpanId,
                                          String toolName, String result, long startTime) {
        if (!isSubAgentTool(toolName) || result == null || result.isBlank()) {
            return;
        }
        try {
            Map<String, Object> output = objectMapper.readValue(result, Map.class);
            String batchId = output.get("batch_id") != null ? output.get("batch_id").toString() : null;
            if (batchId == null || batchId.isBlank()) {
                return;
            }
            String batchSpanId = toolSpanId + ":subagent_batch";
            Object status = output.get("status");
            Map<String, Object> batchAttributes = new LinkedHashMap<>();
            batchAttributes.put("batchId", batchId);
            batchAttributes.put("mode", output.get("mode"));
            batchAttributes.put("aggregation", output.get("aggregation"));
            synchronized (spans) {
                spans.add(LlmTraceSpanDTO.of(batchSpanId, toolSpanId, "subagent_batch", startTime, 0L,
                        "failed".equals(status) ? "ERROR" : "OK", batchAttributes));
                if (output.get("results") instanceof List<?> results) {
                    int index = 0;
                    for (Object raw : results) {
                        if (!(raw instanceof Map<?, ?> task)) continue;
                        String taskId = task.get("task_id") != null ? task.get("task_id").toString() : String.valueOf(index);
                        Map<String, Object> taskAttributes = new LinkedHashMap<>();
                        taskAttributes.put("batchId", batchId);
                        taskAttributes.put("taskId", taskId);
                        taskAttributes.put("subagentName", task.get("subagent_name"));
                        taskAttributes.put("status", task.get("status"));
                        taskAttributes.put("replyPreview", task.get("reply"));
                        taskAttributes.put("error", task.get("error"));
                        spans.add(LlmTraceSpanDTO.of(batchSpanId + ":task:" + index, batchSpanId,
                                "subagent_task", startTime, 0L,
                                "failed".equals(task.get("status")) ? "ERROR" : "OK", taskAttributes));
                        index++;
                    }
                }
            }
        } catch (Exception ignored) {
            // Trace 增强失败不能影响对话工具链。
        }
    }

    private boolean isSubAgentTool(String toolName) {
        return DelegateSubAgentTool.TOOL_NAME.equals(toolName)
                || DelegateSubAgentTool.RESULT_TOOL_NAME.equals(toolName)
                || DelegateSubAgentTool.CANCEL_TOOL_NAME.equals(toolName);
    }

    @SuppressWarnings("unchecked")
    private List<String> parseSubagentNames(String args) {
        List<String> names = new ArrayList<>();
        if (args == null || args.isBlank()) {
            return names;
        }
        try {
            Map<String, Object> map = objectMapper.readValue(args, Map.class);
            Object tasksObj = map.get("tasks");
            if (tasksObj instanceof List<?> tasks) {
                for (Object item : tasks) {
                    if (item instanceof Map<?, ?> raw) {
                        Object nameObj = ((Map<String, Object>) raw).get("subagent_name");
                        if (nameObj == null) {
                            nameObj = ((Map<String, Object>) raw).get("subagentName");
                        }
                        if (nameObj != null && !nameObj.toString().isBlank()) {
                            names.add(nameObj.toString());
                        }
                    }
                }
            }
            if (names.isEmpty()) {
                Map<String, String> parsed = parseSubagentArgs(args);
                String subName = parsed.get("subagentName");
                if (subName != null && !subName.isBlank()) {
                    names.add(subName);
                }
            }
        } catch (Exception e) {
            log.warn("[Chat] 解析 SubAgent 名称失败: {}", e.getMessage());
        }
        return names;
    }

    /**
     * 分类异常信息为用户友好的错误提示
     */
    private String classifyErrorMessage(Throwable e) {
        return com.lightbot.util.ModelErrorClassifier.classifyMessage(e);
    }

    /**
     * 分类异常为错误码
     */
    private String classifyErrorCode(Throwable e) {
        return com.lightbot.util.ModelErrorClassifier.classifyCode(e);
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

    /** 安全地将 Object 转为 Long（兼容 Number 和 String 类型） */
    private static Long parseLongObj(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        try { return Long.parseLong(value.toString()); } catch (NumberFormatException e) { return null; }
    }

    /**
     * 判断异常是否为工具参数 JSON 解析失败（多因模型输出被 maxTokens 截断导致 JSON 未闭合）。
     * 遍历 cause 链，命中 Jackson 的 JSON 解析异常即认定。
     *
     * @param e 工具执行捕获到的异常
     * @return true 表示为参数解析失败，应返回可读提示而非底层报错
     */
    private static boolean isToolArgsParseError(Throwable e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof com.fasterxml.jackson.core.JsonProcessingException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
