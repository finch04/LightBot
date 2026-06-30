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
import com.lightbot.enums.MessageRole;
import com.lightbot.mapper.MessageMapper;
import com.lightbot.model.ModelFactory;
import com.lightbot.model.ProviderResolver;
import com.lightbot.service.*;
import com.lightbot.util.LlmTraceMessageSerializer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Trace 中间件：记录调用链、异步生成标题
 * <p>作为最外层中间件，通过包裹下游 Flux 的 doOnComplete/doOnError 实现后置处理。
 * AI 回复持久化已移至 ChatServiceImpl.buildDoneEvent（[DONE] 之前执行）。</p>
 *
 * @author finch
 * @since 2026-05-23
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TraceMiddleware implements ChatMiddleware {

    private final LlmTraceService llmTraceService;
    private final ProviderResolver providerResolver;
    private final ModelFactory modelFactory;
    private final AgentService agentService;
    private final ChatSessionService chatSessionService;
    private final MessageMapper messageMapper;
    private final ObjectMapper objectMapper;

    @Override
    public Flux<String> execute(ChatContext ctx, ChatMiddlewareChain next) {
        return next.proceed(ctx)
                .doOnComplete(() -> {
                    // 消息持久化已在 ChatServiceImpl.buildDoneEvent 中完成（[DONE] 之前），
                    // 此处仅做 Trace 记录等后置清理。
                    long tEnd = System.currentTimeMillis();

                    // 标题生成已移至 ChatServiceImpl.buildDoneEvent（助手消息落库后），避免 doOnComplete 早于持久化导致消息数不足

                    // 1. 记录本轮用户输入（含附件，便于 Trace 排查）
                    recordUserInputSpan(ctx);

                    // 2.1 记录 ask_user 父子关联
                    annotateAskUserLink(ctx);

                    // 3. 记录发送给 LLM 的完整请求（messages + config + tools）
                    ChatRequest chatRequest = ctx.getRequest();
                    if (ctx.getMessages() != null && !ctx.getMessages().isEmpty()) {
                        boolean lastUserHasAttachments = chatRequest != null && chatRequest.getAttachments() != null
                                && !chatRequest.getAttachments().isEmpty();
                        List<Map<String, Object>> messageList = LlmTraceMessageSerializer.toTraceMessages(
                                ctx.getMessages(), chatRequest, lastUserHasAttachments);
                        Map<String, Object> llmInputAttrs = new java.util.LinkedHashMap<>();
                        llmInputAttrs.put("messageCount", messageList.size());
                        llmInputAttrs.put("messages", messageList);

                        // 请求配置（model、temperature、topP、maxTokens 等）
                        if (ctx.getConfigMap() != null && !ctx.getConfigMap().isEmpty()) {
                            llmInputAttrs.put("config", new java.util.LinkedHashMap<>(ctx.getConfigMap()));
                        }

                        // 工具定义（name、description、inputSchema）
                        if (ctx.getToolCallbackMap() != null && !ctx.getToolCallbackMap().isEmpty()) {
                            List<Map<String, Object>> toolDefs = ctx.getToolCallbackMap().values().stream()
                                    .map(cb -> {
                                        Map<String, Object> td = new java.util.LinkedHashMap<>();
                                        td.put("name", cb.getToolDefinition().name());
                                        td.put("description", cb.getToolDefinition().description());
                                        td.put("inputSchema", cb.getToolDefinition().inputSchema());
                                        return td;
                                    })
                                    .toList();
                            llmInputAttrs.put("toolCount", toolDefs.size());
                            llmInputAttrs.put("tools", toolDefs);
                        }

                        ctx.getSpans().add(LlmTraceSpan.of("llm_input", null, "messages_to_llm",
                                ctx.getStartTime(), 0, "OK", llmInputAttrs));
                    }

                    // 4. 追加AI思考内容到spans
                    if (ctx.getReasoningContent().length() > 0) {
                        ctx.getSpans().add(LlmTraceSpan.of("reasoning", null, "ai_reasoning",
                                ctx.getStartTime(), tEnd - ctx.getStartTime(), "OK",
                                Map.of("content", ctx.getReasoningContent().toString())));
                    }

                    // 5. 构建Trace并异步写库
                    String traceStatus = ctx.isStreamFailed() ? "failed" : "completed";
                    persistTrace(ctx, traceStatus, tEnd - ctx.getStartTime(), ctx.getStreamErrorMessage());
                })
                .doOnError(e -> {
                    long tErr = System.currentTimeMillis();
                    log.error("[Chat] 流式对话异常: sessionId={}, error={}", ctx.getSessionId(), e.getMessage(), e);
                    persistTrace(ctx, "failed", tErr - ctx.getStartTime(), e.getMessage());
                });
    }

    /**
     * 记录 ask_user 父子消息关联：当前用户消息是对 ask_user 工具的回复
     */
    private void annotateAskUserLink(ChatContext ctx) {
        if (ctx.getUserMessageParentId() == null) {
            return;
        }
        Map<String, Object> attrs = new java.util.LinkedHashMap<>();
        attrs.put("childMessageId", ctx.getUserMessageId());
        attrs.put("parentMessageId", ctx.getUserMessageParentId());
        attrs.put("linkType", "ask_user_response");
        ctx.getSpans().add(LlmTraceSpan.of("ask_user_link", null, "ask_user_link",
                ctx.getStartTime(), 0, "OK", attrs));
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
        if (ctx.getUserMessageId() != null) {
            attrs.put("messageId", ctx.getUserMessageId().toString());
        }
        if (ctx.getUserMessageParentId() != null) {
            attrs.put("parentMessageId", ctx.getUserMessageParentId().toString());
        }
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
        long userId = ctx.getUserId() != null ? ctx.getUserId() : 0;

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
        // 数据库安全清理（非法字符），敏感词已在流式/非流式过程中过滤
        trace.setReplyContent(com.lightbot.util.TextNormalizeUtil.sanitizeForAiMessage(replyContent, 0));
        trace.setErrorMessage(com.lightbot.util.TextNormalizeUtil.sanitizeForDatabase(errorMessage));
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

            // 2. 获取前2条消息（减少 token 数，加快生成速度）
            List<Message> messages = messageMapper.selectList(
                    new LambdaQueryWrapper<Message>()
                            .eq(Message::getSessionId, sessionId)
                            .orderByAsc(Message::getCreateTime)
                            .last("LIMIT 2"));
            if (messages.size() < 2) {
                return;
            }

            // 3. 拼接对话文本（每条消息最多100字，避免过长）
            StringBuilder conversationText = new StringBuilder();
            for (Message msg : messages) {
                String role = msg.getRole() == MessageRole.USER ? "用户" : "助手";
                String content = msg.getContent();
                if (content != null && content.length() > 100) {
                    content = content.substring(0, 100) + "...";
                }
                conversationText.append(role).append("：").append(content).append("\n");
            }

            // 4. 使用最便宜的模型 + 限制输出 token（50 tokens 足够生成标题）
            Long providerId = providerResolver.resolve();
            Map<String, Object> titleConfig = new HashMap<>();
            // ensureModelIdInConfig 会自动设置为最便宜的模型（如 qwen-turbo、gpt-4o-mini）
            modelFactory.ensureModelIdInConfig(providerId, titleConfig);
            // 限制输出 token，加快生成速度
            titleConfig.put("maxTokens", 50);
            // 降低 temperature，提高确定性
            titleConfig.put("temperature", 0.3);
            ChatOptions options = modelFactory.buildChatOptions(providerId, titleConfig);

            List<org.springframework.ai.chat.messages.Message> promptMessages = new ArrayList<>();
            promptMessages.add(new SystemMessage("生成标题，只输出标题，不超过20字。"));
            promptMessages.add(new UserMessage("对话：\n" + conversationText));

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
