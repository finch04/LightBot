# LightBot 知识库多类型检索系统技术设计文档

> 基于 LightBot 实际代码架构，设计 pgvector + Milvus 多类型知识库检索系统

---

## 一、需求分析

### 1.1 现状（基于实际代码）

| 维度 | 当前实现 | 文件位置 |
|------|---------|---------|
| 向量存储 | pgvector，raw SQL（`EmbeddingMapper`） | `mapper/EmbeddingMapper.java` |
| 检索参数 | `knowledge.config` JSONB 存 `ragTopK`/`ragThreshold`/`qaTopK`/`qaThreshold` | `entity/Knowledge.java` |
| 检索入口 | 3 处：`RagService`、`QueryKnowledgeTool`、`RetrievalNodeProcessor` | 分散在 service/tool/node |
| Embedding 模型 | 全局单例 `DashScopeEmbeddingModel`（1536 维） | `config/ModelConfig.java` |
| 知识库类型 | 无 `kb_type` 字段，所有知识库统一走 pgvector | `entity/Knowledge.java` |
| 检索模式 | 仅向量检索（cosine），无全文/混合检索 | `EmbeddingServiceImpl.java` |
| 重排序 | 未实现（`system_config` 有 `default_rerank_model` 但未使用） | - |
| 前端检索测试 | 知识库详情页有 search 入口，但无可配参数 | `KnowledgeDetail.vue` |

### 1.2 目标

1. **新增 Milvus 知识库类型**：`knowledge` 表加 `kb_type` 字段（`pgvector` / `milvus`）
2. **检索参数可配置**：在现有 `knowledge.config` 基础上扩展，支持不同类型的差异化参数
3. **检索测试增强**：前端检索测试页支持参数实时调整、结果展示 score
4. **统一检索接口**：3 个检索入口（RagService / QueryKnowledgeTool / RetrievalNode）无感知底层类型

### 1.3 非目标（本期不做）

- Dify / Notion 等外部只读知识库
- 知识图谱与向量检索的融合（Neo4j PPR）
- Embedding 模型动态切换（仍用全局 DashScope 模型）

---

## 二、核心问题：query_params 在 Java 中能不能派上用场

### 2.1 结论：能，而且必须用

当前 `knowledge.config` 已经在存储检索参数（`ragTopK`、`ragThreshold`），只是参数太少且不区分知识库类型。扩展后：

```
当前 config 结构：
{
  "ragTopK": 5,
  "ragThreshold": 0.5,
  "qaTopK": 3,
  "qaThreshold": 0.85,
  "defaultChunkStrategy": "general",
  ...
}

扩展后 config 结构：
{
  // ---- 原有字段保留 ----
  "ragTopK": 5,                    // ← 改为 final_top_k 的别名（向后兼容）
  "ragThreshold": 0.5,             // ← 改为 similarity_threshold 的别名
  "qaTopK": 3,
  "qaThreshold": 0.85,
  "defaultChunkStrategy": "general",
  ...

  // ---- 新增：检索参数配置 ----
  "query_params": {
    "search_mode": "vector",       // pgvector: vector/keyword/hybrid
    "final_top_k": 10,
    "similarity_threshold": 0.3,
    "vector_weight": 0.7,
    "keyword_weight": 0.3,
    "use_reranker": false
  }
}
```

### 2.2 query_params 的三个使用场景

| 场景 | 怎么用 | 优先级 |
|------|--------|--------|
| **检索测试页面** | 前端传临时参数覆盖 config，不持久化 | 高（运行时覆盖） |
| **RAG 问答** | 从 `knowledge.config.query_params` 读取持久化参数 | 低（持久化） |
| **Agent 工具调用** | 从 `knowledge.config.query_params` 读取，workflow 节点可覆盖 | 低（持久化） |

### 2.3 参数合并策略（Java 实现）

