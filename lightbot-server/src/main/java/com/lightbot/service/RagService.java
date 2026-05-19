package com.lightbot.service;

/**
 * RAG 检索增强生成服务接口
 *
 * @author finch
 * @since 2026-05-19
 */
public interface RagService {

    /**
     * RAG 问答
     *
     * @param knowledgeId 知识库ID
     * @param question    用户问题
     * @param providerId  模型提供商ID
     * @return 回答
     */
    String ask(Long knowledgeId, String question, Long providerId);
}
