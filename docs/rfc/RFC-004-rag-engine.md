# RFC-004: RAG Engine

| 字段 | 值 |
|------|------|
| RFC 编号 | 004 |
| 标题 | RAG Engine |
| 状态 | Draft |
| 作者 | LightBot Team |
| 创建日期 | 2025-05-19 |
| 最后更新 | 2025-05-19 |
| 关联模块 | lightbot-core/knowledge |

---

## 1. 背景

LLM 的知识受限于训练数据的截止时间和幻觉问题。RAG（Retrieval-Augmented Generation）通过在推理前检索相关知识，将外部知识注入 LLM 的 Context，是解决知识时效性和准确性的主流方案。

LightBot 需要一套**从文档摄入、向量存储、检索排序到 Prompt 注入**的完整 RAG 链路，支撑知识库问答、文档对话、企业知识管理等场景。

---

## 2. 问题定义

### 2.1 核心问题

**如何构建一个端到端的 RAG 引擎，使其满足：**

1. **文档处理** — 支持 PDF、Word、Markdown、HTML 等格式的文档解析与分块
2. **向量存储** — 文档块向量化后存储到 pgvector，支持高效检索
3. **检索策略** — 支持语义检索、关键词检索、混合检索，结果重排序
4. **Prompt 注入** — 检索结果自动注入 Agent 的 Context
5. **知识库管理** — 多知识库隔离、文档增删改查、权限控制
6. **检索质量可观测** — 检索结果的相似度、命中率可被追踪

### 2.2 约束条件

| 约束 | 说明 |
|------|------|
| 向量数据库 | pgvector（PostgreSQL 扩展），不引入外部向量数据库 |
| Embedding 模型 | 通过 Spring AI 统一接入（OpenAI / 通义千问） |
| 文档大小 | 单文档 ≤ 50MB，知识库 ≤ 10GB |
| 并发检索 | 单实例 50+ QPS |

---

## 3. 设计目标

| 优先级 | 目标 | 衡量标准 |
|--------|------|----------|
| P0 | **文档解析** | 支持 PDF / Word / Markdown / TXT |
| P0 | **分块策略** | 递归字符分块 + 重叠窗口 |
| P0 | **向量存储** | pgvector 存储 + HNSW 索引 |
| P0 | **语义检索** | Embedding 相似度 Top-K 检索 |
| P1 | **混合检索** | 语义 + BM25 关键词混合，RRF 融合 |
| P1 | **重排序** | 检索结果 Rerank 提升精度 |
| P1 | **知识库 CRUD** | 多知识库管理、文档增删改查 |
| P2 | **增量更新** | 文档变更后增量重新索引 |
| P2 | **检索追踪** | 记录每次检索的 query / results / scores |

---

## 4. 非目标

| 非目标 | 原因 |
|--------|------|
| 图片 / 音视频解析 | 依赖重，可用第三方 API 替代 |
| 知识图谱构建 | 复杂度高，单独规划 |
| 自动文档摘要 | 可通过 LLM 节点在 Workflow 中实现 |
| 多模态 RAG | v1.0+ 规划 |
| 分布式向量数据库 | pgvector 满足初期需求 |

---

## 5. 核心架构

### 5.1 分层架构

```
┌─────────────────────────────────────────────────────────────┐
│                    RAG Engine Architecture                   │
├─────────────────────────────────────────────────────────────┤
│  API Layer                                                  │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐        │
│  │ Knowledge   │  │ Document    │  │ Retrieval   │        │
│  │ Base API    │  │ Upload API  │  │ API         │        │
│  └─────────────┘  └─────────────┘  └─────────────┘        │
├─────────────────────────────────────────────────────────────┤
│  Ingestion Pipeline                                         │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐     │
│  │ Document │ │ Text     │ │ Chunking │ │ Embedding│     │
│  │ Parser   │ │ Extractor│ │ Strategy │ │ Generator│     │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘     │
├─────────────────────────────────────────────────────────────┤
│  Retrieval Pipeline                                         │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐     │
│  │ Query    │ │ Semantic │ │ BM25     │ │ Reranker │     │
│  │ Rewrite  │ │ Search   │ │ Search   │ │          │     │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘     │
├─────────────────────────────────────────────────────────────┤
│  Storage Layer                                              │
│  ┌──────────────┐  ┌──────────────┐                       │
│  │  PostgreSQL  │  │   pgvector   │                       │
│  │  (文档元数据) │  │  (向量索引)  │                       │
│  └──────────────┘  └──────────────┘                       │
└─────────────────────────────────────────────────────────────┘
```