```java
/**
 * 合并检索参数：运行时覆盖 > 持久化配置 > 代码默认值
 *
 * @param knowledge  知识库实体（含 config JSONB）
 * @param overrides  运行时覆盖参数（可为 null）
 * @return 合并后的参数 Map
 */
public static Map<String, Object> mergeQueryParams(Knowledge knowledge,
                                                     Map<String, Object> overrides) {
    // 1. 代码默认值
    Map<String, Object> params = new HashMap<>(DEFAULT_PARAMS);

    // 2. 持久化配置覆盖默认值
    Map<String, Object> persisted = getConfigQueryParams(knowledge);
    params.putAll(persisted);

    // 3. 运行时覆盖持久化配置
    if (overrides != null) {
        params.putAll(overrides);
    }

    return params;
}
```

### 2.4 向后兼容方案

现有代码中 `ragTopK` 和 `ragThreshold` 直接在 `config` 根层级读取。迁移方案：

```java
// 读取时兼容旧字段
private Map<String, Object> getConfigQueryParams(Knowledge knowledge) {
    Map<String, Object> config = knowledge.getConfig();
    Map<String, Object> queryParams = (Map<String, Object>) config.get("query_params");

    if (queryParams == null) {
        // 兼容旧格式：从根层级读取
        queryParams = new HashMap<>();
        if (config.containsKey("ragTopK")) {
            queryParams.put("final_top_k", config.get("ragTopK"));
        }
        if (config.containsKey("ragThreshold")) {
            queryParams.put("similarity_threshold", config.get("ragThreshold"));
        }
    }
    return queryParams;
}
```

---

## 三、知识库类型差异分析

### 3.1 底层技术对比

| 维度 | pgvector（现有） | Milvus（新增） |
|------|-----------------|---------------|
| 向量索引 | HNSW（`vector(1536)` + cosine 操作符） | IVF_FLAT / HNSW（FLOAT_VECTOR） |
| 全文检索 | PostgreSQL `tsvector` + GIN 索引 | 内置 BM25（`SPARSE_FLOAT_VECTOR`） |
| 混合检索 | 应用层 RRF 融合（两次查询） | 原生 `hybrid_search`（`WeightedRanker`） |
| 向量写入 | `EmbeddingMapper.insertVector()` raw SQL | Milvus SDK `insert()` |
| 向量查询 | `EmbeddingMapper.searchSimilarSql()` | Milvus SDK `search()` / `hybridSearch()` |
| 运维成本 | 零（复用现有 PG） | 需部署 Milvus 集群（standalone 最简） |
| 适用规模 | 万级~十万级 chunk | 十万级~千万级 chunk |

### 3.2 检索模式对比

| 检索模式 | pgvector 实现 | Milvus 实现 |
|----------|--------------|-------------|
| `vector` | `1 - (embedding <=> #{vector}::vector)` | `collection.search(embedding, COSINE)` |
| `keyword` | `tsvector @@ plainto_tsquery()` | `collection.search(content_sparse, BM25)` |
| `hybrid` | 应用层：两次查询 + RRF 融合 | `hybrid_search([vectorReq, bm25Req], WeightedRanker)` |

### 3.3 检索参数差异

#### pgvector 可配参数（9 个）

| 参数 Key | 类型 | 默认值 | 说明 | 对应现有字段 |
|----------|------|--------|------|-------------|
| `search_mode` | select | `vector` | 检索模式 | 新增 |
| `final_top_k` | number | `5` | 返回 chunk 数 | `ragTopK`（兼容） |
| `similarity_threshold` | number | `0.5` | 相似度阈值 | `ragThreshold`（兼容） |
| `vector_weight` | number | `0.7` | 混合检索向量权重 | 新增 |
| `keyword_weight` | number | `0.3` | 混合检索关键词权重 | 新增 |
| `distance_metric` | select | `cosine` | 距离度量 | 新增 |
| `use_reranker` | boolean | `false` | 启用重排序 | 新增 |
| `reranker_model` | select | `""` | 重排序模型 | 新增 |
| `recall_top_k` | number | `50` | 重排序前候选数 | 新增 |

#### Milvus 可配参数（14 个）

