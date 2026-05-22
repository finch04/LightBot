package com.lightbot.service;

import com.lightbot.dto.ChatRequest;
import com.lightbot.dto.RagReferenceVO;
import reactor.core.publisher.Flux;

import java.util.List;

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

    /**
     * 获取会话的RAG引用信息
     * <p>用于在对话完成后获取检索到的文献引用</p>
     *
     * @param sessionId 会话ID
     * @param agentId AgentID
     * @param question 用户问题
     * @return RAG引用列表
     */
    List<RagReferenceVO> getRagReferences(Long sessionId, Long agentId, String question);
}