### 5.2 核心组件

| 组件 | 职责 |
|------|------|
| `DocumentParser` | 文档解析（PDF / Word / Markdown / HTML） |
| `TextChunker` | 文本分块策略（递归字符 / 语义 / 固定大小） |
| `EmbeddingGenerator` | 调用 Embedding 模型生成向量 |
| `VectorStore` | 向量存储与检索（pgvector） |
| `BM25Index` | 关键词索引（PostgreSQL 全文检索） |
| `RetrievalPipeline` | 检索管线编排（语义 + BM25 + Rerank） |
| `KnowledgeBaseManager` | 知识库 CRUD 管理 |

---

## 6. 文档处理流程

### 6.1 Ingestion Pipeline

```
文档上传
    │
    ▼
┌──────────────────┐
│ 1. Document Parse│  解析文档格式，提取纯文本
└────────┬─────────┘
         ▼
┌──────────────────┐
│ 2. Text Cleaning │  清理特殊字符、页眉页脚
└────────┬─────────┘
         ▼
┌──────────────────┐
│ 3. Chunking      │  按策略分块，保留重叠窗口
└────────┬─────────┘
         ▼
┌──────────────────┐
│ 4. Embedding     │  调用 Embedding 模型生成向量
└────────┬─────────┘
         ▼
┌──────────────────┐
│ 5. Store         │  存储到 PostgreSQL + pgvector
└──────────────────┘
```

### 6.2 分块策略

```java
public class RecursiveCharacterChunker implements TextChunker {

    private final int chunkSize;       // 块大小（字符数）
    private final int chunkOverlap;    // 重叠窗口
    private final List<String> separators; // 分隔符优先级

    @Override
    public List<TextChunk> chunk(String text, Metadata metadata) {
        List<TextChunk> chunks = new ArrayList<>();
        recursiveSplit(text, separators, chunks, metadata);
        return chunks;
    }

    private void recursiveSplit(String text, List<String> seps,
                                List<TextChunk> chunks, Metadata metadata) {
        if (text.length() <= chunkSize) {
            chunks.add(new TextChunk(text, metadata));
            return;
        }

        // 按优先级尝试分隔符
        for (String sep : seps) {
            String[] parts = text.split(sep);
            if (parts.length > 1) {
                StringBuilder buffer = new StringBuilder();
                for (String part : parts) {
                    if (buffer.length() + part.length() > chunkSize) {
                        if (buffer.length() > 0) {
                            chunks.add(new TextChunk(buffer.toString(), metadata));
                            // 保留重叠
                            buffer = new StringBuilder(
                                buffer.substring(Math.max(0, buffer.length() - chunkOverlap)));
                        }
                    }
                    buffer.append(part).append(sep);
                }
                if (buffer.length() > 0) {
                    chunks.add(new TextChunk(buffer.toString(), metadata));
                }
                return;
            }
        }

        // 无合适分隔符，硬切
        for (int i = 0; i < text.length(); i += chunkSize - chunkOverlap) {
            int end = Math.min(i + chunkSize, text.length());
            chunks.add(new TextChunk(text.substring(i, end), metadata));
        }
    }
}
```

---

## 7. 向量存储

### 7.1 pgvector 表结构

