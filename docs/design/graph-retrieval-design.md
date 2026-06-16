# 图检索（Graph Retrieval）技术设计文档

> 参考 Yuxi 项目（Python）实现，设计 LightBot（Java）的图检索方案

---

## 一、需求分析

### 1.1 目标

将 Yuxi 项目检索配置中的 7 个图检索参数迁移到 LightBot，使这些参数在**检索测试**和 **Agent 知识库查询**中均能正常生效。

### 1.2 需迁移的参数

| # | 参数 Key | 类型 | 默认值 | 说明 |
|---|----------|------|--------|------|
| 1 | `use_graph_retrieval` | boolean | `false` | 启用图检索 |
| 2 | `graph_entity_top_k` | int | `10` | 实体向量召回数 |
| 3 | `graph_triple_top_k` | int | `10` | 三元组向量召回数 |
| 4 | `graph_max_nodes` | int | `10000` | 2-hop 子图最大节点数 |
| 5 | `graph_top_k` | int | `20` | PPR 排序后返回的 chunk 数 |
| 6 | `graph_weight` | float | `1.0` | 图结果在 RRF 融合中的权重 |
| 7 | `ppr_damping` | float | `0.85` | PPR 阻尼系数 |

### 1.3 约束条件

- 图检索仅适用于 **Milvus 类型**知识库（pgvector 不涉及）
- 前提：知识库已完成图谱抽取（`knowledge_graph.status = COMPLETED`）
- 不改变现有图谱抽取流程（`GraphService` / `GraphExtractor` 保持不变）

---

## 二、Yuxi 实现分析

### 2.1 整体架构（三存储协同）