| 参数 Key | 类型 | 默认值 | 说明 |
|----------|------|--------|------|
| `search_mode` | select | `vector` | 检索模式 |
| `final_top_k` | number | `10` | 返回 chunk 数 |
| `similarity_threshold` | number | `0.0` | 相似度阈值 |
| `bm25_top_k` | number | `50` | BM25 候选数 |
| `vector_weight` | number | `0.7` | 混合检索向量权重 |
| `bm25_weight` | number | `0.3` | 混合检索 BM25 权重 |
| `bm25_drop_ratio_search` | number | `0.0` | BM25 稀疏项丢弃比例 |
| `use_reranker` | boolean | `false` | 启用重排序 |
| `reranker_model` | select | `""` | 重排序模型 |
| `recall_top_k` | number | `50` | 重排序前候选数 |
| `include_distances` | boolean | `true` | 结果含相似度分数 |

---

## 四、技术设计

### 4.1 改动范围总览

```
改动类型    文件                                    改动内容
───────────────────────────────────────────────────────────────
新增        entity/Knowledge.java                   kb_type 字段
新增        service/KbTypeRouter.java               策略接口
新增        service/impl/PgVectorRouter.java        pgvector 检索实现
新增        service/impl/MilvusRouter.java          Milvus 检索实现
新增        service/KbTypeFactory.java              工厂类
新增        service/QueryParamsResolver.java        参数合并工具
新增        controller/KnowledgeController.java     /query-params 接口
新增        mapper/MilvusClientWrapper.java         Milvus SDK 封装
修改        service/impl/RagServiceImpl.java        检索走 Router
修改        service/impl/EmbeddingServiceImpl.java  检索走 Router
修改        tool/QueryKnowledgeTool.java            检索走 Router
修改        node/RetrievalNodeProcessor.java        检索走 Router
修改        knowledge 表 DDL                         加 kb_type 列
新增        pom.xml                                 milvus-sdk-java 依赖
修改        前端 KnowledgeDetail.vue                 检索参数配置面板
```

### 4.2 策略接口设计

```java
/**
 * 知识库类型检索路由接口
 * 每种向量库类型实现此接口，上层通过工厂获取对应实现
 */
public interface KbTypeRouter {

    /** 知识库类型标识 */
    String getKbType();

    /** 显示名称 */
    String getName();

    /**
     * 执行检索
     *
     * @param queryText  查询文本
     * @param knowledge  知识库实体（含 config）
     * @param overrides  运行时覆盖参数（检索测试传入，可为 null）
     * @return 检索结果
     */
    List<RetrievalResult> search(String queryText, Knowledge knowledge,
                                  Map<String, Object> overrides);

    /**
     * 获取该类型的可配参数 Schema（前端据此渲染配置表单）
     */
    List<ParamConfig> getQueryParamsConfig();

    /**
     * QA 对检索（部分类型可能不支持，返回空列表）
     */
    default List<RetrievalResult> searchQaPairs(String queryText, Knowledge knowledge,
                                                  Map<String, Object> overrides) {
        return Collections.emptyList();
    }
}
```

### 4.3 工厂类

```java
@Component
public class KbTypeFactory {

    private final Map<String, KbTypeRouter> routers;

    @Autowired
    public KbTypeFactory(List<KbTypeRouter> routerList) {
        this.routers = routerList.stream()
            .collect(Collectors.toMap(KbTypeRouter::getKbType, Function.identity()));
    }

    public KbTypeRouter getRouter(String kbType) {
        KbTypeRouter router = routers.get(kbType);
        if (router == null) {
            throw new BizException(ErrorCode.KB_TYPE_NOT_SUPPORTED,
                "不支持的知识库类型: " + kbType + "，可用: " + routers.keySet());
        }
        return router;
    }

    /** 获取所有已注册类型（创建知识库时下拉选择用） */
    public List<Map<String, String>> getAvailableTypes() {
        return routers.values().stream()
            .map(r -> Map.of("type", r.getKbType(), "name", r.getName()))
            .collect(Collectors.toList());
    }
}
```

### 4.4 pgvector 实现（改造现有代码）

现有 `EmbeddingServiceImpl.searchSimilarSql()` 改造为 `PgVectorRouter`：

