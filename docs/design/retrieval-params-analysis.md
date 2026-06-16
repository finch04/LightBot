# 检索测试配置参数分析文档

> 参考 Yuxi 项目（Python）设计，分析 LightBot（Java）的检索参数配置方案与迁移可行性

---

## 一、参数全景对比

### 1.1 Yuxi 项目参数总览（Milvus 类型，18 个）

Yuxi 使用 `@dataclass MilvusRetrievalConfig` 声明式定义参数，每个字段的 `metadata` 字典即为 JSON Schema，前端据此动态渲染。

| # | 参数 Key | 类型 | 默认值 | 范围 | depend_on | 说明 |
|---|----------|------|--------|------|-----------|------|
| 1 | `search_mode` | str | `"vector"` | vector/keyword/hybrid | — | 检索模式 |
| 2 | `final_top_k` | int | `10` | 1–100 | — | 最终返回 chunk 数 |
| 3 | `similarity_threshold` | float | `0.0` | 0.0–1.0 | — | 相似度阈值 |
| 4 | `bm25_top_k` | int | `50` | 1–200 | — | BM25 候选数 |
| 5 | `vector_weight` | float | `0.7` | 0.0–1.0 | — | 混合检索向量权重 |
| 6 | `bm25_weight` | float | `0.3` | 0.0–1.0 | — | 混合检索 BM25 权重 |
| 7 | `bm25_drop_ratio_search` | float | `0.0` | 0.0–1.0 | — | BM25 稀疏项丢弃比例 |
| 8 | `include_distances` | bool | `True` | — | — | 结果是否含相似度分数 |
| 9 | `use_graph_retrieval` | bool | `False` | — | — | 启用图检索 |
| 10 | `graph_entity_top_k` | int | `10` | 1–100 | `use_graph_retrieval` | 实体召回数 |
| 11 | `graph_triple_top_k` | int | `10` | 1–100 | `use_graph_retrieval` | 三元组召回数 |
| 12 | `graph_max_nodes` | int | `10000` | 100–50000 | `use_graph_retrieval` | 2-hop 子图最大节点数 |
| 13 | `graph_top_k` | int | `20` | 1–200 | `use_graph_retrieval` | 图路径召回 chunk 数 |
| 14 | `graph_weight` | float | `1.0` | 0.0–5.0 | `use_graph_retrieval` | 图结果在 RRF 融合中的权重 |
| 15 | `ppr_damping` | float | `0.85` | 0.1–0.99 | `use_graph_retrieval` | PPR 阻尼系数 |
| 16 | `use_reranker` | bool | `False` | — | — | 启用重排序 |
| 17 | `reranker_model` | str | `""` | 动态选项 | `use_reranker` | 重排序模型（从 model_cache 动态获取） |
| 18 | `recall_top_k` | int | `50` | 10–200 | `use_reranker` | 重排序前候选数 |

### 1.2 LightBot 当前已实现参数（Milvus 类型，6 个）

| # | 参数 Key | 类型 | 默认值 | 状态 |
|---|----------|------|--------|------|
| 1 | `search_mode` | String | `"vector"` | ✅ 已实现（vector/keyword/hybrid 三种模式） |
| 2 | `final_top_k` | int | `10` | ✅ 已实现 |
| 3 | `similarity_threshold` | double | `0.0` | ✅ 已实现（仅 vector 模式生效） |
| 4 | `vector_weight` | float | `0.7` | ✅ 已实现（hybrid 模式） |
| 5 | `bm25_weight` | float | `0.3` | ✅ 已实现（hybrid 模式） |
| 6 | `bm25_top_k` | int | `30` | ✅ 已实现（hybrid/keyword 模式） |

### 1.3 LightBot 当前已实现参数（pgvector 类型，3 个）