```
┌─────────────────────────────────────────────────────────────────┐
│                        检索查询流程                               │
│                                                                 │
│  用户问题 ──► Embedding ──┬──► Milvus Chunk 向量检索 ──► 基础结果  │
│                          │                                      │
│                          ├──► Milvus Entity 向量检索 ──┐         │
│                          └──► Milvus Triple 向量检索 ──┤         │
│                                                       ▼         │
│                              构建种子权重（entity×1.0 + triple×0.8）│
│                                                       │         │
│                                                       ▼         │
│                              Neo4j 2-hop 子图 + PPR 排序         │
│                                                       │         │
│                                                       ▼         │
│                              PPR 结果 → 图 chunk 列表            │
│                                                       │         │
│                              基础结果 ──┐                        │
│                              图 chunk ──┤──► RRF 融合 ──► 最终结果│
│                                        │                        │
│                              (可选) Reranker 精排                │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 三存储职责

| 存储 | Yuxi 中的职责 | LightBot 现状 |
|------|-------------|--------------|
| **Milvus** | 存储 Chunk/Entity/Triple 的向量 + BM25 稀疏向量 | ✅ Chunk 向量已有；❌ Entity/Triple 向量 Collection 未创建 |
| **Neo4j** | 存储图结构（Chunk→Entity MENTIONS、Entity→Entity RELATION） | ✅ 已有 `GraphServiceImpl` 写入图结构 |
| **PostgreSQL** | 存储 Entity/Triple 元数据、mention 关联 | ❌ 无 Entity/Triple 关系表 |

### 2.3 Yuxi 的 Neo4j 图结构

**节点类型**：
- `Chunk` — 标签: `:Chunk:MilvusKB:{kb_id}`，属性: chunk_id, file_id, content_preview
- `Entity` — 标签: `:Entity:MilvusKB:{kb_id}`，属性: entity_id, normalized_name, label, name, attributes

**关系类型**：
- `MENTIONS` — Chunk → Entity，表示"分块提及了实体"
- `RELATION` — Entity → Entity，表示三元组关系，属性含 type, triple_id, text

### 2.4 Yuxi 的 Milvus 图向量 Collection

每个知识库额外创建两个 Milvus Collection：

| Collection | 命名 | 字段 | 用途 |
|------------|------|------|------|
| Entity | `{kb_id}_entity` | id, content(normalized_name), embedding, content_sparse | 实体语义检索 |
| Triple | `{kb_id}_triple` | id, content("源→关系→目标"), embedding, content_sparse, source_id, target_id | 三元组语义检索 |

### 2.5 Yuxi 的 PPR 实现

1. 从 Milvus 检索到 seed entity IDs + scores
2. 用 seed IDs 从 Neo4j 获取 2-hop 子图（`MATCH (seed)-[*1..2]-(n)`）
3. 用 `python-igraph` 构建图，设置 seed 权重为 restart 向量
4. 调用 `graph.personalized_pagerank(damping=0.85, reset=weights)`
5. 提取 Chunk 节点的 PPR 分数，降序排列取 top_k

### 2.6 RRF 融合

```
base_chunks:  weight = 1.0
graph_chunks: weight = graph_weight (默认 1.0)
rrf_k = 60
每个 chunk 的 RRF 分数 = weight / (rrf_k + rank)
两组结果合并，同 chunk 累加分数，按总分降序排列
```

---

## 三、LightBot 现状与差距分析

### 3.1 LightBot 已有能力

| 能力 | 状态 | 位置 |
|------|------|------|
| Neo4j 图结构（Entity/Chunk 节点、RELATION/MENTIONS 边） | ✅ | `GraphServiceImpl.writeTriplesToNeo4j()` |
| Neo4j 2-hop 子图查询 | ✅ | `GraphServiceImpl.searchForRag()` 已有 2-hop Cypher |
| LLM 从问题中提取实体名 | ✅ | `GraphExtractor.extractEntitiesFromQuestion()` |
| Milvus Collection 创建/写入/检索 | ✅ | `MilvusUtil` 完整封装 |
| Embedding 模型（Spring AI EmbeddingModel） | ✅ | 已注入多个 Service |
| RRF 融合算法 | ✅ | `EmbeddingServiceImpl.rrfFusion()` 刚实现 |

### 3.2 需新增的能力

| 能力 | 复杂度 | 说明 |
|------|--------|------|
| Entity/Triple 的 Milvus Collection 创建与写入 | ⭐⭐ 中等 | 复用 `MilvusUtil` 现有模式，新增 Collection 管理 |
| Entity/Triple 的向量检索 | ⭐ 简单 | 复用 `MilvusUtil.searchVector()` |
| PPR 算法 | ⭐⭐⭐ 困难 | 需引入 Java 图算法库或自己实现 |
| 图检索集成到 EmbeddingServiceImpl | ⭐⭐ 中等 | 在 `searchSimilarSql()` 中新增图检索分支 |
| RRF 融合扩展（支持图结果） | ⭐ 简单 | 扩展现有 `rrfFusion()` 方法 |
| 前端图检索参数控件 | ⭐ 简单 | `QueryParamsModal` 新增条件区域 |

---

## 四、技术方案设计

### 4.1 核心决策

| 决策点 | 选项 | 选择 | 理由 |
|--------|------|------|------|
| PPR 实现 | A. Neo4j GDS 插件<br>B. 自实现迭代 PPR<br>C. 引入 JGraphT | **B. 自实现** | GDS 需安装插件，JGraphT 引入重依赖；自实现迭代式 PPR 仅需 ~50 行代码 |
| Entity 向量存储 | A. Milvus 新 Collection<br>B. Neo4j 向量索引 | **A. Milvus** | 与 Yuxi 一致，复用现有 Milvus 基础设施，性能更好 |
| Entity/Triple 元数据 | A. 新建 PG 表<br>B. 只用 Neo4j | **B. 只用 Neo4j** | 轻量化，GraphServiceImpl 已将所有元数据存入 Neo4j 节点属性 |
| 子图查询 | A. Cypher 2-hop<br>B. Neo4j BFS | **A. Cypher** | GraphServiceImpl.searchForRag 已有 2-hop Cypher，直接复用 |

### 4.2 检索流程设计

```
EmbeddingServiceImpl.searchSimilarSql(knowledgeId, queryVector, topK, threshold, queryParams)
│
├── Milvus 基础检索（vector/keyword/hybrid）──► baseResults
│
├── if use_graph_retrieval == true:
│   │
│   ├── 1. 向量化 queryText
│   │
│   ├── 2. Milvus Entity 向量检索（graph_entity_top_k）
│   │      → entityHits: [{entityId, name, score}, ...]
│   │
│   ├── 3. Milvus Triple 向量检索（graph_triple_top_k）
│   │      → tripleHits: [{tripleId, sourceId, targetId, content, score}, ...]
│   │
│   ├── 4. 构建 seedWeights:
│   │      entity hits → weight 1.0
│   │      triple hits → source/target 各 weight 0.8
│   │      归一化到 sum=1.0
│   │
│   ├── 5. Neo4j 2-hop 子图（graph_max_nodes 限制）
│   │      → nodes[], edges[]
│   │
│   ├── 6. 迭代式 PPR（ppr_damping, seedWeights）
│   │      → chunkScores: {chunkId: pprScore, ...}
│   │
│   ├── 7. 按 PPR 分数取 top graph_top_k → graphResults
│   │
│   └── 8. RRF 融合: baseResults + graphResults（graph_weight）
│          → fusedResults
│
├── if use_reranker == true:
│   └── Reranker 精排
│
└── return finalResults
```

### 4.3 自实现迭代式 PPR

```java
/**
 * 迭代式 Personalized PageRank
 *
 * @param adjacency  邻接表: nodeId → [neighborIds]
 * @param seedWeights 种子节点权重: nodeId → weight (已归一化)
 * @param damping     阻尼系数 (0.85)
 * @param maxIter     最大迭代次数 (20)
 * @param tolerance   收敛阈值 (1e-6)
 * @return 每个节点的 PPR 分数
 */
