package com.lightbot.service.chat;

import com.lightbot.enums.MessageRole;
import com.lightbot.util.SensitiveWordFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 用户输入敏感词拦截（在保存用户消息、调用模型/工作流之前）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserSensitiveMiddleware implements ChatMiddleware {

    private final MessageMiddleware messageMiddleware;

    @Override
    public Flux<String> execute(ChatContext ctx, ChatMiddlewareChain next) {
        String userMessage = ctx.getRequest().getMessage();
        Long agentId = ctx.getAgent() != null ? ctx.getAgent().getId() : null;
        SensitiveWordFilter.FilterResult check = SensitiveWordFilter.checkUserInput(
                userMessage, ctx.getConfigMap(), agentId, ctx.getSessionId());

        if (check.blocked()) {
            String tip = check.text();
            ctx.getFullReply().append(tip);
            messageMiddleware.saveMessage(ctx.getSessionId(), MessageRole.ASSISTANT, tip);
            return Flux.just(tip);
        }
        return next.proceed(ctx);
    }
}