| # | 参数 Key | 类型 | 默认值 | 状态 |
|---|----------|------|--------|------|
| 1 | `search_mode` | String | `"vector"` | ⚠️ 前端有控件但后端忽略（pgvector 只做向量检索） |
| 2 | `final_top_k` | int | `5` | ✅ 已实现 |
| 3 | `similarity_threshold` | double | `0.5` | ✅ 已实现 |

### 1.4 差距分析

| 参数 | Yuxi | LightBot 现状 | 迁移难度 | 优先级 |
|------|------|--------------|----------|--------|
| `bm25_drop_ratio_search` | ✅ | ❌ 未暴露 | ⭐ 简单 | P2 |
| `include_distances` | ✅ | ❌ 未暴露 | ⭐ 简单 | P3 |
| `use_reranker` + `reranker_model` + `recall_top_k` | ✅ | ❌ 未实现 | ⭐⭐⭐ 困难 | P1 |
| `use_graph_retrieval` + 6 个图参数 | ✅ | ❌ 未实现 | ⭐⭐⭐⭐ 很困难 | P3 |
| pgvector keyword/hybrid 模式 | N/A | ❌ 设计文档有但未实现 | ⭐⭐ 中等 | P1 |

---

## 二、各参数详细设计

### 2.1 基础检索参数（已实现，需补全 overrides 传递）

**问题**：当前 `RagServiceImpl.buildSearchParams()` 只合并了 `search_mode`，hybrid 相关参数（`vector_weight`/`bm25_weight`/`bm25_top_k`）的运行时覆盖未传递到 Milvus 搜索层。

**修复方案**：`buildSearchParams()` 应合并所有 overrides 键值到 params Map。

```java
// RagServiceImpl.buildSearchParams() 当前实现（有缺陷）
private Map<String, Object> buildSearchParams(Knowledge knowledge,
                                               Map<String, Object> overrides,
                                               String question) {
    Map<String, Object> params = new HashMap<>(parseJson(knowledge.getQueryParams()));
    // ❌ 只合并了 search_mode
    if (overrides != null && overrides.get("search_mode") instanceof String s) {
        params.put("search_mode", s);
    }
    params.put("query_text", question);
    return params;
}

// 修复后
private Map<String, Object> buildSearchParams(Knowledge knowledge,
                                               Map<String, Object> overrides,
                                               String question) {
    Map<String, Object> params = new HashMap<>(parseJson(knowledge.getQueryParams()));
    // ✅ 合并所有 overrides（运行时覆盖优先）
    if (overrides != null) {
        params.putAll(overrides);
    }
    params.put("query_text", question);
    return params;
}
```

### 2.2 BM25 参数扩展

#### `bm25_drop_ratio_search`（BM25 稀疏项丢弃比例）

**作用**：控制 BM25 检索时丢弃低权重稀疏项的比例，用于性能优化。值越大，丢弃越多，检索越快但可能损失精度。

**后端实现**：Milvus SDK 的 `SparseFloatVec` 构造时无法直接传此参数，它是在 Collection 创建时作为索引参数设置的。运行时修改需要重建索引，不现实。

**结论**：此参数在 Yuxi 中也是 Collection 级别配置，不适合作为运行时可调参数。建议在创建 Collection 时固定为 `0.0`（不丢弃），不暴露到前端。

#### `include_distances`（结果含相似度分数）

**作用**：控制返回结果是否包含 `score` 字段。

**后端实现**：当前 `MilvusUtil.convertResults()` 始终返回 `score`。此参数仅影响返回数据的字段裁剪，对性能无实质影响。

**结论**：保持始终返回 `score`，不暴露此参数。前端可选择性展示。

### 2.3 Reranker（重排序）— 高优先级

#### 参数定义

| 参数 | 类型 | 默认值 | 范围 | depend_on | 说明 |
|------|------|--------|------|-----------|------|
| `use_reranker` | boolean | `false` | — | — | 启用重排序 |
| `reranker_model` | String | `""` | 动态选项 | `use_reranker=true` | 重排序模型标识 |
| `recall_top_k` | int | `50` | 10–200 | `use_reranker=true` | 重排序前候选数（≥ final_top_k） |

