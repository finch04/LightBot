package com.lightbot.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lightbot.entity.Embedding;
import com.lightbot.mapper.EmbeddingMapper;
import com.lightbot.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
