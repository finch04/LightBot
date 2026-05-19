package com.lightbot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lightbot.entity.Chunk;
import com.lightbot.entity.Embedding;
import com.lightbot.mapper.ChunkMapper;
import com.lightbot.mapper.EmbeddingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 向量服务：负责向量存储和相似度检索
 *
 * @author finch
 * @since 2026-05-19
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService extends ServiceImpl<EmbeddingMapper, Embedding> {

    private final JdbcTemplate jdbcTemplate;
    private final ChunkMapper chunkMapper;

    /**
     * 存储向量（原生SQL，使用 pgvector 类型）
     *
     * @param chunkId   分块ID
     * @param modelName 模型名称
     * @param vector    向量数据
     */
    public void saveVector(Long chunkId, String modelName, float[] vector) {
        String vectorStr = toVectorString(vector);
        jdbcTemplate.update(
                "INSERT INTO embedding (id, chunk_id, model_name, dimension, vector, create_time) " +
                        "VALUES (nextval('embedding_id_seq'), ?, ?, ?, ?::vector, NOW())",
                chunkId, modelName, vector.length, vectorStr);
    }

    /**
     * 余弦相似度检索 Top-K
     *
     * @param knowledgeId 知识库ID
     * @param queryVector 查询向量
     * @param topK        返回数量
     * @param threshold   相似度阈值
     * @return 检索结果（chunk_id, content, document_name, score）
     */
    public List<Map<String, Object>> searchSimilar(Long knowledgeId, float[] queryVector, int topK, double threshold) {
        String vectorStr = toVectorString(queryVector);
        String sql = """
                SELECT
                    c.id AS chunk_id,
                    c.content,
                    d.name AS document_name,
                    1 - (e.vector <=> ?::vector) AS score
                FROM embedding e
                JOIN chunk c ON e.chunk_id = c.id
                JOIN document d ON c.document_id = d.id
                WHERE c.knowledge_id = ?
                  AND d.deleted = 0
                ORDER BY e.vector <=> ?::vector
                LIMIT ?
                """;
        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, vectorStr, knowledgeId, vectorStr, topK);

        // 过滤低于阈值的结果
        return results.stream()
                .filter(row -> {
                    Object score = row.get("score");
                    return score != null && ((Number) score).doubleValue() >= threshold;
                })
                .toList();
    }

    /**
     * 删除指定知识库的所有向量
     */
    public void deleteByKnowledgeId(Long knowledgeId) {
        jdbcTemplate.update(
                "DELETE FROM embedding WHERE chunk_id IN (SELECT id FROM chunk WHERE knowledge_id = ?)",
                knowledgeId);
    }

    /**
     * 删除指定文档的所有向量
     */
    public void deleteByDocumentId(Long documentId) {
        jdbcTemplate.update(
                "DELETE FROM embedding WHERE chunk_id IN (SELECT id FROM chunk WHERE document_id = ?)",
                documentId);
    }

    private String toVectorString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
