package com.lightbot.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lightbot.entity.Embedding;

import java.util.List;
import java.util.Map;

/**
 * 向量服务接口：负责向量存储和相似度检索
 *
 * @author finch
 * @since 2026-05-19
 */
public interface EmbeddingService extends IService<Embedding> {

    /**
     * 存储向量（pgvector 类型需原生SQL操作）
     *
     * @param chunkId   分块ID
     * @param modelName 模型名称
     * @param vector    向量数据
     */
    void saveVector(Long chunkId, String modelName, float[] vector);

    /**
     * 批量存储向量（减少数据库往返次数）
     *
     * @param chunkIds  分块ID列表
     * @param modelName 模型名称
     * @param vectors   向量数据列表，与 chunkIds 一一对应
     */
    void batchSaveVectors(List<Long> chunkIds, String modelName, List<float[]> vectors);

    /**
     * 余弦相似度检索 Top-K
     *
     * @param knowledgeId 知识库ID
     * @param queryVector 查询向量
     * @param topK        返回数量
     * @param threshold   相似度阈值
     * @return 检索结果（chunk_id, content, document_name, score）
     */
    List<Map<String, Object>> searchSimilar(Long knowledgeId, float[] queryVector, int topK, double threshold);

    /**
     * 余弦相似度检索 Top-K（阈值过滤下沉到SQL层）
     *
     * @param knowledgeId 知识库ID
     * @param queryVector 查询向量
     * @param topK        返回数量
     * @param threshold   相似度阈值（SQL WHERE 过滤）
     * @return 检索结果（chunk_id, content, document_name, score）
     */
    List<Map<String, Object>> searchSimilarSql(Long knowledgeId, float[] queryVector, int topK, double threshold);

    /**
     * 余弦相似度检索 Top-K（不过滤阈值，返回原始结果）
     *
     * @param knowledgeId 知识库ID
     * @param queryVector 查询向量
     * @param topK        返回数量
     * @return 原始检索结果（chunk_id, content, document_name, score）
     */
    List<Map<String, Object>> searchSimilarRaw(Long knowledgeId, float[] queryVector, int topK);

    /**
     * 删除指定知识库的所有向量
     *
     * @param knowledgeId 知识库ID
     */
    void deleteByKnowledgeId(Long knowledgeId);

    /**
     * 删除指定文档的所有向量
     *
     * @param documentId 文档ID
     */
    void deleteByDocumentId(Long documentId);
}