```java
@Component
public class PgVectorRouter implements KbTypeRouter {

    @Autowired
    private EmbeddingMapper embeddingMapper;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Override
    public String getKbType() { return "pgvector"; }

    @Override
    public String getName() { return "pgvector 向量库"; }

    @Override
    public List<RetrievalResult> search(String queryText, Knowledge knowledge,
                                         Map<String, Object> overrides) {
        Map<String, Object> params = QueryParamsResolver.merge(knowledge, overrides);

        String searchMode = (String) params.getOrDefault("search_mode", "vector");
        int topK = ((Number) params.getOrDefault("final_top_k", 5)).intValue();
        double threshold = ((Number) params.getOrDefault("similarity_threshold", 0.5)).doubleValue();

        return switch (searchMode) {
            case "vector" -> vectorSearch(queryText, knowledge.getId(), topK, threshold);
            case "keyword" -> keywordSearch(queryText, knowledge.getId(), topK);
            case "hybrid"  -> hybridSearch(queryText, knowledge.getId(), params);
            default -> vectorSearch(queryText, knowledge.getId(), topK, threshold);
        };
    }

    private List<RetrievalResult> vectorSearch(String queryText, Long knowledgeId,
                                                int topK, double threshold) {
        // 复用现有 EmbeddingMapper.searchSimilarSql() 逻辑
        float[] embedding = embeddingModel.embed(queryText);
        String vectorStr = vectorToString(embedding);
        return embeddingMapper.searchSimilarSql(vectorStr, knowledgeId, topK, threshold)
            .stream().map(this::toRetrievalResult).collect(Collectors.toList());
    }

    private List<RetrievalResult> keywordSearch(String queryText, Long knowledgeId, int topK) {
        // 需要新增 Mapper 方法：基于 tsvector 的全文检索
        return embeddingMapper.searchByFullText(queryText, knowledgeId, topK)
            .stream().map(this::toRetrievalResult).collect(Collectors.toList());
    }

    private List<RetrievalResult> hybridSearch(String queryText, Long knowledgeId,
                                                Map<String, Object> params) {
        int recallTopK = ((Number) params.getOrDefault("recall_top_k", 50)).intValue();
        float vectorWeight = ((Number) params.getOrDefault("vector_weight", 0.7)).floatValue();
        float keywordWeight = ((Number) params.getOrDefault("keyword_weight", 0.3)).floatValue();

        List<RetrievalResult> vectorResults = vectorSearch(queryText, knowledgeId, recallTopK, 0);
        List<RetrievalResult> keywordResults = keywordSearch(queryText, knowledgeId, recallTopK);

        return rrfFusion(vectorResults, keywordResults, vectorWeight, keywordWeight);
    }

    @Override
    public List<ParamConfig> getQueryParamsConfig() {
        return List.of(
            ParamConfig.select("search_mode", "检索模式", "vector",
                "vector", "向量检索",
                "keyword", "全文检索",
                "hybrid", "混合检索"),
            ParamConfig.number("final_top_k", "返回 Chunk 数", 5, 1, 100),
            ParamConfig.number("similarity_threshold", "相似度阈值", 0.5, 0.0, 1.0, 0.1),
            ParamConfig.number("vector_weight", "向量权重", 0.7, 0.0, 1.0, 0.1),
            ParamConfig.number("keyword_weight", "关键词权重", 0.3, 0.0, 1.0, 0.1),
            ParamConfig.select("distance_metric", "距离度量", "cosine",
                "cosine", "余弦相似度",
                "l2", "欧氏距离"),
            ParamConfig.bool("use_reranker", "启用重排序", false),
            ParamConfig.number("recall_top_k", "重排序候选数", 50, 10, 200)
                .dependOn("use_reranker", "true")
        );
    }
}
```

### 4.5 Milvus 实现

