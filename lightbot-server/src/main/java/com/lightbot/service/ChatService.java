package com.lightbot.service;

import com.lightbot.dto.ChatRequest;
import reactor.core.publisher.Flux;

/**
 * AI对话服务接口
 *
 * @author finch
 * @since 2026-05-19
 */
public interface ChatService {

    /**
     * 同步对话
     *
     * @param request 对话请求
     * @return AI回复
     */
    String chat(ChatRequest request);

    /**
     * 流式对话（SSE）
     *
     * @param request 对话请求
     * @return 流式回复
     */
    Flux<String> chatStream(ChatRequest request);
}