#### 技术实现分析

**Yuxi 实现**：
- 通过 `get_reranker(model_spec)` 加载 reranker 模型
- 调用 `acompute_score(query, documents, normalization=True)` 获取重排序分数
- 按 `rerank_score` 降序排列，取 `final_top_k` 条返回

**Java 迁移方案**：

Spring AI 1.1.x 没有内置 Reranker 抽象。可选方案：

| 方案 | 实现方式 | 优点 | 缺点 |
|------|---------|------|------|
| **A. 自定义 Reranker 接口** | 定义 `Reranker` 接口，按模型类型实现（DashScope/Cohere/Jina） | 灵活，可扩展 | 需要为每个 reranker 提供商写适配器 |
| **B. 复用 EmbeddingModel 做相似度** | 用 EmbeddingModel 分别 encode query 和 documents，计算余弦相似度排序 | 零额外依赖 | 效果远不如专业 reranker |
| **C. HTTP 调用外部 Reranker API** | 直接 HTTP 调用 DashScope/Cohere 的 rerank API | 实现简单 | 网络延迟，需管理 API Key |

**推荐方案 A**，理由：
1. 项目已有 `ModelProvider` 体系，可复用 provider 路由
2. DashScope 有 `text-rerank-v1` 模型，HTTP API 简单
3. 接口抽象后可扩展 Cohere/Jina 等

```java
// Reranker 接口定义
public interface Reranker {
    String getModelName();
    List<RerankResult> rerank(String query, List<String> documents, int topK);
}

// DashScope Reranker 实现
@Component
public class DashScopeReranker implements Reranker {
    // POST https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank
    // body: { model, input: { query, documents }, parameters: { top_n, return_documents } }
}
```

**运行时流程**：
1. `use_reranker=false` → 跳过，现有逻辑不变
2. `use_reranker=true` → 先以 `recall_top_k` 召回候选 → 调用 reranker → 取 `final_top_k` 返回

```java
// EmbeddingServiceImpl.searchMilvus() 中增加 reranker 逻辑
private List<Map<String, Object>> searchMilvus(Long knowledgeId, float[] queryVector,
                                                int topK, double threshold,
                                                Map<String, Object> queryParams) {
    boolean useReranker = Boolean.TRUE.equals(queryParams.get("use_reranker"));
    int recallTopK = useReranker
        ? Math.max(topK, getIntParam(queryParams, "recall_top_k", 50))
        : topK;

    // 1. 召回（recallTopK 条候选）
    List<Map<String, Object>> candidates = doSearch(..., recallTopK, ...);

    // 2. 重排序（可选）
    if (useReranker && reranker != null) {
        candidates = reranker.rerank(queryText, candidates, topK);
    }

    // 3. 截断 + 阈值过滤
    return candidates.stream()
        .filter(r -> getScore(r) >= threshold)
        .limit(topK)
        .toList();
}
```

**依赖**：
- DashScope Rerank API（已有 DashScope API Key，无需额外配置）
- 或 Cohere Rerank API（需额外 API Key）

### 2.4 图检索（Graph Retrieval）— 低优先级，暂不实现

#### 参数定义（6 个）

| 参数 | 类型 | 默认值 | depend_on |
|------|------|--------|-----------|
| `use_graph_retrieval` | boolean | `false` | — |
| `graph_entity_top_k` | int | `10` | `use_graph_retrieval` |
| `graph_triple_top_k` | int | `10` | `use_graph_retrieval` |
| `graph_max_nodes` | int | `10000` | `use_graph_retrieval` |
| `graph_top_k` | int | `20` | `use_graph_retrieval` |
| `graph_weight` | float | `1.0` | `use_graph_retrieval` |
| `ppr_damping` | float | `0.85` | `use_graph_retrieval` |

#### 技术实现分析