private Map<String, Double> personalizedPageRank(
        Map<String, List<String>> adjacency,
        Map<String, Double> seedWeights,
        double damping, int maxIter, double tolerance) {

    Set<String> allNodes = new HashSet<>(adjacency.keySet());
    seedWeights.keySet().forEach(n -> allNodes.add(n));

    int n = allNodes.size();
    if (n == 0) return Map.of();

    // 初始化: 均匀分布
    Map<String, Double> scores = new HashMap<>();
    for (String node : allNodes) {
        scores.put(node, seedWeights.getOrDefault(node, 0.0));
    }

    // 迭代
    for (int iter = 0; iter < maxIter; iter++) {
        Map<String, Double> newScores = new HashMap<>();
        double maxDiff = 0;

        for (String node : allNodes) {
            // 来自邻居的贡献
            double neighborSum = 0;
            for (String neighbor : adjacency.getOrDefault(node, List.of())) {
                int degree = adjacency.getOrDefault(neighbor, List.of()).size();
                if (degree > 0) {
                    neighborSum += scores.getOrDefault(neighbor, 0.0) / degree;
                }
            }
            double newScore = (1 - damping) * seedWeights.getOrDefault(node, 0.0)
                            + damping * neighborSum;
            newScores.put(node, newScore);
            maxDiff = Math.max(maxDiff, Math.abs(newScore - scores.getOrDefault(node, 0.0)));
        }

        scores = newScores;
        if (maxDiff < tolerance) break;
    }

    return scores;
}
```

**性能评估**：`graph_max_nodes=10000` 时，邻接表 + 20 轮迭代，纯 Java HashMap 操作，预计 <50ms。

---

## 五、涉及文件与改动清单

### 5.1 新增文件

| 文件 | 说明 |
|------|------|
| `util/GraphRetrievalUtil.java` | 图检索核心工具类：Milvus Entity/Triple Collection 管理、Neo4j 子图查询、PPR 算法、RRF 融合 |
| `sql/2026-06-16-002.sql` | Entity/Triple 向量 Collection 初始化脚本（可选，也可运行时自动创建） |

### 5.2 修改文件

| 文件 | 改动 |
|------|------|
| `util/MilvusUtil.java` | 新增 Entity/Triple Collection 创建、写入、检索方法 |
| `service/impl/EmbeddingServiceImpl.java` | `searchSimilarSql()` 中集成图检索分支 |
| `service/impl/GraphServiceImpl.java` | 图谱抽取完成后自动写入 Milvus Entity/Triple 向量 |
| `components/QueryParamsModal.vue` | 新增图检索参数区域（depend on `use_graph_retrieval`） |

### 5.3 复用的现有代码

| 现有代码 | 复用方式 |
|---------|---------|
| `MilvusUtil.createCollectionForKnowledge()` | 模式复用：为 Entity/Triple 创建类似 Collection |
| `MilvusUtil.searchVector()` | 直接复用：Entity/Triple 向量检索 |
| `MilvusUtil.insertVectors()` | 模式复用：Entity/Triple 向量写入 |
| `Neo4jUtil.query()` | 直接复用：2-hop 子图 Cypher 查询 |
| `GraphExtractor.extractEntitiesFromQuestion()` | 直接复用：从问题提取实体名 |
| `GraphServiceImpl.searchForRag()` | 参考其 2-hop Cypher 模式 |
| `EmbeddingServiceImpl.rrfFusion()` | 扩展复用：增加图结果融合 |
| `EmbeddingServiceImpl.toVectorString()` | 直接复用 |

---

## 六、详细设计

### 6.1 MilvusUtil 扩展 — Entity/Triple Collection

```java
// 新增方法（MilvusUtil.java）