```java
@Component
@ConditionalOnProperty(name = "lightbot.milvus.enabled", havingValue = "true")
public class MilvusRouter implements KbTypeRouter {

    @Autowired
    private MilvusClientV2 milvusClient;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Override
    public String getKbType() { return "milvus"; }

    @Override
    public String getName() { return "Milvus 向量库"; }

    @Override
    public List<RetrievalResult> search(String queryText, Knowledge knowledge,
                                         Map<String, Object> overrides) {
        Map<String, Object> params = QueryParamsResolver.merge(knowledge, overrides);
        String collectionName = "kb_" + knowledge.getId();

        String searchMode = (String) params.getOrDefault("search_mode", "vector");
        int finalTopK = ((Number) params.getOrDefault("final_top_k", 10)).intValue();

        List<RetrievalResult> results = switch (searchMode) {
            case "vector"   -> milvusVectorSearch(collectionName, queryText, params);
            case "keyword"  -> milvusKeywordSearch(collectionName, queryText, params);
            case "hybrid"   -> milvusHybridSearch(collectionName, queryText, params);
            default         -> milvusVectorSearch(collectionName, queryText, params);
        };

        return results.subList(0, Math.min(finalTopK, results.size()));
    }

    private List<RetrievalResult> milvusHybridSearch(String collection, String queryText,
                                                      Map<String, Object> params) {
        float[] embedding = embeddingModel.embed(queryText);
        int bm25TopK = ((Number) params.getOrDefault("bm25_top_k", 50)).intValue();
        float vectorWeight = ((Number) params.getOrDefault("vector_weight", 0.7)).floatValue();
        float bm25Weight = ((Number) params.getOrDefault("bm25_weight", 0.3)).floatValue();

        AnnSearchReq vectorReq = AnnSearchReq.builder()
            .vectorFieldName("embedding")
            .vectors(List.of(new FloatVec(embedding)))
            .topK(bm25TopK)
            .metricType(IndexParam.MetricType.COSINE)
            .build();

        AnnSearchReq bm25Req = AnnSearchReq.builder()
            .vectorFieldName("content_sparse")
            .vectors(List.of(new SparseVec(queryText)))
            .topK(bm25TopK)
            .metricType(IndexParam.MetricType.BM25)
            .build();

        HybridSearchResp resp = milvusClient.hybridSearch(
            HybridSearchReq.builder()
                .collectionName(collection)
                .searchRequests(List.of(vectorReq, bm25Req))
                .ranker(new WeightedRanker(vectorWeight, bm25Weight))
                .topK(((Number) params.getOrDefault("final_top_k", 10)).intValue())
                .build()
        );

        return convertResults(resp);
    }

    @Override
    public List<ParamConfig> getQueryParamsConfig() {
        List<ParamConfig> configs = new ArrayList<>(PgVectorRouter.baseParams());
        configs.add(ParamConfig.number("bm25_top_k", "BM25 召回数", 50, 1, 200));
        configs.add(ParamConfig.number("bm25_weight", "BM25 权重", 0.3, 0.0, 1.0, 0.1));
        configs.add(ParamConfig.number("bm25_drop_ratio_search", "BM25 稀疏项丢弃", 0.0, 0.0, 1.0, 0.1));
        return configs;
    }
}
```

### 4.6 检索入口改造

现有 3 个检索入口统一改为通过 `KbTypeFactory` 路由：

#### RagService 改造

```java
// 改造前（RagServiceImpl.java）
List<EmbeddingServiceImpl.SearchResult> results =
    embeddingService.searchSimilarSql(queryVector, knowledgeId, ragTopK, ragThreshold);

// 改造后
Knowledge knowledge = knowledgeMapper.selectById(knowledgeId);
KbTypeRouter router = kbTypeFactory.getRouter(knowledge.getKbType());
List<RetrievalResult> results = router.search(question, knowledge, null);
```

#### QueryKnowledgeTool 改造

```java
// 改造前
List<EmbeddingServiceImpl.SearchResult> chunkResults =
    embeddingService.searchSimilarSql(queryVector, knowledgeId, ragTopK, ragThreshold);

// 改造后
Knowledge knowledge = knowledgeMapper.selectById(knowledgeId);
KbTypeRouter router = kbTypeFactory.getRouter(knowledge.getKbType());
List<RetrievalResult> results = router.search(queryText, knowledge, null);
```

