package com.lightbot.service.chat;

import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 中间件链式执行器。
 * <p>按顺序执行中间件列表，最后一个中间件之后调用终端核心。</p>
 *
 * @author finch
 * @since 2026-05-23
 */
public class ChatMiddlewareChain {

    private final List<ChatMiddleware> middlewares;
    private final int index;
    private final ChatServiceCore core;

    public ChatMiddlewareChain(List<ChatMiddleware> middlewares, int index, ChatServiceCore core) {
        this.middlewares = middlewares;
        this.index = index;
        this.core = core;
    }

    /**
     * 创建链的入口
     */
    public static ChatMiddlewareChain of(List<ChatMiddleware> middlewares, ChatServiceCore core) {
        return new ChatMiddlewareChain(middlewares, 0, core);
    }

    /**
     * 执行当前中间件或终端核心
     */
    public Flux<String> proceed(ChatContext context) {
        if (index < middlewares.size()) {
            return middlewares.get(index).execute(context,
                    new ChatMiddlewareChain(middlewares, index + 1, core));
        }
        return core.execute(context);
    }
}