/** 为知识库创建 Entity Collection */
public void createEntityCollection(Long knowledgeId, int dimension) {
    String collName = entityCollectionName(knowledgeId);
    // 与 createCollectionForKnowledge 相同模式：
    // fields: id(VARCHAR PK), content(VARCHAR+BM25 analyzer),
    //         embedding(FloatVector), content_sparse(SparseFloatVector)
    // index: HNSW(COSINE) + SPARSE_INVERTED_INDEX(BM25)
}

/** 为知识库创建 Triple Collection */
public void createTripleCollection(Long knowledgeId, int dimension) {
    String collName = tripleCollectionName(knowledgeId);
    // fields: id(VARCHAR PK), content(VARCHAR+BM25 analyzer),
    //         source_id(VARCHAR), target_id(VARCHAR),
    //         embedding(FloatVector), content_sparse(SparseFloatVector)
}

/** 批量写入 Entity 向量 */
public void insertEntityVectors(Long knowledgeId, List<String> entityIds,
                                 List<String> contents, List<float[]> vectors) { ... }

/** 批量写入 Triple 向量 */
public void insertTripleVectors(Long knowledgeId, List<String> tripleIds,
                                 List<String> contents, List<String> sourceIds,
                                 List<String> targetIds, List<float[]> vectors) { ... }

/** Entity 向量检索 */
public List<Map<String, Object>> searchEntities(Long knowledgeId, float[] queryVector, int topK) { ... }

/** Triple 向量检索 */
public List<Map<String, Object>> searchTriples(Long knowledgeId, float[] queryVector, int topK) { ... }

/** 按知识库删除 Entity/Triple Collection */
public void dropGraphCollections(Long knowledgeId) {
    dropCollection(entityCollectionName(knowledgeId));
    dropCollection(tripleCollectionName(knowledgeId));
}

// 命名约定（与 Yuxi 一致）
public static String entityCollectionName(Long knowledgeId) {
    return "kb_" + knowledgeId + "_entity";
}
public static String tripleCollectionName(Long knowledgeId) {
    return "kb_" + knowledgeId + "_triple";
}
```

### 6.2 GraphRetrievalUtil — 图检索核心

```java
@Slf4j
@Component
public class GraphRetrievalUtil {

    private final MilvusUtil milvusUtil;
    private final Neo4jUtil neo4jUtil;
    private final EmbeddingModel embeddingModel;