```sql
-- 知识库表
CREATE TABLE knowledge_base (
    id              VARCHAR(36) PRIMARY KEY,
    name            VARCHAR(128) NOT NULL,
    description     TEXT,
    embedding_model VARCHAR(64) NOT NULL,  -- 使用的 Embedding 模型
    chunk_size      INTEGER NOT NULL DEFAULT 512,
    chunk_overlap   INTEGER NOT NULL DEFAULT 50,
    tenant_id       VARCHAR(36) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 文档表
CREATE TABLE knowledge_document (
    id              VARCHAR(36) PRIMARY KEY,
    knowledge_base_id VARCHAR(36) NOT NULL,
    name            VARCHAR(256) NOT NULL,
    file_type       VARCHAR(16) NOT NULL,  -- pdf / docx / md / txt
    file_size       BIGINT NOT NULL,
    chunk_count     INTEGER NOT NULL DEFAULT 0,
    status          VARCHAR(16) NOT NULL,  -- PARSING / INDEXING / READY / FAILED
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    FOREIGN KEY (knowledge_base_id) REFERENCES knowledge_base(id)
);

-- 文档块表（含向量）
CREATE TABLE knowledge_chunk (
    id              VARCHAR(36) PRIMARY KEY,
    document_id     VARCHAR(36) NOT NULL,
    knowledge_base_id VARCHAR(36) NOT NULL,
    content         TEXT NOT NULL,
    embedding       vector(1536),          -- pgvector 向量列
    metadata        JSONB,                 -- 附加元数据（页码、章节等）
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    FOREIGN KEY (document_id) REFERENCES knowledge_document(id)
);

-- HNSW 向量索引
CREATE INDEX idx_chunk_embedding ON knowledge_chunk
    USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);

-- 全文检索索引（BM25）
CREATE INDEX idx_chunk_content_fts ON knowledge_chunk
    USING gin (to_tsvector('simple', content));
```

### 7.2 向量操作

```java
@Repository
public class PgVectorStore implements VectorStore {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void add(List<DocumentChunk> chunks) {
        String sql = "INSERT INTO knowledge_chunk (id, document_id, knowledge_base_id, content, embedding, metadata) "
                   + "VALUES (?, ?, ?, ?, ?::vector, ?::jsonb)";
        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                DocumentChunk chunk = chunks.get(i);
                ps.setString(1, chunk.getId());
                ps.setString(2, chunk.getDocumentId());
                ps.setString(3, chunk.getKnowledgeBaseId());
                ps.setString(4, chunk.getContent());
                ps.setString(5, vectorToString(chunk.getEmbedding()));
                ps.setString(6, JsonUtils.toJson(chunk.getMetadata()));
            }
            @Override
            public int getBatchSize() { return chunks.size(); }
        });
    }

    @Override
    public List<SearchResult> search(float[] queryEmbedding, String knowledgeBaseId, int topK) {
        String sql = "SELECT id, content, metadata, "
                   + "1 - (embedding <=> ?::vector) AS score "
                   + "FROM knowledge_chunk "
                   + "WHERE knowledge_base_id = ? "
                   + "ORDER BY embedding <=> ?::vector "
                   + "LIMIT ?";
        // pgvector cosine distance: <=>  operator
        return jdbcTemplate.query(sql,
            vectorToString(queryEmbedding), knowledgeBaseId,
            vectorToString(queryEmbedding), topK,
            (rs, rowNum) -> new SearchResult(
                rs.getString("id"),
                rs.getString("content"),
                rs.getDouble("score"),
                JsonUtils.fromJson(rs.getString("metadata"))
            ));
    }
}
```

---

## 8. 检索策略

### 8.1 检索管线

```
Query
    │
    ├──▶ [语义检索] pgvector cosine similarity → Top-K
    │
    ├──▶ [BM25 检索] PostgreSQL full-text search → Top-K
    │
    ▼
┌──────────────────┐
│ RRF 融合排序     │  Reciprocal Rank Fusion
└────────┬─────────┘
         ▼
┌──────────────────┐
│ Reranker (可选) │  Cross-encoder 重排序
└────────┬─────────┘
         ▼
┌──────────────────┐
│ Top-N 截取       │  返回最终结果
└──────────────────┘
```

### 8.2 混合检索 + RRF

