package com.lightbot.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.milvus.common.clientenum.FunctionType;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.vector.request.*;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.request.data.SparseFloatVec;
import io.milvus.v2.service.vector.request.ranker.WeightedRanker;
import io.milvus.v2.service.vector.response.SearchResp;
import io.milvus.v2.service.collection.request.*;
import io.milvus.v2.service.collection.response.DescribeCollectionResp;
import io.milvus.v2.service.index.request.CreateIndexReq;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.*;

/**
 * Milvus 向量数据库工具类
 * <p>封装 MilvusClientV2 操作，供 EmbeddingServiceImpl 调用</p>
 * <p>懒加载：首次使用时才初始化客户端，无 Milvus 服务时不影响项目启动</p>
 *
 * @author finch
 * @since 2026-06-15
 */
@Slf4j
@Component
public class MilvusUtil {

    private static final Gson GSON = new Gson();

    private final boolean enabled;
    private final String uri;
    private final String token;
    private final int connectTimeoutMs;
    private final int waitTimeoutMs;

    private volatile MilvusClientV2 client;
    private volatile boolean initialized = false;
    private volatile boolean available = false;

    public MilvusUtil(
            @Value("${lightbot.milvus.enabled:false}") boolean enabled,
            @Value("${lightbot.milvus.uri:http://localhost:19530}") String uri,
            @Value("${lightbot.milvus.token:}") String token,
            @Value("${lightbot.milvus.connect-timeout-ms:10000}") int connectTimeoutMs,
            @Value("${lightbot.milvus.wait-timeout-ms:30000}") int waitTimeoutMs) {
        this.enabled = enabled;
        this.uri = uri;
        this.token = token;
        this.connectTimeoutMs = connectTimeoutMs;
        this.waitTimeoutMs = waitTimeoutMs;
        log.info("[Milvus] 配置已加载, enabled={}, uri={}（懒初始化，首次使用时连接）", enabled, uri);
    }