**Yuxi 实现**：
- Neo4j 存储实体/三元组图谱
- Milvus 中额外存储 entity embedding 和 triple embedding
- 检索时：向量检索 → 实体/三元组检索 → 构建种子节点 → PPR（Personalized PageRank）→ RRF 融合

**Java 迁移难度**：⭐⭐⭐⭐ 很困难

| 依赖 | 现状 | 差距 |
|------|------|------|
| Neo4j 图存储 | ✅ 已有 `Neo4jUtil` | 可用 |
| Entity/Triple embedding 存储 | ❌ Milvus Collection 无 entity/triple 字段 | 需扩展 Collection Schema |
| PPR 算法 | ❌ 无 | 需引入图算法库或 Neo4j GDS |
| RRF 融合 | ❌ 无 | 需实现 |
| 前端条件联动 | ❌ `QueryParamsModal` 无 depend_on 支持 | 需改造前端组件 |

**结论**：图检索涉及 Neo4j + Milvus 双存储协同、PPR 算法实现、RRF 融合排序，工程量大且依赖知识图谱抽取（本项目已有 GraphService，但与检索融合未实现）。**本期不实现**，仅预留参数位。

### 2.5 pgvector 检索模式扩展 — 高优先级

#### 当前状态

pgvector 类型知识库只支持向量检索（cosine），`search_mode` 参数在前端可见但后端忽略。

#### 需要实现的模式

| 模式 | 实现方式 | 依赖 |
|------|---------|------|
| `vector` | 现有 `EmbeddingMapper.searchSimilarWithThreshold()` | 无 |
| `keyword` | PostgreSQL `tsvector` + `plainto_tsquery()` + GIN 索引 | 需新增 `content_tsv` 列 + 触发器 |
| `hybrid` | 向量检索 + 全文检索两次查询 → 应用层 RRF 融合 | 需实现 RRF 算法 |

#### keyword 模式实现

**数据库变更**：
```sql
-- chunk 表新增 tsvector 列
ALTER TABLE chunk ADD COLUMN IF NOT EXISTS content_tsv tsvector;
UPDATE chunk SET content_tsv = to_tsvector('simple', content) WHERE content_tsv IS NULL;
CREATE INDEX IF NOT EXISTS idx_chunk_content_tsv ON chunk USING GIN(content_tsv);

-- 触发器：插入/更新时自动维护 content_tsv
CREATE OR REPLACE FUNCTION update_chunk_content_tsv() RETURNS TRIGGER AS $$
BEGIN
    NEW.content_tsv := to_tsvector('simple', NEW.content);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_chunk_content_tsv
    BEFORE INSERT OR UPDATE OF content ON chunk
    FOR EACH ROW EXECUTE FUNCTION update_chunk_content_tsv();
```

**Mapper 新增方法**：
```java
// EmbeddingMapper 新增
@Select("""
    SELECT c.id AS chunk_id, c.content, c.document_id,
           ts_rank(c.content_tsv, plainto_tsquery('simple', #{query})) AS score
    FROM chunk c
    WHERE c.knowledge_id = #{knowledgeId}
      AND c.content_tsv @@ plainto_tsquery('simple', #{query})
    ORDER BY score DESC
    LIMIT #{topK}
    """)
List<Map<String, Object>> searchByFullText(@Param("query") String query,
                                            @Param("knowledgeId") Long knowledgeId,
                                            @Param("topK") int topK);
```

#### hybrid 模式实现（RRF 融合）

