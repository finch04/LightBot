package com.lightbot.service.chat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.dto.LlmTraceSpan;
import com.lightbot.entity.Agent;
import com.lightbot.entity.ChatSession;
import com.lightbot.entity.LlmTrace;
import com.lightbot.entity.Message;
import com.lightbot.enums.MessageRole;
import com.lightbot.mapper.MessageMapper;
import com.lightbot.model.ModelFactory;
import com.lightbot.service.*;
import com.lightbot.util.SensitiveWordFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
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
    private final InitMiddleware initMiddleware;
    private final ModelFactory modelFactory;
    private final AgentService agentService;
    private final ChatSessionService chatSessionService;
    private final MessageMapper messageMapper;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public Flux<String> execute(ChatContext ctx, ChatMiddlewareChain next) {
        return next.proceed(ctx)
                .doOnComplete(() -> {
                    long tEnd = System.currentTimeMillis();
                    long totalTokens = ctx.getInputTokenHolder()[0] + ctx.getOutputTokenHolder()[0];

                    // 1. 持久化AI回复
                    Long agentId = ctx.getAgent() != null ? ctx.getAgent().getId() : null;
                    String replyToSave = SensitiveWordFilter.filterAiOutput(
                            ctx.getFullReply().toString(), ctx.getConfigMap(), agentId, ctx.getSessionId()).text();
                    messageMiddleware.saveMessage(ctx.getSessionId(), MessageRole.ASSISTANT,
                            replyToSave, ctx.getRagMetadataHolder()[0], (int) totalTokens);
                    ctx.getFullReply().setLength(0);
                    ctx.getFullReply().append(replyToSave);

                    // 2. 异步生成标题
                    taskExecutor.execute(() -> generateTitle(ctx.getSessionId(), ctx.getAgent()));

                    // 3. 记录发送给LLM的消息列表（用于可观测性排查）
                    if (ctx.getMessages() != null && !ctx.getMessages().isEmpty()) {
                        List<Map<String, Object>> messageList = ctx.getMessages().stream()
                                .map(msg -> {
                                    Map<String, Object> item = new java.util.LinkedHashMap<>();
                                    item.put("role", msg.getMessageType().getValue());
                                    String content = extractMessageContent(msg);
                                    // 截断超长内容（单条消息超过2000字符只保留前2000）
                                    if (content != null && content.length() > 2000) {
                                        content = content.substring(0, 2000) + "...(截断)";
                                    }
                                    item.put("content", content);
                                    return item;
                                }).toList();
                        ctx.getSpans().add(buildSpan("llm_input", null, "messages_to_llm",
                                ctx.getStartTime(), 0, "OK",
                                Map.of("messageCount", messageList.size(), "messages", messageList)));
                    }

                    // 4. 追加AI思考内容到spans
                    if (ctx.getReasoningContent().length() > 0) {
                        ctx.getSpans().add(buildSpan("reasoning", null, "ai_reasoning",
                                ctx.getStartTime(), tEnd - ctx.getStartTime(), "OK",
                                Map.of("content", ctx.getReasoningContent().toString())));
                    }

                    // 5. 构建Trace并异步写库
                    persistTrace(ctx, "completed", tEnd - ctx.getStartTime(), null);
                })
                .doOnError(e -> {
                    long tErr = System.currentTimeMillis();
                    log.error("[Chat] 流式对话异常: sessionId={}, error={}", ctx.getSessionId(), e.getMessage(), e);
                    persistTrace(ctx, "failed", tErr - ctx.getStartTime(), e.getMessage());
                });
    }

    /**
     * 提取消息内容（兼容各类 Message 类型）
     */
    private String extractMessageContent(org.springframework.ai.chat.messages.Message msg) {
        if (msg instanceof org.springframework.ai.chat.messages.SystemMessage sm) {
            return sm.getText();
        } else if (msg instanceof org.springframework.ai.chat.messages.UserMessage um) {
            return um.getText();
        } else if (msg instanceof org.springframework.ai.chat.messages.AssistantMessage am) {
            return am.getText();
        } else {
            return msg.toString();
        }
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
        trace.setStatus(status);
        trace.setInputTokens(ctx.getInputTokenHolder()[0]);
        trace.setOutputTokens(ctx.getOutputTokenHolder()[0]);
        trace.setTotalTokens(ctx.getInputTokenHolder()[0] + ctx.getOutputTokenHolder()[0]);
        trace.setToolCallCount(ctx.getToolCallCountHolder()[0]);
        trace.setTotalDurationMs(durationMs);
        trace.setReplyContent(ctx.getFullReply().toString());
        trace.setErrorMessage(errorMessage);
        try {
            trace.setSpans(OBJECT_MAPPER.writeValueAsString(ctx.getSpans()));
        } catch (Exception ex) {
            trace.setSpans("[]");
        }
        llmTraceService.recordTrace(trace);
    }

    /**
     * 异步生成对话标题：标题仍为"新对话"且消息数>=2时，调用AI生成简短标题
     */
    public void generateTitle(Long sessionId, Agent agent) {
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

            // 4. 使用会话绑定的Agent模型生成标题
            Long providerId = resolveTitleProviderId(agent);
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
     * 解析标题生成使用的providerId
     */
    public Long resolveTitleProviderId(Agent agent) {
        if (agent != null) {
            Map<String, Object> configMap = initMiddleware.parseConfig(agent.getConfig());
            Long providerId = initMiddleware.getProviderId(configMap);
            if (providerId != null) {
                return providerId;
            }
        }
        return initMiddleware.getDefaultProviderId();
    }

    /**
     * 构建调用链Span对象
     */
    public LlmTraceSpan buildSpan(String spanId, String parentSpanId, String name,
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
}