#### RetrievalNodeProcessor 改造

```java
// 改造前
List<EmbeddingServiceImpl.SearchResult> results =
    embeddingService.searchSimilarSql(queryVector, knowledgeId, topK, threshold);

// 改造后
Knowledge knowledge = knowledgeMapper.selectById(knowledgeId);
Map<String, Object> overrides = nodeConfig.isOverrideConfig()
    ? Map.of("final_top_k", topK, "similarity_threshold", threshold)
    : null;
List<RetrievalResult> results = router.search(queryText, knowledge, overrides);
```

### 4.7 API 接口

```java
// KnowledgeController.java 新增

/**
 * 获取知识库检索参数配置 Schema
 * 前端据此动态渲染参数表单
 */
@GetMapping("/{id}/query-params")
public Result<List<ParamConfig>> getQueryParams(@PathVariable Long id) {
    Knowledge knowledge = knowledgeService.getById(id);
    KbTypeRouter router = kbTypeFactory.getRouter(knowledge.getKbType());
    return Result.success(router.getQueryParamsConfig());
}

/**
 * 更新知识库检索参数（持久化到 knowledge.config.query_params）
 */
@PutMapping("/{id}/query-params")
public Result<Void> updateQueryParams(@PathVariable Long id,
                                       @RequestBody Map<String, Object> queryParams) {
    Knowledge knowledge = knowledgeService.getById(id);
    Map<String, Object> config = knowledge.getConfig();
    config.put("query_params", queryParams);
    knowledgeMapper.updateById(knowledge);
    return Result.success();
}

/**
 * 检索测试（支持运行时参数覆盖，不持久化）
 */
@PostMapping("/{id}/query-test")
public Result<QueryTestResponse> queryTest(@PathVariable Long id,
                                            @RequestBody QueryTestRequest request) {
    Knowledge knowledge = knowledgeService.getById(id);
    KbTypeRouter router = kbTypeFactory.getRouter(knowledge.getKbType());

    long start = System.currentTimeMillis();
    List<RetrievalResult> results = router.search(
        request.getQuery(), knowledge, request.getParams());
    long cost = System.currentTimeMillis() - start;

    return Result.success(new QueryTestResponse(results, cost, request.getParams()));
}
```

### 4.8 数据库变更

```sql
-- 1. knowledge 表加 kb_type 列
ALTER TABLE knowledge ADD COLUMN IF NOT EXISTS kb_type VARCHAR(32) NOT NULL DEFAULT 'pgvector';
CREATE INDEX IF NOT EXISTS idx_knowledge_kb_type ON knowledge(kb_type);

-- 2. 现有知识库默认都是 pgvector（已由 DEFAULT 保证）
-- 3. 如需支持全文检索，给 chunk 表加 tsvector 列
ALTER TABLE chunk ADD COLUMN IF NOT EXISTS content_tsv tsvector;
UPDATE chunk SET content_tsv = to_tsvector('simple', content) WHERE content_tsv IS NULL;
CREATE INDEX IF NOT EXISTS idx_chunk_content_tsv ON chunk USING GIN(content_tsv);

-- 触发器：自动更新 content_tsv
CREATE OR REPLACE FUNCTION update_chunk_content_tsv() RETURNS TRIGGER AS $$
BEGIN
    NEW.content_tsv := to_tsvector('simple', NEW.content);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_chunk_content_tsv
    BEFORE INSERT OR UPDATE OF content ON chunk
    FOR EACH ROW EXECUTE FUNCTION update_chunk_content_tsv();

-- 4. embedding 表如需支持不同维度，考虑加 dimension 约束
-- 当前 vector(1536) 是硬编码的，Milvus 侧维度由 Collection Schema 管理
```

### 4.9 Milvus Collection Schema

每个知识库对应一个 Collection（`kb_{knowledge_id}`）：