```java
// 应用层 RRF 融合
private List<Map<String, Object>> rrfFusion(List<Map<String, Object>> vectorResults,
                                              List<Map<String, Object>> keywordResults,
                                              float vectorWeight, float keywordWeight,
                                              int topK) {
    int k = 60; // RRF 常量
    Map<Long, double[]> scoreMap = new LinkedHashMap<>(); // chunkId → [rrfScore, originalScore]

    for (int i = 0; i < vectorResults.size(); i++) {
        long chunkId = getChunkId(vectorResults.get(i));
        double rrfScore = vectorWeight / (k + i + 1);
        scoreMap.merge(chunkId, new double[]{rrfScore, getScore(vectorResults.get(i))},
                       (a, b) -> new double[]{a[0] + b[0], Math.max(a[1], b[1])});
    }

    for (int i = 0; i < keywordResults.size(); i++) {
        long chunkId = getChunkId(keywordResults.get(i));
        double rrfScore = keywordWeight / (k + i + 1);
        scoreMap.merge(chunkId, new double[]{rrfScore, getScore(keywordResults.get(i))},
                       (a, b) -> new double[]{a[0] + b[0], Math.max(a[1], b[1])});
    }

    return scoreMap.entrySet().stream()
        .sorted((a, b) -> Double.compare(b.getValue()[0], a.getValue()[0]))
        .limit(topK)
        .map(e -> buildResult(e.getKey(), e.getValue()[0]))
        .toList();
}
```

---

## 三、参数存储与合并机制

### 3.1 Yuxi 的三层设计

```
┌─────────────────────────────────────────────────────────────┐
│  第一层：Schema 声明（代码定义）                               │
│  @dataclass MilvusRetrievalConfig → _retrieval_config_options │
│  定义参数 key/type/default/min/max/depend_on                 │
├─────────────────────────────────────────────────────────────┤
│  第二层：持久化存储（DB JSONB）                                │
│  knowledge.query_params = {"options": {key: value, ...}}     │
│  GET /query-params 返回 schema + 已保存值（default 替换）     │
│  PUT /query-params 写入 DB                                    │
├─────────────────────────────────────────────────────────────┤
│  第三层：运行时合并（查询时）                                  │
│  query_params = self._get_query_params(kb_id)  // DB 持久化   │
│  merged = {**query_params, **kwargs}           // 前端覆盖     │
│  前端 meta 参数优先级 > DB 默认配置                            │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 LightBot 当前实现

```
┌─────────────────────────────────────────────────────────────┐
│  第一层：Schema 声明 ❌ 缺失                                  │
│  前端 QueryParamsModal.vue 硬编码字段，无 schema 驱动         │
├─────────────────────────────────────────────────────────────┤
│  第二层：持久化存储 ✅ 已有                                    │
│  knowledge.query_params JSONB 列                              │
│  GET /{id}/query-params 返回原始 JSON                         │
│  PUT /{id}/query-params 直接覆盖                              │
├─────────────────────────────────────────────────────────────┤
│  第三层：运行时合并 ⚠️ 部分实现                                │
│  buildSearchParams() 只合并 search_mode                       │
│  resolveTopK/resolveThreshold 有 4 级优先级                   │
│  但 hybrid 参数（vector_weight 等）的 overrides 未传递        │
└─────────────────────────────────────────────────────────────┘
```

### 3.3 改进方案

#### 方案选择

| 方案 | 描述 | 优点 | 缺点 |
|------|------|------|------|
| **A. 保持现有结构，补全合并逻辑** | 不引入 schema 驱动，前端硬编码，后端补全 overrides 传递 | 改动最小，快速交付 | 前后端参数不同步风险 |
| **B. 引入 schema 驱动（类 Yuxi）** | 后端返回 `ParamConfig[]`，前端动态渲染 | 参数定义单一数据源，自动联动 | 需改造前端组件，工作量大 |
| **C. 混合方案** | 保持前端硬编码，但后端用 `ParamConfig` 定义 schema 做校验 | 兼顾校验和开发效率 | 收益不大 |

**推荐方案 A**，理由：
1. 当前参数数量少（Milvus 6 个 + pgvector 3 个），硬编码可维护
2. Yuxi 的 18 个参数中，图检索 7 个本期不做，reranker 3 个新增即可
3. 前端 `QueryParamsModal` 已有按类型条件渲染的逻辑，扩展即可
4. schema 驱动的收益在参数 > 15 个时才明显

#### 合并逻辑统一

```java
/**
 * 统一参数合并：运行时覆盖 > 持久化配置 > 代码默认值
 * 替代当前分散在 resolveTopK/resolveThreshold/buildSearchParams 的逻辑
 */
