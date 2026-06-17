package com.lightbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lightbot.entity.Chunk;
import com.lightbot.entity.Document;
import com.lightbot.entity.Embedding;
import com.lightbot.entity.Knowledge;
import com.lightbot.enums.KnowledgeType;
import com.lightbot.mapper.DocumentMapper;
import com.lightbot.mapper.EmbeddingMapper;
import com.lightbot.service.ChunkService;
import com.lightbot.service.EmbeddingService;
import com.lightbot.service.KnowledgeService;
import com.lightbot.util.GraphRetrievalUtil;
import com.lightbot.util.MilvusUtil;
import com.lightbot.util.RerankerUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 向量服务实现类：负责向量存储和相似度检索
 * <p>pgvector 相关 SQL 操作已下沉到 EmbeddingMapper（@Select/@Insert/@Delete 注解）</p>
 *
 * @author finch
 * @since 2026-05-19
 */
@Slf4j
@Service
public class EmbeddingServiceImpl extends ServiceImpl<EmbeddingMapper, Embedding>
        implements EmbeddingService {

    private final EmbeddingMapper embeddingMapper;
    private final MilvusUtil milvusUtil;
    private final DocumentMapper documentMapper;
    private final ChunkService chunkService;
    private final KnowledgeService knowledgeService;
    private final RerankerUtil rerankerUtil;
    private final GraphRetrievalUtil graphRetrievalUtil;

    private static final String SEARCH_MODE_VECTOR = "vector";
    private static final String SEARCH_MODE_KEYWORD = "keyword";
    private static final String SEARCH_MODE_HYBRID = "hybrid";

    /** Milvus 集合存在性缓存，避免每次检索前 RPC 调用 hasCollection */
    private final ConcurrentHashMap<Long, Boolean> collectionExistsCache = new ConcurrentHashMap<>();

    public EmbeddingServiceImpl(EmbeddingMapper embeddingMapper, MilvusUtil milvusUtil,
                                DocumentMapper documentMapper, ChunkService chunkService,
                                @Lazy KnowledgeService knowledgeService, RerankerUtil rerankerUtil,
                                GraphRetrievalUtil graphRetrievalUtil) {
        this.embeddingMapper = embeddingMapper;
        this.milvusUtil = milvusUtil;
        this.documentMapper = documentMapper;
        this.chunkService = chunkService;
        this.knowledgeService = knowledgeService;
        this.rerankerUtil = rerankerUtil;
        this.graphRetrievalUtil = graphRetrievalUtil;
    }

    @Override
    public void saveVector(Long chunkId, String modelName, float[] vector) {
        long id = IdWorker.getId();
        String vectorStr = toVectorString(vector);
        embeddingMapper.insertVector(id, chunkId, modelName, vector.length, vectorStr);
    }

    /**
     * 批量存储向量（减少数据库往返次数）
     * <p>默认走 pgvector，调用方无 knowledgeId 时使用此方法</p>
     */
    @Override
    public void batchSaveVectors(List<Long> chunkIds, String modelName, List<float[]> vectors) {
        batchSaveVectors(null, chunkIds, modelName, vectors);
    }

    /**
     * 批量存储向量（带 knowledgeId，支持 Milvus 路由）
     *
     * @param knowledgeId 知识库ID（为 null 时走 pgvector）
     * @param chunkIds    分块ID列表
     * @param modelName   模型名称
     * @param vectors     向量数据列表，与 chunkIds 一一对应
     */
    public void batchSaveVectors(Long knowledgeId, List<Long> chunkIds, String modelName, List<float[]> vectors) {
        // Milvus 路由
        if (shouldRouteToMilvus(knowledgeId)) {
            // 确保 Collection 存在（首次写入时自动创建，使用缓存避免重复 RPC）
            if (!hasCollectionCached(knowledgeId) && !vectors.isEmpty()) {
                int dimension = vectors.get(0).length;
                milvusUtil.createCollectionForKnowledge(knowledgeId, dimension);
                collectionExistsCache.put(knowledgeId, true);
            }
            List<Chunk> chunks = chunkService.list(new LambdaQueryWrapper<Chunk>()
                    .in(Chunk::getId, chunkIds));
            Map<Long, Chunk> chunkMap = chunks.stream()
                    .collect(Collectors.toMap(Chunk::getId, c -> c));
            List<Long> documentIds = new ArrayList<>(chunkIds.size());
            List<String> contents = new ArrayList<>(chunkIds.size());
            for (Long chunkId : chunkIds) {
                Chunk chunk = chunkMap.get(chunkId);
                documentIds.add(chunk != null ? chunk.getDocumentId() : 0L);
                contents.add(chunk != null ? chunk.getContent() : "");
            }
            milvusUtil.insertVectors(knowledgeId, chunkIds, documentIds, contents, vectors);
            log.info("[Embedding] Milvus 批量写入: knowledgeId={}, count={}", knowledgeId, chunkIds.size());
            return;
        }

        // pgvector（原有逻辑）
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
    @Transactional(readOnly = true)
    public List<Map<String, Object>> searchSimilarSql(Long knowledgeId, float[] queryVector, int topK, double threshold) {
        return searchSimilarSql(knowledgeId, queryVector, topK, threshold, null);
    }

    /**
     * 向量检索（支持 Milvus 路由 + search_mode 参数 + 图检索融合）
     *
     * @param knowledgeId 知识库ID
     * @param queryVector 查询向量
     * @param topK        返回数量
     * @param threshold   相似度阈值
     * @param queryParams 检索配置参数（search_mode/vector_weight/bm25_weight/bm25_top_k/use_graph_retrieval 等）
     * @return 检索结果（chunk_id, content, document_id, document_name, score）
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> searchSimilarSql(Long knowledgeId, float[] queryVector,
                                                       int topK, double threshold,
                                                       Map<String, Object> queryParams) {
        // 1. 常规检索（Milvus 或 pgvector）
        List<Map<String, Object>> results;
        if (shouldRouteToMilvus(knowledgeId)) {
            results = searchMilvus(knowledgeId, queryVector, topK, threshold, queryParams);
        } else {
            results = searchPgvector(knowledgeId, queryVector, topK, threshold, queryParams);
        }

        // 2. 图检索（可选）：与常规结果 RRF 融合
        results = applyGraphRetrieval(knowledgeId, queryVector, results, topK, queryParams);

        // 3. Reranker（可选）
        results = applyReranker(results, queryParams, topK);

        return results;
    }

    /**
     * pgvector 检索路由：根据 search_mode 分发
     */
    private List<Map<String, Object>> searchPgvector(Long knowledgeId, float[] queryVector,
                                                      int topK, double threshold,
                                                      Map<String, Object> queryParams) {
        // 设置 ef_search 提升召回率（SET LOCAL 仅对当前事务生效）
        embeddingMapper.setHnswEfSearch();
        String searchMode = queryParams != null && queryParams.get("search_mode") instanceof String s
                ? s : SEARCH_MODE_VECTOR;

        return switch (searchMode) {
            case SEARCH_MODE_KEYWORD -> {
                String queryText = getQueryParam(queryParams, "query_text", "");
                yield embeddingMapper.searchByFullText(queryText, knowledgeId, topK);
            }
            case SEARCH_MODE_HYBRID -> searchPgHybrid(knowledgeId, queryVector, topK, threshold, queryParams);
            default -> {
                String vectorStr = toVectorString(queryVector);
                yield embeddingMapper.searchSimilarWithThreshold(vectorStr, knowledgeId, topK, threshold);
            }
        };
    }

    @Override
    public List<Map<String, Object>> searchSimilarRaw(Long knowledgeId, float[] queryVector, int topK) {
        String vectorStr = toVectorString(queryVector);
        return embeddingMapper.searchSimilar(vectorStr, knowledgeId, topK);
    }

    @Override
    public void deleteByKnowledgeId(Long knowledgeId) {
        embeddingMapper.deleteByKnowledgeId(knowledgeId);
        if (shouldRouteToMilvus(knowledgeId)) {
            milvusUtil.dropCollection(knowledgeId);
            collectionExistsCache.remove(knowledgeId);
        }
    }

    @Override
    public void deleteByDocumentId(Long documentId) {
        embeddingMapper.deleteByDocumentId(documentId);
        // Milvus 侧：查询文档获取 knowledgeId，判断是否需要清理
        Document doc = documentMapper.selectById(documentId);
        if (doc != null && shouldRouteToMilvus(doc.getKnowledgeId())) {
            milvusUtil.deleteByDocumentId(doc.getKnowledgeId(), documentId);
        }
    }

    /**
     * 删除指定文档的向量（调用方已知 knowledgeId 和 type，避免重复查询）
     *
     * @param documentId  文档ID
     * @param knowledgeId 知识库ID
     * @param type        知识库类型
     */
    public void deleteByDocumentId(Long documentId, Long knowledgeId, KnowledgeType type) {
        embeddingMapper.deleteByDocumentId(documentId);
        if (type == KnowledgeType.MILVUS && milvusUtil.isAvailable()) {
            milvusUtil.deleteByDocumentId(knowledgeId, documentId);
        }
    }

    /**
     * pgvector 混合检索：向量检索 + 全文检索 → RRF 融合
     */
    private List<Map<String, Object>> searchPgHybrid(Long knowledgeId, float[] queryVector,
                                                      int topK, double threshold,
                                                      Map<String, Object> params) {
        String queryText = getQueryParam(params, "query_text", "");
        int recallTopK = Math.max(topK * 3, getIntParam(params, "recall_top_k", 30));
        float vectorWeight = getFloatParam(params, "vector_weight", 0.7f);
        float keywordWeight = getFloatParam(params, "keyword_weight", 0.3f);

        String vectorStr = toVectorString(queryVector);
        List<Map<String, Object>> vectorResults = embeddingMapper.searchSimilarWithThreshold(
                vectorStr, knowledgeId, recallTopK, 0);
        List<Map<String, Object>> keywordResults = embeddingMapper.searchByFullText(
                queryText, knowledgeId, recallTopK);

        return rrfFusion(vectorResults, keywordResults, vectorWeight, keywordWeight, topK, threshold);
    }

    /**
     * RRF（Reciprocal Rank Fusion）融合排序
     *
     * @param vectorResults  向量检索结果
     * @param keywordResults 全文检索结果
     * @param vectorWeight   向量结果权重
     * @param keywordWeight  关键词结果权重
     * @param topK           返回数量
     * @param threshold      RRF 分数阈值（低于此值不返回，0 表示不过滤）
     * @return 融合后的结果列表
     */
    private List<Map<String, Object>> rrfFusion(List<Map<String, Object>> vectorResults,
                                                 List<Map<String, Object>> keywordResults,
                                                 float vectorWeight, float keywordWeight,
                                                 int topK, double threshold) {
        int k = 60; // RRF 常量
        // chunkId → [rrfScore, bestOriginalScore, sourceRow]
        Map<Long, double[]> scoreMap = new LinkedHashMap<>();
        Map<Long, Map<String, Object>> sourceMap = new LinkedHashMap<>();

        for (int i = 0; i < vectorResults.size(); i++) {
            Map<String, Object> row = vectorResults.get(i);
            long chunkId = getChunkId(row);
            if (chunkId <= 0) continue;
            double rrfScore = vectorWeight / (k + i + 1);
            double originalScore = getScore(row);
            scoreMap.merge(chunkId, new double[]{rrfScore, originalScore},
                    (a, b) -> new double[]{a[0] + b[0], Math.max(a[1], b[1])});
            sourceMap.putIfAbsent(chunkId, row);
        }

        for (int i = 0; i < keywordResults.size(); i++) {
            Map<String, Object> row = keywordResults.get(i);
            long chunkId = getChunkId(row);
            if (chunkId <= 0) continue;
            double rrfScore = keywordWeight / (k + i + 1);
            double originalScore = getScore(row);
            scoreMap.merge(chunkId, new double[]{rrfScore, originalScore},
                    (a, b) -> new double[]{a[0] + b[0], Math.max(a[1], b[1])});
            sourceMap.putIfAbsent(chunkId, row);
        }

        return scoreMap.entrySet().stream()
                .filter(e -> threshold <= 0 || e.getValue()[0] >= threshold)
                .sorted((a, b) -> Double.compare(b.getValue()[0], a.getValue()[0]))
                .limit(topK)
                .map(e -> {
                    Map<String, Object> row = new LinkedHashMap<>(sourceMap.get(e.getKey()));
                    row.put("score", e.getValue()[0]);
                    return row;
                })
                .toList();
    }

    private long getChunkId(Map<String, Object> row) {
        Object id = row.get("chunk_id");
        if (id instanceof Number n) return n.longValue();
        return 0;
    }

    private double getScore(Map<String, Object> row) {
        Object score = row.get("score");
        return score instanceof Number n ? n.doubleValue() : 0;
    }

    private String getQueryParam(Map<String, Object> params, String key, String defaultValue) {
        if (params != null && params.get(key) instanceof String s) {
            return s;
        }
        return defaultValue;
    }

    private int getIntParam(Map<String, Object> params, String key, int defaultValue) {
        if (params != null && params.get(key) instanceof Number n) {
            return n.intValue();
        }
        return defaultValue;
    }

    private float getFloatParam(Map<String, Object> params, String key, float defaultValue) {
        if (params != null && params.get(key) instanceof Number n) {
            return n.floatValue();
        }
        return defaultValue;
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

    /**
     * 判断是否应路由到 Milvus
     */
    private boolean shouldRouteToMilvus(Long knowledgeId) {
        if (knowledgeId == null || !milvusUtil.isAvailable()) {
            return false;
        }
        Knowledge knowledge = knowledgeService.getById(knowledgeId);
        return knowledge != null && knowledge.getType() == KnowledgeType.MILVUS;
    }

    /**
     * 带缓存的 Collection 存在性检查（避免每次检索前 RPC 调用）
     */
    private boolean hasCollectionCached(Long knowledgeId) {
        return collectionExistsCache.computeIfAbsent(knowledgeId, milvusUtil::hasCollection);
    }

    /**
     * Milvus 检索路由：根据 search_mode 分发到 vector/keyword/hybrid 检索
     */
    private List<Map<String, Object>> searchMilvus(Long knowledgeId, float[] queryVector,
                                                    int topK, double threshold,
                                                    Map<String, Object> queryParams) {
        // Collection 不存在时直接返回空结果（使用缓存避免每次 RPC）
        if (!hasCollectionCached(knowledgeId)) {
            log.info("[Embedding] Milvus Collection 不存在, knowledgeId={}", knowledgeId);
            return List.of();
        }

        String searchMode = SEARCH_MODE_VECTOR;
        if (queryParams != null && queryParams.get("search_mode") instanceof String s) {
            searchMode = s;
        }

        List<Map<String, Object>> results;
        switch (searchMode) {
            case SEARCH_MODE_KEYWORD -> {
                String queryText = queryParams != null && queryParams.get("query_text") instanceof String s
                        ? s : "";
                results = milvusUtil.searchKeyword(knowledgeId, queryText, topK);
            }
            case SEARCH_MODE_HYBRID -> {
                String queryText = queryParams != null && queryParams.get("query_text") instanceof String s
                        ? s : "";
                float vectorWeight = queryParams != null && queryParams.get("vector_weight") instanceof Number n
                        ? n.floatValue() : 0.7f;
                float bm25Weight = queryParams != null && queryParams.get("bm25_weight") instanceof Number n
                        ? n.floatValue() : 0.3f;
                int bm25TopK = queryParams != null && queryParams.get("bm25_top_k") instanceof Number n
                        ? n.intValue() : topK * 3;
                results = milvusUtil.searchHybrid(knowledgeId, queryText, queryVector,
                        topK, vectorWeight, bm25Weight, bm25TopK);
            }
            default -> {
                results = milvusUtil.searchVector(knowledgeId, queryVector, topK, threshold);
            }
        }

        // 补充 document_name 字段（Milvus 只返回 document_id）
        enrichWithDocumentNames(results);

        // Reranker（可选）
        results = applyReranker(results, queryParams, topK);

        return results;
    }

    /**
     * 为 Milvus 检索结果补充 document_name 字段
     */
    private void enrichWithDocumentNames(List<Map<String, Object>> results) {
        List<Long> docIds = results.stream()
                .map(r -> r.get("document_id"))
                .filter(id -> id instanceof Long)
                .map(id -> (Long) id)
                .distinct()
                .toList();
        if (docIds.isEmpty()) {
            return;
        }
        Map<Long, String> nameMap = documentMapper.selectBatchIds(docIds).stream()
                .collect(Collectors.toMap(Document::getId, Document::getName));
        for (Map<String, Object> row : results) {
            Object docId = row.get("document_id");
            if (docId instanceof Long id) {
                row.put("document_name", nameMap.getOrDefault(id, "未知文档"));
            }
        }
    }

    /**
     * 应用 Reranker（如果启用）
     *
     * @param results     检索结果
     * @param queryParams 查询参数
     * @param topK        最终返回数量
     * @return 重排序后的结果
     */
    private List<Map<String, Object>> applyReranker(List<Map<String, Object>> results,
                                                      Map<String, Object> queryParams,
                                                      int topK) {
        boolean useReranker = queryParams != null
                && Boolean.TRUE.equals(queryParams.get("use_reranker"));
        if (!useReranker || results.isEmpty()) {
            return results;
        }
        String queryText = getQueryParam(queryParams, "query_text", "");
        if (queryText.isBlank()) {
            return results;
        }
        String rerankerModel = getQueryParam(queryParams, "reranker_model", "");
        return rerankerUtil.rerank(queryText, results, topK, rerankerModel);
    }

    /**
     * 应用图检索（如果启用）：图检索结果与常规结果 RRF 融合
     *
     * @param knowledgeId 知识库ID
     * @param queryVector 查询向量
     * @param results     常规检索结果
     * @param topK        最终返回数量
     * @param queryParams 查询参数
     * @return 融合后的结果
     */
    private List<Map<String, Object>> applyGraphRetrieval(Long knowledgeId, float[] queryVector,
                                                           List<Map<String, Object>> results,
                                                           int topK, Map<String, Object> queryParams) {
        boolean useGraph = queryParams != null
                && Boolean.TRUE.equals(queryParams.get("use_graph_retrieval"));
        if (!useGraph || !shouldRouteToMilvus(knowledgeId)) {
            return results;
        }

        int graphEntityTopK = getIntParam(queryParams, "graph_entity_top_k", 10);
        int graphTripleTopK = getIntParam(queryParams, "graph_triple_top_k", 10);
        int graphMaxNodes = getIntParam(queryParams, "graph_max_nodes", 100);
        int graphTopK = getIntParam(queryParams, "graph_top_k", 5);
        double graphWeight = getFloatParam(queryParams, "graph_weight", 0.3f);
        double pprDamping = getFloatParam(queryParams, "ppr_damping", 0.85f);

        try {
            List<Map<String, Object>> graphResults = graphRetrievalUtil.search(
                    knowledgeId, queryVector, graphEntityTopK, graphTripleTopK,
                    graphMaxNodes, graphTopK, pprDamping);

            if (graphResults.isEmpty()) {
                return results;
            }

            // 为图检索结果分配唯一负数 chunk_id，避免与常规结果冲突
            for (int i = 0; i < graphResults.size(); i++) {
                graphResults.get(i).put("chunk_id", -(i + 1L));
            }

            // RRF 融合：常规结果权重=1.0，图检索结果权重=graphWeight
            return rrfFusion(results, graphResults, 1.0f, (float) graphWeight, topK, 0);
        } catch (Exception e) {
            log.error("[Embedding] 图检索异常，返回常规结果: {}", e.getMessage(), e);
            return results;
        }
    }
}
