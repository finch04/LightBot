package com.lightbot.service;

import com.lightbot.dto.RagSearchResultVO;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * RAG 检索增强生成服务接口
 *
 * @author finch
 * @since 2026-05-19
 */
public interface RagService {

    /**
     * RAG 问答（同步）
     *
     * @param knowledgeId 知识库ID
     * @param question    用户问题
     * @param providerId  模型提供商ID
     * @return 回答
     */
    String ask(Long knowledgeId, String question, Long providerId);

    /**
     * RAG 问答（流式）
     *
     * @param knowledgeId 知识库ID
     * @param question    用户问题
     * @param providerId  模型提供商ID
     * @return 流式回答
     */
    Flux<String> askStream(Long knowledgeId, String question, Long providerId);

    /**
     * 纯向量检索测试（不调用LLM），返回检索到的文档块列表
     *
     * @param knowledgeId 知识库ID
     * @param question    用户问题
     * @return 检索结果列表
     */
    List<RagSearchResultVO> search(Long knowledgeId, String question);
}