```java
public void createCollection(String collectionName, int dimension) {
    // 字段定义
    List<FieldType> fields = List.of(
        FieldType.newBuilder()
            .withName("id").withDataType(DataType.VarChar)
            .withMaxLength(64).withIsPrimaryKey(true).build(),
        FieldType.newBuilder()
            .withName("content").withDataType(DataType.VarChar)
            .withMaxLength(65535)
            .withAnalyzerParams(Map.of("type", "chinese"))  // 中文分词
            .enableAnalyzer().build(),
        FieldType.newBuilder()
            .withName("chunk_id").withDataType(DataType.VarChar).withMaxLength(64).build(),
        FieldType.newBuilder()
            .withName("embedding").withDataType(DataType.FloatVector)
            .withDimension(dimension).build(),
        FieldType.newBuilder()
            .withName("content_sparse").withDataType(DataType.SparseFloatVector).build()
    );

    // BM25 Function（content -> content_sparse 自动转换）
    Function function = Function.newBuilder()
        .withName("bm25_sparse")
        .withType(FunctionType.BM25)
        .addInputField("content")
        .addOutputField("content_sparse")
        .build();

    CreateCollectionReq req = CreateCollectionReq.builder()
        .collectionName(collectionName)
        .addFieldTypes(fields)
        .addFunction(function)
        .build();

    milvusClient.createCollection(req);

    // 创建索引
    // 向量索引：HNSW + COSINE
    milvusClient.createIndex(CreateIndexReq.builder()
        .collectionName(collectionName)
        .fieldName("embedding")
        .indexType(IndexType.HNSW)
        .metricType(IndexParam.MetricType.COSINE)
        .extraParams(Map.of("M", 16, "efConstruction", 200))
        .build());

    // BM25 索引
    milvusClient.createIndex(CreateIndexReq.builder()
        .collectionName(collectionName)
        .fieldName("content_sparse")
        .indexType(IndexType.SPARSE_INVERTED_INDEX)
        .metricType(IndexParam.MetricType.BM25)
        .extraParams(Map.of("drop_ratio_search", 0.0))
        .build());
}
```

---

## 五、前端检索测试页面

### 5.1 页面布局

```
┌──────────────────────────────────────────────────────────┐
│  检索测试                                    [知识库 ▼]   │
├──────────────────────────────────────────────────────────┤
│                                                          │
│  ┌── 查询输入 ─────────────────────────────────────────┐ │
│  │  [ 什么是向量检索？                    ] [测试检索]  │ │
│  └─────────────────────────────────────────────────────┘ │
│                                                          │
│  ┌── 检索配置 ─────────────────────────────────────────┐ │
│  │  知识库类型: pgvector（只读展示）                     │ │
│  │  检索模式:   [向量检索 ▼]                            │ │
│  │  返回数量:   [5]       相似度阈值: [0.5]             │ │
│  │  向量权重:   [0.7]     关键词权重: [0.3]             │ │
│  │  ☐ 启用重排序                                       │ │
│  │                           [保存为默认] [恢复默认]    │ │
│  └─────────────────────────────────────────────────────┘ │
│                                                          │
│  ┌── 检索结果 (5条，耗时 120ms) ──────────────────────┐  │
│  │  #1  score: 0.87  [RAG技术详解.pdf]                │  │
│  │      向量检索是将文本转换为高维向量…                 │  │
│  │                                                    │  │
│  │  #2  score: 0.82  [知识库架构设计.md]              │  │
│  │      在RAG系统中，向量检索是最基础的检索方式…       │  │
│  └────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────┘
```

### 5.2 前端交互逻辑

```javascript
// knowledge.js API 层新增
export function getQueryParams(kbId) {
  return request.get(`/api/knowledge/${kbId}/query-params`)
}

export function updateQueryParams(kbId, params) {
  return request.put(`/api/knowledge/${kbId}/query-params`, params)
}

export function queryTest(kbId, query, params) {
  return request.post(`/api/knowledge/${kbId}/query-test`, { query, params })
}

// 检索测试组件逻辑
const loadQueryParams = async () => {
  const config = await getQueryParams(kbId)
  // config 是 ParamConfig[]，根据 type 字段动态渲染表单
  paramFields.value = config
}

const handleTest = async () => {
  const overrides = collectFormValues()  // 收集表单值作为运行时覆盖
  const res = await queryTest(kbId, queryText.value, overrides)
  results.value = res.data.results
  costMs.value = res.data.costMs
}

const handleSaveDefaults = async () => {
  const params = collectFormValues()
  await updateQueryParams(kbId, params)
  message.success('检索配置已保存')
}
```