    /**
     * 执行图检索
     *
     * @param knowledgeId 知识库ID
     * @param queryText   查询文本
     * @param baseResults 基础检索结果（用于提取关联 entity IDs）
     * @param params      图检索参数
     * @return 图检索结果（chunk_id, content, score 等）
     */
    public List<Map<String, Object>> graphRetrieve(
            Long knowledgeId, String queryText,
            List<Map<String, Object>> baseResults,
            Map<String, Object> params) {

        int entityTopK = getIntParam(params, "graph_entity_top_k", 10);
        int tripleTopK = getIntParam(params, "graph_triple_top_k", 10);
        int maxNodes = getIntParam(params, "graph_max_nodes", 10000);
        int graphTopK = getIntParam(params, "graph_top_k", 20);
        double pprDamping = getDoubleParam(params, "ppr_damping", 0.85);

        // 1. 向量化查询文本
        float[] queryVector = embedText(queryText);

        // 2. Milvus Entity/Triple 向量检索
        List<Map<String, Object>> entityHits = milvusUtil.searchEntities(
                knowledgeId, queryVector, entityTopK);
        List<Map<String, Object>> tripleHits = milvusUtil.searchTriples(
                knowledgeId, queryVector, tripleTopK);

        // 3. 构建 seed 权重
        Map<String, Double> seedWeights = buildSeedWeights(
                entityHits, tripleHits, baseResults);

        // 4. Neo4j 2-hop 子图
        GraphSubgraph subgraph = querySeedSubgraph(
                knowledgeId, seedWeights.keySet(), maxNodes);

        // 5. PPR
        Map<String, Double> pprScores = personalizedPageRank(
                subgraph.adjacency, seedWeights, pprDamping, 20, 1e-6);

        // 6. 提取 Chunk 节点的 PPR 分数，取 top_k
        return selectTopChunks(pprScores, subgraph, graphTopK);
    }
}
```

### 6.3 GraphServiceImpl 扩展 — 向量化写入

在图谱抽取完成后，自动将 Entity/Triple 写入 Milvus 向量 Collection：

```java
// GraphServiceImpl.java 新增方法

/**
 * 将图谱实体和三元组写入 Milvus 向量 Collection
 * 在图谱抽取完成后调用
 */
private void writeGraphVectors(Long knowledgeId) {
    // 1. 从 Neo4j 查询所有 Entity 节点
    List<Map<String, Object>> entities = queryEntities(knowledgeId);
    // 2. 从 Neo4j 查询所有 RELATION 关系
    List<Map<String, Object>> triples = queryTriples(knowledgeId);

    // 3. 确保 Milvus Collection 存在
    int dimension = getEmbeddingDimension();
    milvusUtil.createEntityCollection(knowledgeId, dimension);
    milvusUtil.createTripleCollection(knowledgeId, dimension);

    // 4. Embedding + 写入 Entity
    List<float[]> entityVectors = batchEmbed(entityContents);
    milvusUtil.insertEntityVectors(knowledgeId, entityIds, entityContents, entityVectors);

    // 5. Embedding + 写入 Triple
    List<float[]> tripleVectors = batchEmbed(tripleContents);
    milvusUtil.insertTripleVectors(knowledgeId, tripleIds, tripleContents,
                                     sourceIds, targetIds, tripleVectors);
}
```

**触发时机**：`GraphExtractionExecutor` 执行完成后调用 `writeGraphVectors(knowledgeId)`。

### 6.4 EmbeddingServiceImpl 集成

```java
// EmbeddingServiceImpl.searchSimilarSql() 改造

public List<Map<String, Object>> searchSimilarSql(Long knowledgeId, float[] queryVector,
                                                    int topK, double threshold,
                                                    Map<String, Object> queryParams) {
    // Milvus 路由
    if (shouldRouteToMilvus(knowledgeId)) {
        List<Map<String, Object>> results = searchMilvus(knowledgeId, queryVector, topK, threshold, queryParams);

        // 图检索（可选）
        boolean useGraph = Boolean.TRUE.equals(queryParams.get("use_graph_retrieval"));
        if (useGraph && graphRetrievalUtil.isAvailable(knowledgeId)) {
            String queryText = getQueryParam(queryParams, "query_text", "");
            List<Map<String, Object>> graphResults = graphRetrievalUtil.graphRetrieve(
                    knowledgeId, queryText, results, queryParams);
            // RRF 融合
            float graphWeight = getFloatParam(queryParams, "graph_weight", 1.0f);
            results = rrfFusionWithGraph(results, graphResults, graphWeight, topK);
        }

        // Reranker（可选）
        results = applyReranker(results, queryParams, topK);
        return results;
    }

    // pgvector 路径（无图检索）
    // ... 现有逻辑不变
}
```

### 6.5 RRF 融合扩展

```java
/**
 * RRF 融合：基础结果 + 图结果
 */