public Map<String, Object> mergeSearchParams(Knowledge knowledge,
                                               Map<String, Object> overrides,
                                               String question) {
    // 1. 持久化配置
    Map<String, Object> params = new HashMap<>(parseQueryParams(knowledge));

    // 2. 运行时覆盖（全量合并，不仅仅是 search_mode）
    if (overrides != null) {
        params.putAll(overrides);
    }

    // 3. 注入查询文本（keyword/hybrid 模式需要）
    params.put("query_text", question);

    return params;
}

// 使用处统一取值
int topK = getIntParam(params, "final_top_k", knowledgeType == MILVUS ? 10 : 5);
double threshold = getDoubleParam(params, "similarity_threshold", knowledgeType == MILVUS ? 0.0 : 0.5);
```

---

## 四、前端参数配置

### 4.1 当前 QueryParamsModal 参数

| 参数 | pg 显示 | milvus 显示 | 备注 |
|------|---------|-------------|------|
| `search_mode` | ❌ | ✅ | 仅 Milvus 显示 |
| `final_top_k` | ✅ | ✅ | |
| `similarity_threshold` | ✅ | ✅ | |
| `vector_weight` | ❌ | ✅ | 仅 Milvus hybrid 模式 |
| `bm25_weight` | ❌ | ✅ | 仅 Milvus hybrid 模式 |
| `bm25_top_k` | ❌ | ✅ | 仅 Milvus hybrid 模式 |

### 4.2 需新增的参数控件

#### Reranker 参数（Milvus + pgvector 通用）

```vue
<!-- QueryParamsModal.vue 新增 -->

<!-- use_reranker 开关 -->
<a-form-item label="启用重排序">
  <a-switch v-model:checked="form.use_reranker" />
</a-form-item>

<!-- reranker_model 选择（depend on use_reranker） -->
<a-form-item v-if="form.use_reranker" label="重排序模型">
  <a-select v-model:value="form.reranker_model" :options="rerankerModelOptions" />
</a-form-item>

<!-- recall_top_k（depend on use_reranker） -->
<a-form-item v-if="form.use_reranker" label="召回候选数">
  <a-input-number v-model:value="form.recall_top_k" :min="10" :max="200" />
</a-form-item>
```

**reranker 模型选项来源**：后端 `GET /knowledge/{id}/reranker-models` 接口，从 `ModelProvider` 表查询 type=`rerank` 的模型。

#### pgvector 搜索模式

```vue
<!-- pg 类型也显示 search_mode -->
<a-form-item label="检索模式">
  <a-select v-model:value="form.search_mode">
    <a-select-option value="vector">向量检索</a-select-option>
    <a-select-option value="keyword">全文检索</a-select-option>
    <a-select-option value="hybrid">混合检索</a-select-option>
  </a-select>
</a-form-item>

<!-- hybrid 模式显示权重（pg 也需要） -->
<a-form-item v-if="form.search_mode === 'hybrid'" label="向量权重">
  <a-input-number v-model:value="form.vector_weight" :min="0" :max="1" :step="0.1" />
</a-form-item>
<a-form-item v-if="form.search_mode === 'hybrid'" label="关键词权重">
  <a-input-number v-model:value="form.keyword_weight" :min="0" :max="1" :step="0.1" />
