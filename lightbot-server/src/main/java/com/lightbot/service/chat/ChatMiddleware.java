package com.lightbot.service.chat;

import reactor.core.publisher.Flux;

/**
 * 对话中间件接口。
 * <p>洋葱模型：每个中间件处理自己的关注点，然后调用 next.proceed() 传递给下一个。</p>
 * <p>TraceMiddleware 通过包裹 next.proceed() 的 Flux 实现 doOnComplete/doOnError。</p>
 *
 * @author finch
 * @since 2026-05-23
 */
public interface ChatMiddleware {

    /**
     * 执行中间件逻辑
     *
     * @param context 共享管道上下文
     * @param next    剩余管道（下一个中间件或终端核心）
     * @return Flux 流式输出
     */
    Flux<String> execute(ChatContext context, ChatMiddlewareChain next);
}