private List<Map<String, Object>> rrfFusionWithGraph(
        List<Map<String, Object>> baseResults,
        List<Map<String, Object>> graphResults,
        float graphWeight, int topK) {

    int k = 60;
    Map<Long, double[]> scoreMap = new LinkedHashMap<>();
    Map<Long, Map<String, Object>> sourceMap = new LinkedHashMap<>();

    // 基础结果: weight = 1.0
    for (int i = 0; i < baseResults.size(); i++) {
        long chunkId = getChunkId(baseResults.get(i));
        double rrfScore = 1.0 / (k + i + 1);
        scoreMap.merge(chunkId, new double[]{rrfScore},
                (a, b) -> new double[]{a[0] + b[0]});
        sourceMap.putIfAbsent(chunkId, baseResults.get(i));
    }

    // 图结果: weight = graphWeight
    for (int i = 0; i < graphResults.size(); i++) {
        long chunkId = getChunkId(graphResults.get(i));
        double rrfScore = graphWeight / (k + i + 1);
        scoreMap.merge(chunkId, new double[]{rrfScore},
                (a, b) -> new double[]{a[0] + b[0]});
        sourceMap.putIfAbsent(chunkId, graphResults.get(i));
    }

    return scoreMap.entrySet().stream()
            .sorted((a, b) -> Double.compare(b.getValue()[0], a.getValue()[0]))
            .limit(topK)
            .map(e -> {
                Map<String, Object> row = new LinkedHashMap<>(sourceMap.get(e.getKey()));
                row.put("score", e.getValue()[0]);
                return row;
            })
            .toList();
}
```

---

## 七、Neo4j 子图查询 Cypher

复用 GraphServiceImpl 已有的 2-hop 模式，增加 path_limit 控制：

```cypher
-- 2-hop 子图查询
-- 参数: $entity_ids (seed entity ID 列表), $kb_label (知识库标签), $path_limit (路径上限)
MATCH (seed:Entity:`kb_label`)
WHERE seed.id IN $entity_ids
MATCH p = (seed)-[*1..2]-(n:`kb_label`)
WITH p LIMIT $path_limit
WITH collect(p) AS paths
UNWIND paths AS node_path
UNWIND nodes(node_path) AS node
WITH paths, collect(DISTINCT node) AS graph_nodes
UNWIND paths AS rel_path
UNWIND relationships(rel_path) AS rel
RETURN graph_nodes AS nodes, collect(DISTINCT rel) AS edges
```

**注意**：LightBot 的 Neo4j 实体节点用 `id` 属性（雪花算法字符串），而非 Yuxi 的 `entity_id`（hash）。Cypher 查询条件需适配。

---

## 八、依赖分析

### 8.1 已有依赖（无需新增）

| 依赖 | 版本 | 用途 |
|------|------|------|
| `milvus-sdk-java` | 2.6.13 | Entity/Triple Collection 管理 |
| `neo4j-java-driver` | 5.26.0 | 图结构查询 |
| Spring AI `EmbeddingModel` | 1.1.x | Entity/Triple 向量化 |
| `Neo4jUtil` | 自研 | Neo4j 事务管理 |
| `MilvusUtil` | 自研 | Milvus 操作封装 |

### 8.2 不需要的依赖

| Yuxi 依赖 | LightBot 是否需要 | 原因 |
|-----------|------------------|------|
| `python-igraph` | ❌ | 自实现迭代式 PPR，~50 行代码 |
| Neo4j GDS 插件 | ❌ | 不需要图算法库，自实现 PPR |
| PostgreSQL Entity/Triple 表 | ❌ | 元数据全部存 Neo4j，轻量化 |

---

## 九、迁移难点与风险

### 9.1 PPR 实现精度

**风险**：自实现的迭代式 PPR 与 `python-igraph` 的实现可能有数值差异。

**缓解**：PPR 的核心是矩阵迭代，公式标准，数值差异极小。可通过单元测试对齐。

### 9.2 Entity ID 映射

**风险**：Yuxi 用 `entity_id = hash(kb_id:name:label)`，LightBot 用雪花算法 `id`。Cypher 查询需用 LightBot 的 ID 体系。

**缓解**：GraphServiceImpl 的 Neo4j 节点已有 `id` 属性，直接使用即可。

### 9.3 Entity 向量化时机

**风险**：图谱抽取是异步任务，Entity 向量写入 Milvus 需在抽取完成后执行。

**缓解**：在 `GraphExtractionExecutor` 完成回调中调用 `writeGraphVectors()`。已有成熟的 Task 生命周期管理。

### 9.4 图谱数据量

**风险**：`graph_max_nodes=10000` 时，Neo4j 2-hop 子图查询 + PPR 迭代可能耗时较长。

**缓解**：
- Cypher 查询有 `LIMIT` 控制
- PPR 迭代 20 次，10000 节点的 HashMap 操作 <50ms
- 可设置超时保护

### 9.5 pgvector 不支持图检索

**风险**：图检索依赖 Milvus 的 Entity/Triple Collection，pgvector 类型知识库无法使用。

**缓解**：`use_graph_retrieval` 参数仅对 Milvus 类型生效，前端条件渲染。

---

## 十、实施优先级

### P1：基础能力（3-4 天）

1. **MilvusUtil 扩展** — Entity/Triple Collection 创建、写入、检索
2. **GraphServiceImpl 扩展** — 图谱抽取后自动写入 Milvus 向量
3. **GraphRetrievalUtil** — 图检索核心（种子权重 + Neo4j 子图 + PPR）
4. **EmbeddingServiceImpl 集成** — `searchSimilarSql()` 中集成图检索

### P2：前端参数（0.5 天）

5. **QueryParamsModal** — 图检索参数区域（`use_graph_retrieval` 开关 + 6 个 depend_on 参数）

### P3：验证与优化（1 天）

6. **端到端测试** — Milvus 知识库完成图谱抽取后，检索测试中启用图检索验证
7. **性能调优** — PPR 迭代次数、子图大小限制

---

## 十一、参数生效矩阵（图检索部分）

| 参数 | Milvus vector | Milvus keyword | Milvus hybrid | pgvector |
|------|---------------|----------------|---------------|----------|
| `use_graph_retrieval` | ✅ | ✅ | ✅ | ❌ 不支持 |
| `graph_entity_top_k` | ✅ | ✅ | ✅ | — |
| `graph_triple_top_k` | ✅ | ✅ | ✅ | — |
| `graph_max_nodes` | ✅ | ✅ | ✅ | — |
| `graph_top_k` | ✅ | ✅ | ✅ | — |
| `graph_weight` | ✅ | ✅ | ✅ | — |
| `ppr_damping` | ✅ | ✅ | ✅ | — |

---

## 十二、关键决策记录

| 决策 | 选择 | 理由 |
|------|------|------|
| PPR 实现方式 | 自实现迭代式 PPR | 避免引入 GDS 插件或 JGraphT 依赖，~50 行代码足够 |
| Entity 元数据存储 | 只用 Neo4j | 轻量化，不新建 PG 表，GraphServiceImpl 已将元数据存入 Neo4j |
| Entity 向量存储 | Milvus 新 Collection | 与 Yuxi 一致，复用 MilvusUtil，性能优于 Neo4j 向量索引 |
| 图谱抽取后向量化 | 异步写入 Milvus | 在 GraphExtractionExecutor 完成回调中执行，不阻塞主流程 |
| pgvector 图检索 | 不支持 | 图检索依赖 Milvus Collection，pgvector 无此能力 |