</a-form-item>
```

### 4.3 默认值差异

| 参数 | pg 默认值 | milvus 默认值 | Yuxi 默认值 |
|------|-----------|--------------|-------------|
| `final_top_k` | 5 | 10 | 10 |
| `similarity_threshold` | 0.5 | 0.0 | 0.0（运行时回退 0.2） |
| `vector_weight` | 0.7 | 0.7 | 0.7 |
| `bm25_weight`/`keyword_weight` | 0.3 | 0.3 | 0.3 |
| `bm25_top_k` | — | 30 | 50 |
| `recall_top_k` | 50 | 50 | 50 |

---

## 五、依赖与中间件

### 5.1 已有依赖

| 依赖 | 版本 | 用途 | 状态 |
|------|------|------|------|
| `milvus-sdk-java` | 2.6.13 | Milvus 向量库操作 | ✅ 已集成 |
| PostgreSQL | — | pgvector 向量 + 全文检索 | ✅ 已有 |
| DashScope API | — | Embedding 模型 | ✅ 已有 |
| Neo4j | 5.26.0 | 知识图谱存储 | ✅ 已有（图检索用） |

### 5.2 需新增的依赖/配置

| 依赖 | 用途 | 是否必须 | 备注 |
|------|------|----------|------|
| DashScope Rerank API | 重排序模型 | P1 | 复用已有 DashScope API Key，无需新依赖 |
| `chunk.content_tsv` 列 + GIN 索引 | pgvector 全文检索 | P1 | 需要 DDL 变更 + 触发器 |
| `chunk.content_tsv` 数据回填 | 现有 chunk 的 tsvector 初始化 | P1 | 一次性 UPDATE |

### 5.3 不需要的依赖

| Yuxi 依赖 | LightBot 是否需要 | 原因 |
|-----------|------------------|------|
| Neo4j GDS（图算法库） | ❌ 不需要 | 图检索本期不做 |
| Cohere/Jina Rerank API | ❌ 不需要 | 用 DashScope 即可 |
| langchain Reranker | ❌ 不需要 | Java 自实现简单 |

---

## 六、实施优先级与计划

### P1：核心参数补全（1-2 天）

1. **修复 `buildSearchParams()` overrides 合并** — 所有 overrides 键值都应传递到 Milvus 搜索层
2. **统一参数合并逻辑** — 合并 `resolveTopK`/`resolveThreshold`/`buildSearchParams` 为一个 `mergeSearchParams()`
3. **pgvector keyword 模式** — 新增 `content_tsv` 列 + 触发器 + `searchByFullText` Mapper 方法
4. **pgvector hybrid 模式** — 实现 RRF 融合（应用层）
5. **前端 pg 类型显示 search_mode** — 去掉 `isMilvus` 条件限制

### P2：Reranker 集成（2-3 天）

1. **定义 `Reranker` 接口** — `rerank(query, documents, topK)`
2. **实现 `DashScopeReranker`** — 调用 DashScope `text-rerank-v1` API
3. **`EmbeddingServiceImpl` 集成** — `use_reranker=true` 时先召回 `recall_top_k` 再重排序
4. **前端 reranker 控件** — `use_reranker` 开关 + `reranker_model` 下拉 + `recall_top_k` 输入
5. **后端 reranker 模型列表接口** — `GET /knowledge/{id}/reranker-models`

### P3：优化项（后续）

1. `bm25_drop_ratio_search` — 创建 Collection 时固定，不暴露
2. `include_distances` — 始终返回 score，不暴露
3. 图检索 — 需要 Neo4j + Milvus 双存储协同，工程量大，单独排期

---

## 七、关键决策记录

| 决策 | 选择 | 理由 |
|------|------|------|
| 参数 schema 驱动 vs 硬编码 | **硬编码** | 参数 < 15 个，硬编码简单可控，schema 驱动收益不大 |
| Reranker 方案 | **自定义接口 + DashScope API** | 复用已有 API Key，Spring AI 无内置 reranker |
| 图检索 | **本期不做** | 依赖 PPR 算法 + Neo4j GDS + 双存储融合，工程量独立排期 |
| `bm25_drop_ratio_search` | **不暴露** | Collection 级别配置，运行时修改需重建索引 |
| pgvector 全文检索 | **tsvector + GIN** | PostgreSQL 原生能力，无额外依赖 |
| 混合检索融合算法 | **RRF（Reciprocal Rank Fusion）** | 业界标准，Yuxi 也用 RRF，实现简单 |