---

## 六、迁移难点与风险

### 6.1 现有代码改造

| 风险 | 影响 | 解决方案 |
|------|------|----------|
| `EmbeddingMapper` 是 pgvector 专用 | Milvus 类型知识库走不到 Mapper | 通过 `KbTypeRouter` 策略隔离，pgvector 走 Mapper，Milvus 走 SDK |
| `RagService` 直接调 `EmbeddingService` | 3 个检索入口都要改 | 统一改为 `kbTypeFactory.getRouter(kbType).search()` |
| `QueryKnowledgeTool` 硬编码检索逻辑 | Agent 工具调用不走 Router | 改为注入 `KbTypeFactory`，按知识库类型路由 |
| `knowledge.config` 已有数据 | 旧数据没有 `query_params` 字段 | 读取时兼容旧格式（`ragTopK` → `final_top_k`） |

### 6.2 Milvus 引入

| 风险 | 影响 | 解决方案 |
|------|------|----------|
| 运维复杂度 | 需部署 Milvus 集群 | 开发环境用 Milvus Lite（嵌入式），生产用 Standalone |
| 数据双写 | pgvector 和 Milvus 数据不一致 | 不做双写，创建知识库时选定类型后不可切换 |
| Embedding 维度 | pgvector 硬编码 1536，Milvus 可配 | 创建 Collection 时根据 `knowledge.embeddingModel` 决定维度 |
| 中文分词 | Milvus BM25 需要中文分词器 | Collection Schema 设置 `analyzer: "chinese"` |
| 向量写入改造 | 现有 `EmbeddingMapper.insertVector()` 是 pgvector 专用 | 新增 `MilvusVectorWriter`，入库时根据 `kb_type` 路由 |

### 6.3 前端适配

| 风险 | 影响 | 解决方案 |
|------|------|----------|
| 参数表单动态渲染 | 不同类型参数不同 | 后端返回 `ParamConfig[]`，前端用 `v-for` + `component :is` 动态渲染 |
| 创建知识库时选类型 | 需要新增下拉框 | 创建表单加 `kb_type` 字段，创建后不可修改 |
| 旧知识库无类型 | 显示异常 | 默认 `pgvector`，前端不显示类型标签 |

---

## 七、实施计划

### Phase 1：框架 + pgvector 改造（1 周）

- [ ] `knowledge` 表加 `kb_type` 列（DDL）
- [ ] `Knowledge` 实体加 `kbType` 字段
- [ ] `KbTypeRouter` 接口 + `KbTypeFactory` 工厂
- [ ] `PgVectorRouter` 实现（封装现有 `EmbeddingMapper` 逻辑）
- [ ] `QueryParamsResolver` 参数合并工具
- [ ] 改造 `RagService` / `QueryKnowledgeTool` / `RetrievalNodeProcessor` 走 Router
- [ ] `GET/PUT /{id}/query-params` + `POST /{id}/query-test` 接口
- [ ] 前端参数配置组件 + 检索测试页面

### Phase 2：Milvus 集成（1-2 周）

- [ ] `milvus-sdk-java` 依赖引入
- [ ] `MilvusClientWrapper` 封装（连接管理、Collection CRUD）
- [ ] `MilvusRouter` 实现（向量 / BM25 / 混合检索）
- [ ] Milvus Collection 自动创建（知识库创建时）
- [ ] 文档入库时根据 `kb_type` 路由写入 Milvus 或 pgvector
- [ ] `chunk.content_tsv` 列 + 触发器（pgvector 全文检索支持）
- [ ] 前端创建知识库时选择类型

### Phase 3：优化（后续）

- [ ] 重排序模型集成（跨类型通用）
- [ ] Milvus 连接池 + 健康检查
- [ ] 检索性能监控
