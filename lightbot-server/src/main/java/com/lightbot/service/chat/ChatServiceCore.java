package com.lightbot.service.chat;

import reactor.core.publisher.Flux;

/**
 * 对话管道终端核心：执行 LLM 调用 + 工具循环
 *
 * @author finch
 * @since 2026-05-23
 */
@FunctionalInterface
public interface ChatServiceCore {

    /**
     * 执行核心 LLM 流式调用
     *
     * @param context 已准备完毕的管道上下文
     * @return Flux 流式输出
     */
    Flux<String> execute(ChatContext context);
}
