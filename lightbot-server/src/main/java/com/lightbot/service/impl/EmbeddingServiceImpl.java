package com.lightbot.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lightbot.entity.Embedding;
import com.lightbot.mapper.EmbeddingMapper;
import com.lightbot.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 向量服务实现类：负责向量存储和相似度检索
 * <p>pgvector 相关 SQL 操作已下沉到 EmbeddingMapper（@Select/@Insert/@Delete 注解）</p>
 *
 * @author finch
 * @since 2026-05-19
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingServiceImpl extends ServiceImpl<EmbeddingMapper, Embedding>
        implements EmbeddingService {

    private final EmbeddingMapper embeddingMapper;

    @Override
    public void saveVector(Long chunkId, String modelName, float[] vector) {
        long id = IdWorker.getId();
        String vectorStr = toVectorString(vector);
        embeddingMapper.insertVector(id, chunkId, modelName, vector.length, vectorStr);
    }

    /**
     * 批量存储向量（减少数据库往返次数）
     *
     * @param chunkIds  分块ID列表
     * @param modelName 模型名称
     * @param vectors   向量数据列表，与 chunkIds 一一对应
     */
    public void batchSaveVectors(List<Long> chunkIds, String modelName, List<float[]> vectors) {
        List<Map<String, Object>> batch = new ArrayList<>(chunkIds.size());
        for (int i = 0; i < chunkIds.size(); i++) {
            float[] vector = vectors.get(i);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", IdWorker.getId());
            row.put("chunkId", chunkIds.get(i));
            row.put("modelName", modelName);
            row.put("dimension", vector.length);
            row.put("vector", toVectorString(vector));
            batch.add(row);
        }
        embeddingMapper.batchInsertVectors(batch);
    }

    @Override
    public List<Map<String, Object>> searchSimilar(Long knowledgeId, float[] queryVector, int topK, double threshold) {
        String vectorStr = toVectorString(queryVector);
        List<Map<String, Object>> results = embeddingMapper.searchSimilar(vectorStr, knowledgeId, topK);

        // 过滤低于阈值的结果
        return results.stream()
                .filter(row -> {
                    Object score = row.get("score");
                    return score != null && ((Number) score).doubleValue() >= threshold;
                })
                .toList();
    }

    @Override
    public List<Map<String, Object>> searchSimilarSql(Long knowledgeId, float[] queryVector, int topK, double threshold) {
        String vectorStr = toVectorString(queryVector);
        return embeddingMapper.searchSimilarWithThreshold(vectorStr, knowledgeId, topK, threshold);
    }

    @Override
    public List<Map<String, Object>> searchSimilarRaw(Long knowledgeId, float[] queryVector, int topK) {
        String vectorStr = toVectorString(queryVector);
        return embeddingMapper.searchSimilar(vectorStr, knowledgeId, topK);
    }

    @Override
    public void deleteByKnowledgeId(Long knowledgeId) {
        embeddingMapper.deleteByKnowledgeId(knowledgeId);
    }

    @Override
    public void deleteByDocumentId(Long documentId) {
        embeddingMapper.deleteByDocumentId(documentId);
    }

    /**
     * 将float数组转换为pgvector可识别的字符串格式 "[0.1,0.2,...]"
     */
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
