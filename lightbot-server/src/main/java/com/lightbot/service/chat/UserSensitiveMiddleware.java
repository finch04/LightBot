package com.lightbot.service.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.enums.MessageRole;
import com.lightbot.util.SensitiveWordFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;

import static com.lightbot.service.chat.ToolEventGenerator.STATUS_PREFIX;
import static com.lightbot.service.chat.ToolEventGenerator.sensitiveBlockEvent;

/**
 * 用户输入敏感词拦截（在保存用户消息、调用模型/工作流之前）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserSensitiveMiddleware implements ChatMiddleware {

    private final MessageMiddleware messageMiddleware;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public Flux<String> execute(ChatContext ctx, ChatMiddlewareChain next) {
        String userMessage = ctx.getRequest().getMessage();
        if ((userMessage == null || userMessage.isBlank())
                && ctx.getRequest().getAttachments() != null && !ctx.getRequest().getAttachments().isEmpty()) {
            userMessage = "请根据附件内容回答。";
        }
        Long agentId = ctx.getAgent() != null ? ctx.getAgent().getId() : null;
        SensitiveWordFilter.FilterResult check = SensitiveWordFilter.checkUserInput(
                userMessage, ctx.getConfigMap(), agentId, ctx.getSessionId());

        if (check.blocked()) {
            String tip = check.text();
            ctx.getFullReply().append(tip);
            // 保存 metadata 标记为 sensitiveBlock，便于历史消息回显
            try {
                String metadata = OBJECT_MAPPER.writeValueAsString(Map.of("sensitiveBlock", "user_input"));
                messageMiddleware.saveMessage(ctx.getSessionId(), MessageRole.ASSISTANT, tip, metadata, 0);
            } catch (Exception e) {
                messageMiddleware.saveMessage(ctx.getSessionId(), MessageRole.ASSISTANT, tip);
            }
            return Flux.just(STATUS_PREFIX + sensitiveBlockEvent("user_input", tip));
        }
        return next.proceed(ctx);
    }
}
