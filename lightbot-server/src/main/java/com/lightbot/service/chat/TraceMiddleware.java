package com.lightbot.service.chat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.dto.ChatAttachmentDTO;
import com.lightbot.dto.ChatRequest;
import com.lightbot.dto.LlmTraceSpan;
import com.lightbot.entity.Agent;
import com.lightbot.entity.ChatSession;
import com.lightbot.entity.LlmTrace;
import com.lightbot.entity.Message;
import com.lightbot.entity.ToolCall;
import com.lightbot.enums.MessageRole;
import com.lightbot.mapper.MessageMapper;
import com.lightbot.model.ModelFactory;
import com.lightbot.model.ProviderResolver;
import com.lightbot.service.*;
import com.lightbot.util.LlmTraceMessageSerializer;
import com.lightbot.util.SensitiveWordFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Trace 中间件：记录调用链、持久化AI回复、异步生成标题
 * <p>作为最外层中间件，通过包裹下游 Flux 的 doOnComplete/doOnError 实现后置处理。</p>
 *
 * @author finch
 * @since 2026-05-23
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TraceMiddleware implements ChatMiddleware {

    private final TaskExecutor taskExecutor;
    private final LlmTraceService llmTraceService;
    private final MessageMiddleware messageMiddleware;
    private final ProviderResolver providerResolver;
    private final ModelFactory modelFactory;
    private final AgentService agentService;
    private final ChatSessionService chatSessionService;
    private final MessageMapper messageMapper;
    private final ToolCallService toolCallService;
    private final ObjectMapper objectMapper;

    @Override
    public Flux<String> execute(ChatContext ctx, ChatMiddlewareChain next) {
        return next.proceed(ctx)
                .doOnComplete(() -> {
                    long tEnd = System.currentTimeMillis();
                    long totalTokens = ctx.getInputTokenHolder()[0] + ctx.getOutputTokenHolder()[0];

                    // 1. 持久化AI回复（合并 reasoningContent 到 metadata）
                    Long agentId = ctx.getAgent() != null ? ctx.getAgent().getId() : null;
                    String replyToSave = SensitiveWordFilter.filterAiOutput(
                            ctx.getFullReply().toString(), ctx.getConfigMap(), agentId, ctx.getSessionId()).text();
                    String metadataStr = buildPersistMetadata(ctx);
                    Long messageId = messageMiddleware.saveMessage(ctx.getSessionId(), MessageRole.ASSISTANT,
                            replyToSave, metadataStr, (int) totalTokens);

                    // 1.1 批量写入工具调用记录（关联 assistant 消息ID）
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
                    ctx.getFullReply().setLength(0);
                    ctx.getFullReply().append(replyToSave);

                    // 2. 异步生成标题
                    taskExecutor.execute(() -> generateTitle(ctx.getSessionId(), ctx.getAgent(), ctx.getConfigMap()));

                    // 3. 记录本轮用户输入（含附件，便于 Trace 排查）
                    recordUserInputSpan(ctx);

                    // 4. 记录发送给 LLM 的完整消息（系统提示词、历史、用户图文等，不截断）
                    ChatRequest chatRequest = ctx.getRequest();
                    if (ctx.getMessages() != null && !ctx.getMessages().isEmpty()) {
                        boolean lastUserHasAttachments = chatRequest != null && chatRequest.getAttachments() != null
                                && !chatRequest.getAttachments().isEmpty();
                        List<Map<String, Object>> messageList = LlmTraceMessageSerializer.toTraceMessages(
                                ctx.getMessages(), chatRequest, lastUserHasAttachments);
                        Map<String, Object> llmInputAttrs = new java.util.LinkedHashMap<>();
                        llmInputAttrs.put("messageCount", messageList.size());
                        llmInputAttrs.put("messages", messageList);
                        ctx.getMessages().stream()
                                .filter(org.springframework.ai.chat.messages.SystemMessage.class::isInstance)
                                .map(org.springframework.ai.chat.messages.SystemMessage.class::cast)
                                .findFirst()
                                .ifPresent(sm -> llmInputAttrs.put("systemPrompt", sm.getText()));
                        ctx.getSpans().add(LlmTraceSpan.of("llm_input", null, "messages_to_llm",
                                ctx.getStartTime(), 0, "OK", llmInputAttrs));
                    }

                    // 5. 追加AI思考内容到spans
                    if (ctx.getReasoningContent().length() > 0) {
                        ctx.getSpans().add(LlmTraceSpan.of("reasoning", null, "ai_reasoning",
                                ctx.getStartTime(), tEnd - ctx.getStartTime(), "OK",
                                Map.of("content", ctx.getReasoningContent().toString())));
                    }

                    // 6. 构建Trace并异步写库
                    persistTrace(ctx, "completed", tEnd - ctx.getStartTime(), null);
                })
                .doOnError(e -> {
                    long tErr = System.currentTimeMillis();
                    log.error("[Chat] 流式对话异常: sessionId={}, error={}", ctx.getSessionId(), e.getMessage(), e);
                    persistTrace(ctx, "failed", tErr - ctx.getStartTime(), e.getMessage());
                });
    }

    /**
     * 记录本轮用户问题与附件
     */
    private void recordUserInputSpan(ChatContext ctx) {
        ChatRequest request = ctx.getRequest();
        if (request == null) {
            return;
        }
        Map<String, Object> attrs = new java.util.LinkedHashMap<>();
        String text = request.getMessage();
        if (text != null && !text.isBlank()) {
            attrs.put("content", text);
        }
        if (request.getBizParams() != null && !request.getBizParams().isEmpty()) {
            attrs.put("bizParams", request.getBizParams());
        }
        List<ChatAttachmentDTO> attachments = request.getAttachments();
        if (attachments != null && !attachments.isEmpty()) {
            attrs.put("attachments", attachments.stream().map(this::attachmentToTraceMap).toList());
        }
        if (attrs.isEmpty()) {
            return;
        }
        ctx.getSpans().add(LlmTraceSpan.of("user_input", null, "user_message",
                ctx.getStartTime(), 0, "OK", attrs));
    }

    private Map<String, Object> attachmentToTraceMap(ChatAttachmentDTO att) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        if (att.getId() != null) {
            m.put("id", att.getId());
        }
        if (att.getType() != null) {
            m.put("type", att.getType());
        }
        if (att.getFileName() != null) {
            m.put("fileName", att.getFileName());
        }
        if (att.getMimeType() != null) {
            m.put("mimeType", att.getMimeType());
        }
        if (att.getPreviewUrl() != null) {
            m.put("previewUrl", att.getPreviewUrl());
        }
        if (att.getObjectKey() != null) {
            m.put("objectKey", att.getObjectKey());
        }
        return m;
    }

    /**
     * 持久化Trace记录
     */
    private void persistTrace(ChatContext ctx, String status, long durationMs, String errorMessage) {
        String modelName = ctx.getConfigMap().containsKey("modelId") ? ctx.getConfigMap().get("modelId").toString() : null;
        long userId = 0;
        try { userId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong(); } catch (Exception ignored) {}

        LlmTrace trace = new LlmTrace();
        trace.setRequestId(ctx.getRequestId());
        trace.setSessionId(ctx.getSessionId());
        trace.setUserId(userId);
        trace.setAgentId(ctx.getAgent() != null ? ctx.getAgent().getId() : null);
        trace.setAgentName(ctx.getAgent() != null ? ctx.getAgent().getName() : null);
        trace.setModel(modelName);
        trace.setTraceSource("chat");
        trace.setStatus(status);
        trace.setInputTokens(ctx.getInputTokenHolder()[0]);
        trace.setOutputTokens(ctx.getOutputTokenHolder()[0]);
        trace.setTotalTokens(ctx.getInputTokenHolder()[0] + ctx.getOutputTokenHolder()[0]);
        trace.setToolCallCount(ctx.getToolCallCountHolder()[0]);
        trace.setTotalDurationMs(durationMs);
        // 如果 fullReply 为空但有 reasoning 内容，将 reasoning 作为回复内容记录
        String replyContent = ctx.getFullReply().toString();
        if (replyContent.isBlank() && ctx.getReasoningContent().length() > 0) {
            replyContent = ctx.getReasoningContent().toString();
        }
        trace.setReplyContent(replyContent);
        trace.setErrorMessage(errorMessage);
        try {
            trace.setSpans(objectMapper.writeValueAsString(ctx.getSpans()));
        } catch (Exception ex) {
            trace.setSpans("[]");
        }
        llmTraceService.recordTrace(trace);
    }

    /**
     * 异步生成对话标题：标题仍为"新对话"且消息数>=2时，调用AI生成简短标题
     */
    public void generateTitle(Long sessionId, Agent agent) {
        generateTitle(sessionId, agent, null);
    }

    /**
     * 异步生成对话标题
     *
     * @param runtimeConfig 对话运行时 config（含 configVersion / 线上快照），为空则回退 agent 表 config
     */
    public void generateTitle(Long sessionId, Agent agent, Map<String, Object> runtimeConfig) {
        try {
            // 1. 检查会话是否存在且标题仍为默认值
            ChatSession session = chatSessionService.getById(sessionId);
            if (session == null || !ChatSession.DEFAULT_TITLE.equals(session.getTitle())) {
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

            // 4. 统一使用系统默认模型（速度快，不占用对话模型资源）
            Long providerId = providerResolver.resolve();
            Map<String, Object> titleConfig = new HashMap<>();
            modelFactory.ensureModelIdInConfig(providerId, titleConfig);
            ChatOptions options = modelFactory.buildChatOptions(providerId, titleConfig);

            List<org.springframework.ai.chat.messages.Message> promptMessages = new ArrayList<>();
            promptMessages.add(new SystemMessage("你是一个标题生成助手，只输出标题，不要任何其他内容。"));
            promptMessages.add(new UserMessage("请根据以下对话内容生成一个简短的标题（不超过20个字，不要加引号）：\n" + conversationText));

            ChatResponse response = com.lightbot.util.LlmTraceContext.callWithoutTrace(() ->
                    modelFactory.getChatModel(providerId).call(new Prompt(promptMessages, options)));
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
     * 构建持久化 metadata：合并 ragMetadata + reasoningContent
     */
    private String buildPersistMetadata(ChatContext ctx) {
        try {
            Map<String, Object> meta = new java.util.LinkedHashMap<>();
            // 解析现有 ragMetadata（toolEvents、toolBlockOffsets、ragReferences 等）
            String ragMeta = ctx.getRagMetadataHolder()[0];
            if (ragMeta != null && !ragMeta.isBlank()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> existing = objectMapper.readValue(ragMeta, Map.class);
                meta.putAll(existing);
            }
            // 追加 reasoningContent
            if (ctx.getReasoningContent().length() > 0) {
                meta.put("reasoningContent", ctx.getReasoningContent().toString());
            }
            // 追加 sensitiveBlock 标记（AI 输出被拦截时）
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

}