```java
public class HybridRetrievalPipeline implements RetrievalPipeline {

    private final VectorStore vectorStore;
    private final BM25Index bm25Index;
    private final Reranker reranker;

    @Override
    public List<SearchResult> retrieve(String query, RetrievalConfig config) {
        // 1. 语义检索
        float[] queryEmbedding = embeddingService.embed(query);
        List<SearchResult> semanticResults = vectorStore.search(
            queryEmbedding, config.getKnowledgeBaseId(), config.getTopK());

        // 2. BM25 检索
        List<SearchResult> bm25Results = bm25Index.search(
            query, config.getKnowledgeBaseId(), config.getTopK());

        // 3. RRF 融合
        List<SearchResult> merged = reciprocalRankFusion(
            semanticResults, bm25Results, config.getRrfK());

        // 4. Rerank（可选）
        if (config.isRerankEnabled() && reranker != null) {
            merged = reranker.rerank(query, merged, config.getRerankTopN());
        }

        return merged.subList(0, Math.min(config.getTopN(), merged.size()));
    }

    /**
     * Reciprocal Rank Fusion
     * score = Σ 1 / (k + rank_i)
     */
    private List<SearchResult> reciprocalRankFusion(
            List<SearchResult> list1, List<SearchResult> list2, int k) {
        Map<String, Double> scoreMap = new HashMap<>();
        Map<String, SearchResult> resultMap = new HashMap<>();

        for (int i = 0; i < list1.size(); i++) {
            String id = list1.get(i).getId();
            scoreMap.merge(id, 1.0 / (k + i + 1), Double::sum);
            resultMap.put(id, list1.get(i));
        }
        for (int i = 0; i < list2.size(); i++) {
            String id = list2.get(i).getId();
            scoreMap.merge(id, 1.0 / (k + i + 1), Double::sum);
            resultMap.putIfAbsent(id, list2.get(i));
        }

        return scoreMap.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .map(e -> {
                SearchResult r = resultMap.get(e.getKey());
                return new SearchResult(r.getId(), r.getContent(), e.getValue(), r.getMetadata());
            })
            .collect(Collectors.toList());
    }
}
```

---

## 9. RAG 与 Agent 集成

### 9.1 自动检索注入

Agent 配置知识库后，每次对话自动检索并将结果注入 System Prompt：

```java
public class RAGAgentInterceptor implements AgentInterceptor {

    private final RetrievalPipeline retrievalPipeline;

    @Override
    public AgentRequest preHandle(AgentRequest request, AgentDefinition definition) {
        List<String> kbIds = definition.getKnowledgeBaseIds();
        if (kbIds == null || kbIds.isEmpty()) {
            return request;
        }

        // 检索相关知识
        String query = request.getLatestUserMessage();
        List<SearchResult> results = retrievalPipeline.retrieve(query,
            RetrievalConfig.builder()
                .knowledgeBaseIds(kbIds)
                .topK(5)
                .build());

        // 注入 Context
        StringBuilder context = new StringBuilder();
        context.append("以下是从知识库中检索到的参考资料：\n\n");
        for (int i = 0; i < results.size(); i++) {
            context.append(String.format("[%d] (相似度: %.2f) %s\n\n",
                i + 1, results.getScore(), results.get(i).getContent()));
        }

        request.addVariable("rag_context", context.toString());
        return request;
    }
}
```

### 9.2 Prompt 模板

```
你是一个智能助手。请根据以下参考资料回答用户问题。
如果参考资料中没有相关信息，请如实告知。

参考资料：
{{rag_context}}

用户问题：{{user_query}}
```

---

## 10. 风险分析

| 风险 | 等级 | 缓解措施 |
|------|------|----------|
| 文档解析失败（格式不支持/损坏） | 中 | 多解析器降级 + 友好错误信息 |
| 分块质量差导致检索不准 | 高 | 可配置分块策略 + 重叠窗口 |
| 向量维度与模型不匹配 | 高 | 维度校验 + 模型维度配置 |
| Embedding API 调用延迟 | 中 | 批量 Embedding + 缓存 |
| pgvector 大数据量性能下降 | 中 | HNSW 索引 + 分区表 |
| 检索结果不相关（幻觉注入） | 高 | Reranker + 相似度阈值过滤 |

---

## 11. 后续演进

| 阶段 | 能力 |
|------|------|
| v0.2 | 基础 RAG：文档解析 + 向量存储 + 语义检索 |
| v0.2+ | 混合检索 + Reranker |
| v0.3 | Knowledge Graph 集成 |
| v1.0 | 多模态 RAG（图片、表格） |

---

## 附录：配置项

```yaml
lightbot:
  knowledge:
    embedding:
      model: text-embedding-3-small
      dimensions: 1536
      batch-size: 100
    chunking:
      default-size: 512
      default-overlap: 50
      separators: ["\n\n", "\n", "。", ".", " "]
    retrieval:
      default-top-k: 10
      default-top-n: 5
      similarity-threshold: 0.7
      rrf-k: 60
    rerank:
      enabled: false
      model: rerank-v1
      top-n: 3
```