    /**
     * 获取客户端（懒初始化）
     */
    private MilvusClientV2 getClient() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    try {
                        ConnectConfig config = ConnectConfig.builder()
                                .uri(uri)
                                .token(token)
                                .connectTimeoutMs(connectTimeoutMs)
                                .keepAliveTimeMs(waitTimeoutMs)
                                .build();
                        this.client = new MilvusClientV2(config);
                        this.available = true;
                        log.info("[Milvus] 连接成功, uri={}", uri);
                    } catch (Exception e) {
                        log.warn("[Milvus] 连接失败, uri={}, error={}", uri, e.getMessage());
                        this.available = false;
                    }
                    this.initialized = true;
                }
            }
        }
        return client;
    }

    /**
     * 判断 Milvus 是否可用
     */
    public boolean isAvailable() {
        return enabled && getClient() != null && available;
    }

    /**
     * 判断 Collection 是否存在
     */
    public boolean hasCollection(Long knowledgeId) {
        String collName = collectionName(knowledgeId);
        try {
            DescribeCollectionResp desc = getClient().describeCollection(
                    DescribeCollectionReq.builder().collectionName(collName).build());
            return desc != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取知识库对应的 Collection 名称
     */
    public static String collectionName(Long knowledgeId) {
        return "kb_" + knowledgeId;
    }

    /**
     * 获取 Entity Collection 名称
     */
    public static String entityCollectionName(Long knowledgeId) {
        return "kb_" + knowledgeId + "_entity";
    }

    /**
     * 获取 Triple Collection 名称
     */
    public static String tripleCollectionName(Long knowledgeId) {
        return "kb_" + knowledgeId + "_triple";
    }

    // ==================== Collection 管理 ====================

    /**
     * 为知识库创建 Collection
     * <p>包含向量字段（HNSW+COSINE）和 BM25 稀疏向量字段</p>
     *
     * @param knowledgeId 知识库ID
     * @param dimension   向量维度（由 Embedding 模型决定）
     */
    public void createCollectionForKnowledge(Long knowledgeId, int dimension) {
        String collName = collectionName(knowledgeId);

        // 检查是否已存在
        try {
            DescribeCollectionResp desc = getClient().describeCollection(
                    DescribeCollectionReq.builder().collectionName(collName).build());
            if (desc != null) {
                log.info("[Milvus] Collection 已存在, skipping: {}", collName);
                return;
            }
        } catch (Exception e) {
            // 不存在则继续创建
        }

        // 1. 定义字段
        List<CreateCollectionReq.FieldSchema> fieldSchemas = new ArrayList<>();

        fieldSchemas.add(CreateCollectionReq.FieldSchema.builder()
                .name("id").dataType(DataType.VarChar)
                .maxLength(64).isPrimaryKey(true).autoID(false).build());

        fieldSchemas.add(CreateCollectionReq.FieldSchema.builder()
                .name("document_id").dataType(DataType.VarChar)
                .maxLength(64).build());

        fieldSchemas.add(CreateCollectionReq.FieldSchema.builder()
                .name("content").dataType(DataType.VarChar)
                .maxLength(65535)
                .enableAnalyzer(true)
                .analyzerParams(Map.of("type", "chinese"))
                .build());

        fieldSchemas.add(CreateCollectionReq.FieldSchema.builder()
                .name("embedding").dataType(DataType.FloatVector)
                .dimension(dimension).build());

        fieldSchemas.add(CreateCollectionReq.FieldSchema.builder()
                .name("content_sparse").dataType(DataType.SparseFloatVector)
                .build());

        // 2. 创建 BM25 Function（content → content_sparse 自动转换）
        CreateCollectionReq.Function bm25Function = CreateCollectionReq.Function.builder()
                .functionType(FunctionType.BM25)
                .name("bm25_sparse")
                .inputFieldNames(List.of("content"))
                .outputFieldNames(List.of("content_sparse"))
                .build();

        // 3. 构建 CollectionSchema（含字段 + Function）
        CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder()
                .fieldSchemaList(fieldSchemas)
                .functionList(List.of(bm25Function))
                .build();

        // 4. 创建 Collection
        CreateCollectionReq createReq = CreateCollectionReq.builder()
                .collectionName(collName)
                .collectionSchema(schema)
                .build();
        getClient().createCollection(createReq);

        // 5. 创建向量索引（HNSW + COSINE）
        IndexParam vectorIndex = IndexParam.builder()
                .fieldName("embedding")
                .indexType(IndexParam.IndexType.HNSW)
                .metricType(IndexParam.MetricType.COSINE)
                .extraParams(Map.of("M", 16, "efConstruction", 200))
                .build();

        // 6. 创建 BM25 稀疏向量索引
        IndexParam sparseIndex = IndexParam.builder()
                .fieldName("content_sparse")
                .indexType(IndexParam.IndexType.SPARSE_INVERTED_INDEX)
                .metricType(IndexParam.MetricType.BM25)
                .extraParams(Map.of("drop_ratio_search", 0.0))
                .build();

        CreateIndexReq indexReq = CreateIndexReq.builder()
                .collectionName(collName)
                .indexParams(List.of(vectorIndex, sparseIndex))
                .build();
        getClient().createIndex(indexReq);

        // 7. 加载 Collection 到内存
        getClient().loadCollection(LoadCollectionReq.builder()
                .collectionName(collName).build());

        log.info("[Milvus] Collection 创建成功: {}, dimension={}", collName, dimension);
    }

    /**
     * 删除知识库的 Collection
     */
    public void dropCollection(Long knowledgeId) {
        String collName = collectionName(knowledgeId);
        try {
            getClient().dropCollection(DropCollectionReq.builder()
                    .collectionName(collName).build());
            log.info("[Milvus] Collection 已删除: {}", collName);
        } catch (Exception e) {
            log.warn("[Milvus] 删除 Collection 失败: {}, error={}", collName, e.getMessage());
        }
    }

    // ==================== 图检索 Collection 管理 ====================

    /**
     * 判断 Entity Collection 是否存在
     */
    public boolean hasEntityCollection(Long knowledgeId) {
        return hasCollectionByName(entityCollectionName(knowledgeId));
    }

    /**
     * 判断 Triple Collection 是否存在
     */
    public boolean hasTripleCollection(Long knowledgeId) {
        return hasCollectionByName(tripleCollectionName(knowledgeId));
    }

    private boolean hasCollectionByName(String collName) {
        try {
            DescribeCollectionResp desc = getClient().describeCollection(
                    DescribeCollectionReq.builder().collectionName(collName).build());
            return desc != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 为知识库创建 Entity Collection
     * <p>字段: id(VarChar PK), content(VarChar+BM25), embedding(FloatVector), content_sparse(SparseFloatVector)</p>
     *
     * @param knowledgeId 知识库ID
     * @param dimension   向量维度
     */
    public void createEntityCollection(Long knowledgeId, int dimension) {
        String collName = entityCollectionName(knowledgeId);
        createGraphCollection(collName, dimension, false);
    }

    /**
     * 为知识库创建 Triple Collection
     * <p>字段: id(VarChar PK), content(VarChar+BM25), source_id(VarChar), target_id(VarChar),
     * embedding(FloatVector), content_sparse(SparseFloatVector)</p>
     *
     * @param knowledgeId 知识库ID
     * @param dimension   向量维度
     */
    public void createTripleCollection(Long knowledgeId, int dimension) {
        String collName = tripleCollectionName(knowledgeId);
        createGraphCollection(collName, dimension, true);
    }

    /**
     * 创建图检索 Collection 的通用逻辑
     *
     * @param collName    Collection 名称
     * @param dimension   向量维度
     * @param isTriple    是否为 Triple Collection（Triple 多 source_id、target_id 字段）
     */
    private void createGraphCollection(String collName, int dimension, boolean isTriple) {
        // 检查是否已存在
        if (hasCollectionByName(collName)) {
            log.info("[Milvus] 图 Collection 已存在, skipping: {}", collName);
            return;
        }

        List<CreateCollectionReq.FieldSchema> fieldSchemas = new ArrayList<>();

        fieldSchemas.add(CreateCollectionReq.FieldSchema.builder()
                .name("id").dataType(DataType.VarChar)
                .maxLength(64).isPrimaryKey(true).autoID(false).build());

        fieldSchemas.add(CreateCollectionReq.FieldSchema.builder()
                .name("content").dataType(DataType.VarChar)
                .maxLength(65535)
                .enableAnalyzer(true)
                .analyzerParams(Map.of("type", "chinese"))
                .build());

        if (isTriple) {
            fieldSchemas.add(CreateCollectionReq.FieldSchema.builder()
                    .name("source_id").dataType(DataType.VarChar).maxLength(64).build());
            fieldSchemas.add(CreateCollectionReq.FieldSchema.builder()
                    .name("target_id").dataType(DataType.VarChar).maxLength(64).build());
        }

        fieldSchemas.add(CreateCollectionReq.FieldSchema.builder()
                .name("embedding").dataType(DataType.FloatVector)
                .dimension(dimension).build());

        fieldSchemas.add(CreateCollectionReq.FieldSchema.builder()
                .name("content_sparse").dataType(DataType.SparseFloatVector)
                .build());

        CreateCollectionReq.Function bm25Function = CreateCollectionReq.Function.builder()
                .functionType(FunctionType.BM25)
                .name("bm25_sparse")
                .inputFieldNames(List.of("content"))
                .outputFieldNames(List.of("content_sparse"))
                .build();

        CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder()
                .fieldSchemaList(fieldSchemas)
                .functionList(List.of(bm25Function))
                .build();

        getClient().createCollection(CreateCollectionReq.builder()
                .collectionName(collName).collectionSchema(schema).build());

        IndexParam vectorIndex = IndexParam.builder()
                .fieldName("embedding")
                .indexType(IndexParam.IndexType.HNSW)
                .metricType(IndexParam.MetricType.COSINE)
                .extraParams(Map.of("M", 16, "efConstruction", 200))
                .build();

        IndexParam sparseIndex = IndexParam.builder()
                .fieldName("content_sparse")
                .indexType(IndexParam.IndexType.SPARSE_INVERTED_INDEX)
                .metricType(IndexParam.MetricType.BM25)
                .extraParams(Map.of("drop_ratio_search", 0.0))
                .build();

        getClient().createIndex(CreateIndexReq.builder()
                .collectionName(collName)
                .indexParams(List.of(vectorIndex, sparseIndex))
                .build());

        getClient().loadCollection(LoadCollectionReq.builder()
                .collectionName(collName).build());

        log.info("[Milvus] 图 Collection 创建成功: {}, dimension={}", collName, dimension);
    }

    // ==================== 图检索向量写入 ====================

    /**
     * 批量写入 Entity 向量
     *
     * @param knowledgeId 知识库ID
     * @param entityIds   实体ID列表
     * @param contents    实体描述列表
     * @param vectors     向量列表
     */
    public void insertEntityVectors(Long knowledgeId, List<Long> entityIds,
                                     List<String> contents, List<float[]> vectors) {
        String collName = entityCollectionName(knowledgeId);
        List<JsonObject> rows = new ArrayList<>(entityIds.size());
        for (int i = 0; i < entityIds.size(); i++) {
            JsonObject row = new JsonObject();
            row.addProperty("id", String.valueOf(entityIds.get(i)));
            row.addProperty("content", contents.get(i));
            row.add("embedding", GSON.toJsonTree(vectors.get(i)));
            rows.add(row);
        }
        getClient().insert(InsertReq.builder().collectionName(collName).data(rows).build());
        log.info("[Milvus] Entity 向量写入: collection={}, count={}", collName, entityIds.size());
    }

    /**
     * 批量写入 Triple 向量
     *
     * @param knowledgeId 知识库ID
     * @param tripleIds   三元组ID列表
     * @param contents    三元组描述列表
     * @param sourceIds   源实体ID列表
     * @param targetIds   目标实体ID列表
     * @param vectors     向量列表
     */
    public void insertTripleVectors(Long knowledgeId, List<Long> tripleIds,
                                     List<String> contents, List<Long> sourceIds,
                                     List<Long> targetIds, List<float[]> vectors) {
        String collName = tripleCollectionName(knowledgeId);
        List<JsonObject> rows = new ArrayList<>(tripleIds.size());
        for (int i = 0; i < tripleIds.size(); i++) {
            JsonObject row = new JsonObject();
            row.addProperty("id", String.valueOf(tripleIds.get(i)));
            row.addProperty("content", contents.get(i));
            row.addProperty("source_id", String.valueOf(sourceIds.get(i)));
            row.addProperty("target_id", String.valueOf(targetIds.get(i)));
            row.add("embedding", GSON.toJsonTree(vectors.get(i)));
            rows.add(row);
        }
        getClient().insert(InsertReq.builder().collectionName(collName).data(rows).build());
        log.info("[Milvus] Triple 向量写入: collection={}, count={}", collName, tripleIds.size());
    }

    // ==================== 图检索向量搜索 ====================

    /**
     * Entity 向量检索
     *
     * @param knowledgeId 知识库ID
     * @param queryVector 查询向量
     * @param topK        返回数量
     * @return 检索结果（id, content, score）
     */
    public List<Map<String, Object>> searchEntities(Long knowledgeId, float[] queryVector, int topK) {
        String collName = entityCollectionName(knowledgeId);
        if (!hasCollectionByName(collName)) {
            return List.of();
        }

        SearchReq req = SearchReq.builder()
                .collectionName(collName)
                .data(List.of(new FloatVec(queryVector)))
                .annsField("embedding")
                .topK(topK)
                .metricType(IndexParam.MetricType.COSINE)
                .outputFields(List.of("id", "content"))
                .build();

        SearchResp resp = getClient().search(req);
        return convertGraphResults(resp);
    }

    /**
     * Triple 向量检索
     *
     * @param knowledgeId 知识库ID
     * @param queryVector 查询向量
     * @param topK        返回数量
     * @return 检索结果（id, content, source_id, target_id, score）
     */
    public List<Map<String, Object>> searchTriples(Long knowledgeId, float[] queryVector, int topK) {
        String collName = tripleCollectionName(knowledgeId);
        if (!hasCollectionByName(collName)) {
            return List.of();
        }

        SearchReq req = SearchReq.builder()
                .collectionName(collName)
                .data(List.of(new FloatVec(queryVector)))
                .annsField("embedding")
                .topK(topK)
                .metricType(IndexParam.MetricType.COSINE)
                .outputFields(List.of("id", "content", "source_id", "target_id"))
                .build();

        SearchResp resp = getClient().search(req);
        return convertGraphResults(resp);
    }

    /**
     * 删除知识库的图检索 Collections（Entity + Triple）
     */
    public void dropGraphCollections(Long knowledgeId) {
        dropByName(entityCollectionName(knowledgeId));
        dropByName(tripleCollectionName(knowledgeId));
    }

    private void dropByName(String collName) {
        try {
            getClient().dropCollection(DropCollectionReq.builder()
                    .collectionName(collName).build());
            log.info("[Milvus] 图 Collection 已删除: {}", collName);
        } catch (Exception e) {
            log.warn("[Milvus] 删除图 Collection 失败: {}, error={}", collName, e.getMessage());
        }
    }

    /**
     * 将图检索 SearchResp 转换为统一的 Map 格式
     */
    private List<Map<String, Object>> convertGraphResults(SearchResp resp) {
        List<Map<String, Object>> results = new ArrayList<>();
        if (resp == null || resp.getSearchResults() == null) {
            return results;
        }
        for (List<SearchResp.SearchResult> rowList : resp.getSearchResults()) {
            for (SearchResp.SearchResult row : rowList) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", Long.parseLong((String) row.getEntity().get("id")));
                map.put("content", row.getEntity().get("content"));
                map.put("score", row.getScore());
                // Triple Collection 额外字段
                Object sourceId = row.getEntity().get("source_id");
                Object targetId = row.getEntity().get("target_id");
                if (sourceId != null) {
                    map.put("source_id", Long.parseLong((String) sourceId));
                }
                if (targetId != null) {
                    map.put("target_id", Long.parseLong((String) targetId));
                }
                results.add(map);
            }
        }
        return results;
    }

    // ==================== 向量写入 ====================

    /**
     * 批量写入向量和内容到 Milvus
     *
     * @param knowledgeId 知识库ID
     * @param chunkIds    分块ID列表
     * @param documentIds 文档ID列表（与 chunkIds 一一对应）
     * @param contents    文本内容列表（与 chunkIds 一一对应）
     * @param vectors     向量数据列表（与 chunkIds 一一对应）
     */
    public void insertVectors(Long knowledgeId, List<Long> chunkIds,
                              List<Long> documentIds, List<String> contents,
                              List<float[]> vectors) {
        String collName = collectionName(knowledgeId);

        // 2.6.13 SDK 使用 List<JsonObject> 格式
        List<JsonObject> rows = new ArrayList<>(chunkIds.size());
        for (int i = 0; i < chunkIds.size(); i++) {
            JsonObject row = new JsonObject();
            row.addProperty("id", String.valueOf(chunkIds.get(i)));
            row.addProperty("document_id", String.valueOf(documentIds.get(i)));
            row.addProperty("content", contents.get(i));
            row.add("embedding", GSON.toJsonTree(vectors.get(i)));
            rows.add(row);
        }

        InsertReq insertReq = InsertReq.builder()
                .collectionName(collName)
                .data(rows)
                .build();
        getClient().insert(insertReq);
    }

    /**
     * 按文档ID删除 Milvus 中的数据
     */
    public void deleteByDocumentId(Long knowledgeId, Long documentId) {
        String collName = collectionName(knowledgeId);
        String expr = "document_id == \"" + documentId + "\"";
        try {
            getClient().delete(DeleteReq.builder()
                    .collectionName(collName)
                    .filter(expr)
                    .build());
            log.info("[Milvus] 按文档删除向量: collection={}, documentId={}", collName, documentId);
        } catch (Exception e) {
            log.warn("[Milvus] 按文档删除失败: collection={}, documentId={}, error={}",
                    collName, documentId, e.getMessage());
        }
    }

    // ==================== 检索 ====================

    /**
     * 向量检索（COSINE 相似度）
     *
     * @param knowledgeId 知识库ID
     * @param queryVector 查询向量
     * @param topK        返回数量
     * @param threshold   相似度阈值（低于此值不返回）
     * @return 检索结果列表，每项包含 chunk_id, content, document_id, score
     */
    public List<Map<String, Object>> searchVector(Long knowledgeId, float[] queryVector,
                                                   int topK, double threshold) {
        String collName = collectionName(knowledgeId);

        SearchReq req = SearchReq.builder()
                .collectionName(collName)
                .data(List.of(new FloatVec(queryVector)))
                .annsField("embedding")
                .topK(topK)
                .metricType(IndexParam.MetricType.COSINE)
                .outputFields(List.of("id", "document_id", "content"))
                .build();

        SearchResp resp = getClient().search(req);
        return convertResults(resp, threshold);
    }

    /**
     * BM25 关键词检索
     *
     * @param knowledgeId 知识库ID
     * @param queryText   查询文本
     * @param topK        返回数量
     * @return 检索结果列表
     */
    public List<Map<String, Object>> searchKeyword(Long knowledgeId, String queryText, int topK) {
        String collName = collectionName(knowledgeId);

        SearchReq req = SearchReq.builder()
                .collectionName(collName)
                .data(List.of(new SparseFloatVec(sparseFromString(queryText))))
                .annsField("content_sparse")
                .topK(topK)
                .metricType(IndexParam.MetricType.BM25)
                .outputFields(List.of("id", "document_id", "content"))
                .build();

        SearchResp resp = getClient().search(req);
        return convertResults(resp, 0);
    }

    /**
     * 混合检索（向量 + BM25，加权融合）
     *
     * @param knowledgeId  知识库ID
     * @param queryText    查询文本
     * @param queryVector  查询向量
     * @param topK         最终返回数量
     * @param vectorWeight 向量权重
     * @param bm25Weight   BM25 权重
     * @param bm25TopK     BM25 候选数量
     * @return 检索结果列表
     */
    public List<Map<String, Object>> searchHybrid(Long knowledgeId, String queryText,
                                                   float[] queryVector, int topK,
                                                   float vectorWeight, float bm25Weight,
                                                   int bm25TopK) {
        String collName = collectionName(knowledgeId);

        // 向量检索请求
        AnnSearchReq vectorReq = AnnSearchReq.builder()
                .vectorFieldName("embedding")
                .vectors(List.of(new FloatVec(queryVector)))
                .topK(bm25TopK)
                .metricType(IndexParam.MetricType.COSINE)
                .build();

        // BM25 检索请求
        AnnSearchReq bm25Req = AnnSearchReq.builder()
                .vectorFieldName("content_sparse")
                .vectors(List.of(new SparseFloatVec(sparseFromString(queryText))))
                .topK(bm25TopK)
                .metricType(IndexParam.MetricType.BM25)
                .build();

        // 加权融合排序器
        WeightedRanker ranker = WeightedRanker.builder()
                .weights(List.of(vectorWeight, bm25Weight))
                .build();

        HybridSearchReq req = HybridSearchReq.builder()
                .collectionName(collName)
                .searchRequests(List.of(vectorReq, bm25Req))
                .ranker(ranker)
                .topK(topK)
                .outFields(List.of("id", "document_id", "content"))
                .build();

        SearchResp resp = getClient().hybridSearch(req);
        return convertResults(resp, 0);
    }

    // ==================== 内部工具方法 ====================

    /**
     * 将 Milvus SearchResp 转换为统一的 Map 格式
     */
    private List<Map<String, Object>> convertResults(SearchResp resp, double threshold) {
        List<Map<String, Object>> results = new ArrayList<>();
        if (resp == null || resp.getSearchResults() == null) {
            return results;
        }
        for (List<SearchResp.SearchResult> rowList : resp.getSearchResults()) {
            for (SearchResp.SearchResult row : rowList) {
                double score = row.getScore();
                if (score < threshold) {
                    continue;
                }
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("chunk_id", Long.parseLong((String) row.getEntity().get("id")));
                map.put("content", row.getEntity().get("content"));
                map.put("document_id", Long.parseLong((String) row.getEntity().get("document_id")));
                map.put("score", score);
                results.add(map);
            }
        }
        return results;
    }

    /**
     * 将文本转为 BM25 稀疏向量
     * <p>Milvus 的 BM25 Function 会在 Insert 时自动将 content 转为 content_sparse，
     * 检索时需要手动构造稀疏向量输入</p>
     */
    private SortedMap<Long, Float> sparseFromString(String text) {
        SortedMap<Long, Float> sparse = new TreeMap<>();
        if (text == null || text.isEmpty()) {
            return sparse;
        }
        // 使用简单的字符级 hash 作为 sparse vector key
        for (int i = 0; i < text.length(); i++) {
            long key = Math.abs(text.charAt(i)) + 1L;
            sparse.merge(key, 1.0f, Float::sum);
        }
        return sparse;
    }

    @PreDestroy
    public void close() {
        if (client != null) {
            try {
                client.close();
                log.info("[Milvus] 客户端已关闭");
            } catch (Exception e) {
                log.warn("[Milvus] 关闭客户端异常: {}", e.getMessage());
            }
        }
    }
}
