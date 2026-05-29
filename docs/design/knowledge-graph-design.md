# 知识库 - 知识图谱功能技术设计文档

> 2026-05-29 | 状态：方案设计

---

## 一、功能定位

### 1.1 什么是知识图谱

知识图谱是以**实体（Entity）和关系（Relation）**为基本单元的结构化知识表示。与 Chunk 的"平铺文本"不同，知识图谱将知识组织成**有向图**：

```
[张三] --担任--> [技术总监] --隶属于--> [研发部]
  |                                      |
  |--> 参与项目 --> [LightBot] <--负责-- [李四]
```

### 1.2 与现有知识库的关系

**结论：知识图谱是知识库内的功能，不是独立模块。**

| 维度 | 独立模块 | 知识库内功能（选定） |
|------|---------|-------------------|
| 数据来源 | 自己管文档 | 复用知识库已有文档 |
| 用户心智 | 多一个顶级概念要理解 | "知识库有文档、问答对、图谱三种知识形态" |
| RAG 集成 | 需要跨模块调用 | 同一个知识库内，检索天然聚合 |
| 权限体系 | 新建一套 | 复用 KnowledgeRole |
| 参考 Yuxi | Yuxi 的图谱也是挂在知识库下（`kb_xxx` label） | 一致 |

**知识库的三种知识形态：**

```
Knowledge
├── Document → Chunk → Embedding     （非结构化：文档切片 + 向量）
├── QAPair → Embedding               （半结构化：问答对 + 向量）  ★ 待开发
└── KnowledgeGraph → Node/Edge       （结构化：实体关系图）       ★ 本文档
```

### 1.3 一个知识库是否需要多套知识图谱

**场景分析：**

一个知识库可能包含多种类型的文档（如技术文档 + 业务文档 + 人事文档），不同类型的文档实体关系差异很大：
- 技术文档：`[SpringBoot] --使用--> [MyBatis]`、`[Redis] --缓存--> [用户会话]`
- 业务文档：`[订单] --流转--> [支付]`、`[退款] --触发--> [财务审批]`
- 人事文档：`[张三] --担任--> [技术总监]`、`[研发部] --隶属于--> [技术中心]`

如果混合在一个图谱中，会导致：
1. **图谱稀释**：实体类型混杂，力导向图布局混乱，不美观
2. **检索噪声**：问技术问题时可能匹配到人事实体
3. **维护困难**：不同领域专家编辑同一张图容易冲突

**决策：暂不支持多套图谱，但预留扩展能力。**

理由：
1. **当前阶段**：知识图谱功能刚起步，先跑通单套图谱的完整链路（抽取 → 存储 → 检索 → 可视化 → 手动编辑）
2. **Neo4j Label 天然支持**：当前用 `kb_{knowledgeId}` 作为 Label，未来可扩展为 `kb_{knowledgeId}_{graphId}`，无需改数据模型
3. **用户可控**：通过「指定文档抽取」+「手动编辑」，用户可以在单套图谱内按需精炼特定领域的子图

**未来扩展路径（当需求明确时再做）：**

```
当前：Knowledge 1:1 KnowledgeGraph
  └── Neo4j Label: kb_{knowledgeId}

未来：Knowledge 1:N KnowledgeGraph（如果用户确实需要）
  ├── graph_id=1, name="技术架构图谱", Label: kb_{knowledgeId}_1
  ├── graph_id=2, name="业务流程图谱", Label: kb_{knowledgeId}_2
  └── graph_id=3, name="组织关系图谱", Label: kb_{knowledgeId}_3
```

需要新增 `knowledge_graph` 表：
```sql
CREATE TABLE knowledge_graph (
    id              BIGINT          NOT NULL,
    knowledge_id    BIGINT          NOT NULL,
    name            VARCHAR(128)    NOT NULL,
    description     TEXT,
    node_count      INTEGER         NOT NULL DEFAULT 0,
    edge_count      INTEGER         NOT NULL DEFAULT 0,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
```

### 1.4 核心价值

| 场景 | 纯向量检索（Chunk） | 图谱增强检索 |
|------|-------------------|-------------|
| "张三负责哪个项目？" | 检索到一段包含张三的文档片段，LLM 提取答案 | 直接从图谱找到 `张三 --负责--> 项目A`，答案精确 |
| "研发部有哪些人？" | 可能检索到组织架构文档，但需要 LLM 理解 | 图谱遍历 `研发部 <--隶属于-- [所有成员]`，结果完整 |
| "张三和李四什么关系？" | 需要多段文档拼接，LLM 容易遗漏 | 图谱多跳遍历，关系链清晰 |
| 多跳推理 | LLM 需要从多个 Chunk 中自行推理 | 图谱提供结构化路径，降低幻觉风险 |

### 1.5 独立图谱 vs 知识库内图谱（Yuxi 双模式分析）

Yuxi 项目中存在**两种不同的知识图谱功能**，定位和用途完全不同：

#### 模式 A：独立知识图谱（Standalone / Upload Type）

**定位**：全局级别的知识图谱，独立于任何知识库，由用户手动上传 JSONL 三元组文件维护。

| 维度 | 说明 |
|------|------|
| **数据来源** | 用户手动上传 JSONL 文件（`{"h": {...}, "r": {...}, "t": {...}}`） |
| **存储** | Neo4j，Label 为 `Entity:Upload`，全局唯一 |
| **向量索引** | 有，实体名 Embedding 存入 Neo4j vector index，支持语义相似度搜索 |
| **生命周期** | 独立管理，不随知识库删除而消失 |
| **前端入口** | 顶级页面 `/graph`，有数据库切换下拉框 |
| **Agent 工具** | `query_knowledge_graph`，返回三元组给 LLM 推理 |
| **适用场景** | 企业级通用知识图谱、跨知识库共享的领域知识 |

#### 模式 B：知识库内图谱（Knowledge Base / LightRAG Type）

**定位**：知识库的附属功能，由文档入库时 LLM 自动抽取，随知识库生命周期管理。

| 维度 | 说明 |
|------|------|
| **数据来源** | 文档入库时 LightRAG 自动抽取（Python LLM） |
| **存储** | Neo4j，Label 为 `kb_{id}`（如 `kb_abc123`），每个 KB 独立 |
| **向量索引** | 无（实体名搜索用 keyword match） |
| **生命周期** | 绑定知识库，KB 删除则图谱删除 |
| **前端入口** | 知识库详情页 Tab（仅 LightRAG 类型 KB 显示） |
| **Agent 工具** | LightRAG `aquery` 的 `retrieval_content_scope` 参数 |
| **适用场景** | 特定文档集的结构化知识表示，增强 RAG 检索 |

#### 两种模式对比

| 对比维度 | 独立图谱（Upload） | 知识库内图谱（LightRAG） |
|---------|-------------------|------------------------|
| 数据来源 | 手动上传 JSONL | LLM 自动抽取 |
| 数据质量 | 高（人工维护） | 中（LLM 抽取有噪声） |
| 维护成本 | 高（需人工整理三元组） | 低（全自动） |
| 向量搜索 | 支持（cosine similarity） | 不支持 |
| 数据隔离 | 全局共享 | 按知识库隔离 |
| 查询场景 | Agent 推理、通用问答 | RAG 检索增强 |
| 可编辑性 | 完全可编辑 | 只读（自动生成） |

#### LightBot 的选择

**LightBot 当前实现的是「模式 B：知识库内图谱」**，与 Yuxi 的 LightRAG 图谱定位一致：
- 挂在知识库下，用 `kb_{knowledgeId}` 做 Label 隔离
- 支持 AI 自动抽取 + 手动编辑
- 未来可扩展「模式 A：独立图谱」作为全局知识管理功能

**是否需要独立图谱？**

| 场景 | 是否需要独立图谱 | 理由 |
|------|----------------|------|
| 企业内部知识管理 | 是 | 跨知识库共享的领域知识（如组织架构、产品体系）需要全局维护 |
| 单一知识库使用 | 否 | 知识库内图谱已满足需求 |
| Agent 推理增强 | 是 | Agent 需要全局知识图谱作为推理依据，不应局限于单个 KB |
| 多租户 SaaS | 视情况 | 每个租户一个独立图谱，或按知识库隔离 |

**结论**：当前聚焦模式 B（知识库内图谱），待产品成熟后根据用户反馈决定是否增加模式 A（独立图谱）。Neo4j Label 机制天然支持两种模式共存。

### 1.6 与 Yuxi 的对比

| 维度 | Yuxi | LightBot 设计 |
|------|------|--------------|
| 图谱来源 | LightRAG 自动抽取（Python LLM） | 自研抽取（SpringAI LLM）+ 手动导入 |
| 图数据库 | Neo4j 5.26 | Neo4j 5.x（同） |
| 存储隔离 | 每个 KB 一个 Neo4j label（`kb_xxx`） | 每个 KB 一个 Neo4j label（`kb_{id}`）（同） |
| 检索模式 | LightRAG mix 模式 | QA 优先 → 图谱增强 → Chunk 兜底（三层漏斗） |
| 可视化 | @antv/g6 | @antv/g6（同） |
| 向量索引 | Neo4j vector index on entity name | Neo4j vector index on entity name（同） |

---

## 二、业务流程

### 2.1 图谱构建

#### 2.1.1 自动抽取（核心流程）

```
文档上传 → 切片完成（Document status=COMPLETED）
  ↓
触发图谱抽取（异步任务）
  ↓
对每个 Chunk 调用 LLM 抽取实体和关系
  ↓
LLM 返回结构化三元组：[{head, relation, tail, head_type, tail_type}]
  ↓
实体去重（同名同类型合并，更新描述）
关系去重（同 head+relation+tail 合并）
  ↓
写入 Neo4j（MERGE 语句，幂等）
  ↓
对实体名做 Embedding，写入 Neo4j vector index
  ↓
更新知识库统计信息
```

**LLM 抽取 Prompt 模板：**

```
请从以下文本中抽取实体和关系，输出 JSON 数组格式。

要求：
1. 实体：提取人名、组织、职位、项目、产品、地点、技术、概念等有明确含义的名词
2. 关系：提取实体间的语义关系，用动词短语描述（如"负责"、"隶属于"、"使用"）
3. 每个三元组格式：{"head": "实体A", "head_type": "类型", "relation": "关系", "tail": "实体B", "tail_type": "类型"}
4. 实体名称保持原文，不要翻译或缩写
5. 关系方向要正确：A 做了某事指向 B，则 head=A, tail=B

文本：
{chunk_content}

输出格式（纯 JSON 数组，不要额外文字）：
[{"head": "...", "head_type": "...", "relation": "...", "tail": "...", "tail_type": "..."}]
```

#### 2.1.2 手动导入

用户上传 JSONL 文件，每行一个三元组：

```jsonl
{"head": "张三", "head_type": "人物", "relation": "担任", "tail": "技术总监", "tail_type": "职位"}
{"head": "技术总监", "head_type": "职位", "relation": "隶属于", "tail": "研发部", "tail_type": "组织"}
```

#### 2.1.3 手动创建

用户在前端图谱编辑器中：
- 添加节点（名称 + 类型 + 描述）
- 添加边（起始节点 + 关系 + 目标节点）
- 支持拖拽连线

### 2.2 图谱检索（集成到 RAG）

```
用户提问
  ↓
┌─ QA Pair 检索（score ≥ 0.85 → 直接返回）     ← 优先级最高
├─ 图谱检索（实体匹配 + 关系遍历）              ← 本文新增
└─ Chunk 向量检索                               ← 兜底
  ↓
图谱检索结果 + Chunk 检测结果合并
  ↓
注入 LLM 上下文，生成回答
```

**图谱检索策略：**

1. **实体识别**：从用户问题中用 LLM 提取关键实体名
2. **实体匹配**：在 Neo4j 中通过 vector index 或 name 精确匹配找到实体节点
3. **子图展开**：从匹配实体出发，1-2 跳遍历获取相关实体和关系
4. **结果格式化**：将子图转为文本三元组注入 LLM 上下文

```
图谱检索结果示例：
根据知识图谱查询到以下信息：
- 张三 担任 技术总监
- 技术总监 隶属于 研发部
- 张三 负责 LightBot 项目
```

### 2.3 图谱可视化

- 知识库详情页新增「图谱」Tab
- 使用 @antv/g6 渲染力导向图
- 支持：搜索节点、点击展开邻居、拖拽移动、缩放
- 节点按类型着色，边显示关系标签
- 点击节点/边展示详情面板

### 2.4 权限设计

| 操作 | CREATOR | MANAGER | DEVELOPER | VIEWER |
|------|---------|---------|-----------|--------|
| 查看图谱 | ✅ | ✅ | ✅ | ✅ |
| 触发图谱抽取 | ✅ | ✅ | ✅ | ❌ |
| 手动导入 | ✅ | ✅ | ✅ | ❌ |
| 手动编辑节点/边 | ✅ | ✅ | ✅ | ❌ |
| 删除图谱数据 | ✅ | ✅ | ❌ | ❌ |

---

## 三、数据库设计

### 3.1 关系型数据库（PostgreSQL）

#### 3.1.1 图谱任务表：graph_extraction_task

记录图谱抽取任务的执行状态。

```sql
CREATE TABLE graph_extraction_task (
    id              BIGINT          NOT NULL,
    knowledge_id    BIGINT          NOT NULL,
    document_id     BIGINT,
    status          VARCHAR(20)     NOT NULL DEFAULT 'pending',
    source          VARCHAR(20)     NOT NULL DEFAULT 'auto',
    entity_count    INTEGER         NOT NULL DEFAULT 0,
    relation_count  INTEGER         NOT NULL DEFAULT 0,
    error_message   TEXT,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE INDEX idx_graph_task_knowledge_id ON graph_extraction_task (knowledge_id);
CREATE INDEX idx_graph_task_status ON graph_extraction_task (status);
COMMENT ON TABLE graph_extraction_task IS '图谱抽取任务';
COMMENT ON COLUMN graph_extraction_task.source IS '来源：auto-自动抽取、import-手动导入';
COMMENT ON COLUMN graph_extraction_task.status IS '状态：pending/running/completed/failed';
```

#### 3.1.2 知识库表扩展

```sql
ALTER TABLE knowledge ADD COLUMN graph_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE knowledge ADD COLUMN node_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE knowledge ADD COLUMN edge_count INTEGER NOT NULL DEFAULT 0;
COMMENT ON COLUMN knowledge.graph_enabled IS '是否启用知识图谱';
COMMENT ON COLUMN knowledge.node_count IS '图谱节点数';
COMMENT ON COLUMN knowledge.edge_count IS '图谱边数';
```

### 3.2 图数据库（Neo4j）

#### 3.2.1 节点设计

每个知识库的节点使用独立 Label 进行数据隔离：`kb_{knowledgeId}`

**节点通用属性：**

| 属性 | 类型 | 说明 |
|------|------|------|
| id | String | 节点唯一标识（雪花算法转字符串） |
| name | String | 实体名称（用于显示和 MERGE 匹配） |
| entity_type | String | 实体类型（人物/组织/职位/项目/技术/概念等） |
| description | String | 实体描述（LLM 生成或手动填写） |
| source | String | 来源：auto / import / manual |
| document_id | String | 来源文档 ID（自动抽取时记录） |
| chunk_id | String | 来源 Chunk ID（自动抽取时记录） |
| knowledge_id | String | 所属知识库 ID |
| created_at | DateTime | 创建时间 |
| updated_at | DateTime | 更新时间 |

**Cypher 创建示例：**

```cypher
// 自动抽取的节点
MERGE (n:Entity:`kb_2056961707612393473` {name: '张三'})
SET n.entity_type = '人物',
    n.description = 'LightBot 项目技术负责人',
    n.source = 'auto',
    n.document_id = '2056961707612394000',
    n.chunk_id = '2056961707612395000',
    n.knowledge_id = '2056961707612393473',
    n.updated_at = datetime()
ON CREATE SET n.id = '2056961707612396000',
              n.created_at = datetime()
```

#### 3.2.2 关系设计

| 属性 | 类型 | 说明 |
|------|------|------|
| id | String | 关系唯一标识 |
| relation_type | String | 关系类型（担任/隶属于/负责/使用/包含等） |
| description | String | 关系描述 |
| weight | Float | 权重（默认 1.0，出现次数越多越高） |
| source | String | 来源 |
| document_id | String | 来源文档 ID |
| knowledge_id | String | 所属知识库 ID |
| created_at | DateTime | 创建时间 |

**Cypher 创建示例：**

```cypher
// 自动抽取的关系
MATCH (h:Entity:`kb_2056961707612393473` {name: '张三'})
MATCH (t:Entity:`kb_2056961707612393473` {name: '技术总监'})
MERGE (h)-[r:RELATION {relation_type: '担任'}]->(t)
SET r.description = '张三担任技术总监职务',
    r.weight = 1.0,
    r.source = 'auto',
    r.knowledge_id = '2056961707612393473',
    r.updated_at = datetime()
ON CREATE SET r.id = '2056961707612397000',
              r.created_at = datetime()
```

#### 3.2.3 索引设计

```cypher
// 实体名唯一性约束（每个知识库内实体名唯一）
CREATE CONSTRAINT entity_name_unique IF NOT EXISTS
FOR (n:Entity) REQUIRE (n.name, n.knowledge_id) IS UNIQUE;

// 实体名全文索引（支持模糊搜索）
CREATE FULLTEXT INDEX entity_fulltext IF NOT EXISTS
FOR (n:Entity) ON EACH [n.name, n.description];

// 实体名向量索引（支持语义搜索）
CREATE VECTOR INDEX entity_embeddings IF NOT EXISTS
FOR (n:Entity)
ON (n.embedding)
OPTIONS {
    indexConfig: {
        `vector.dimensions`: 1536,
        `vector.similarity_function`: 'cosine'
    }
};
```

#### 3.2.4 数据隔离策略

与 Yuxi 一致，每个知识库使用独立的 Neo4j Label（`kb_{knowledgeId}`），所有查询都带 Label 过滤，确保数据隔离。

```cypher
// 查询特定知识库的图谱
MATCH (n:`kb_2056961707612393473`)
...

// 删除特定知识库的图谱
MATCH (n:`kb_2056961707612393473`) DETACH DELETE n
```

---

## 四、技术架构

### 4.1 后端模块结构

```
com.lightbot
├── config/Neo4jConfig.java                          # Neo4j 连接配置
├── util/Neo4jUtil.java                              # Neo4j 客户端封装（Util 规范）
│
├── entity/GraphExtractionTask.java                  # 抽取任务实体
├── enums/GraphTaskStatus.java                       # 任务状态枚举
├── enums/GraphTaskSource.java                       # 任务来源枚举
├── mapper/GraphExtractionTaskMapper.java            # 任务 Mapper
│
├── service/GraphService.java                        # 图谱 Service 接口
├── service/impl/GraphServiceImpl.java               # 图谱 Service 实现
│
├── dto/GraphTripleDTO.java                          # 三元组 DTO
├── dto/GraphImportDTO.java                          # 导入 DTO
├── dto/GraphNodeVO.java                             # 节点 VO
├── dto/GraphEdgeVO.java                             # 边 VO
├── dto/GraphSubgraphVO.java                         # 子图 VO（节点+边集合）
├── dto/GraphStatsVO.java                            # 统计 VO
│
├── model/graph/GraphExtractor.java                  # 实体关系抽取器（调用 LLM）
├── model/graph/GraphQueryBuilder.java               # Cypher 查询构建器
│
└── controller/KnowledgeController.java              # 复用现有 Controller，新增方法
```

### 4.2 核心类设计

#### 4.2.1 Neo4jUtil（中间件封装）

按项目规范，Neo4j 操作封装为 Util 类，不做业务逻辑：

```java
@Slf4j
@Component
public class Neo4jUtil {

    private final Driver driver;

    public Neo4jUtil(@Value("${neo4j.uri}") String uri,
                     @Value("${neo4j.username}") String username,
                     @Value("${neo4j.password}") String password) {
        this.driver = GraphDatabase.driver(uri, AuthTokens.basic(username, password));
    }

    /**
     * 执行写事务
     */
    public <T> T executeWrite(TransactionCallback<T> callback) {
        try (Session session = driver.session()) {
            return session.executeWrite(callback);
        }
    }

    /**
     * 执行读事务
     */
    public <T> T executeRead(TransactionCallback<T> callback) {
        try (Session session = driver.session()) {
            return session.executeRead(callback);
        }
    }

    /**
     * 批量执行 Cypher（用于抽取结果写入）
     */
    public void executeBatch(String cypher, List<Map<String, Object>> paramsList) {
        try (Session session = driver.session()) {
            session.executeWrite(tx -> {
                for (Map<String, Object> params : paramsList) {
                    tx.run(cypher, params);
                }
                return null;
            });
        }
    }

    /**
     * 健康检查
     */
    public boolean ping() {
        try (Session session = driver.session()) {
            session.run("RETURN 1").consume();
            return true;
        } catch (Exception e) {
            log.warn("[Neo4j] 健康检查失败: {}", e.getMessage());
            return false;
        }
    }

    @PreDestroy
    public void close() {
        driver.close();
    }
}
```

#### 4.2.2 GraphService 接口

```java
public interface GraphService {

    /**
     * 触发文档的图谱抽取（异步）
     */
    void extractFromDocument(Long knowledgeId, Long documentId);

    /**
     * 批量抽取知识库所有已完成文档
     */
    void extractFromKnowledge(Long knowledgeId);

    /**
     * 批量导入三元组
     */
    void importTriples(Long knowledgeId, List<GraphTripleDTO> triples);

    /**
     * 获取子图（用于可视化和检索）
     * @param keyword 搜索关键词（null 表示采样）
     * @param maxDepth 最大跳数
     * @param maxNodes 最大节点数
     */
    GraphSubgraphVO getSubgraph(Long knowledgeId, String keyword, int maxDepth, int maxNodes);

    /**
     * 图谱检索：从问题中提取实体，展开子图，返回文本格式的三元组
     */
    List<String> searchForRag(Long knowledgeId, String question);

    /**
     * 获取图谱统计信息
     */
    GraphStatsVO getStats(Long knowledgeId);

    /**
     * 删除知识库的全部图谱数据
     */
    void deleteByKnowledgeId(Long knowledgeId);

    /**
     * 删除文档关联的图谱数据
     */
    void deleteByDocumentId(Long knowledgeId, Long documentId);

    /**
     * 手动创建节点
     */
    GraphNodeVO createNode(Long knowledgeId, String name, String entityType, String description);

    /**
     * 手动创建边
     */
    GraphEdgeVO createEdge(Long knowledgeId, String headName, String relationType, String tailName, String description);

    /**
     * 删除节点
     */
    void deleteNode(Long knowledgeId, String nodeId);

    /**
     * 删除边
     */
    void deleteEdge(Long knowledgeId, String edgeId);
}
```

#### 4.2.3 GraphExtractor（LLM 抽取器）

```java
@Component
@RequiredArgsConstructor
public class GraphExtractor {

    private final ChatModel chatModel;

    /**
     * 从文本中抽取实体和关系三元组
     */
    public List<GraphTripleDTO> extract(String content) {
        String prompt = """
            请从以下文本中抽取实体和关系，输出 JSON 数组格式。

            要求：
            1. 实体：提取人名、组织、职位、项目、产品、地点、技术、概念等有明确含义的名词
            2. 关系：提取实体间的语义关系，用动词短语描述（如"负责"、"隶属于"、"使用"）
            3. 每个三元组格式：{"head": "实体A", "headType": "类型", "relation": "关系", "tail": "实体B", "tailType": "类型"}
            4. 实体名称保持原文，不要翻译或缩写
            5. 关系方向要正确
            6. 如果文本中没有明确的实体关系，返回空数组 []

            文本：
            %s
            """.formatted(content);

        String response = chatModel.call(prompt);
        return parseTriples(response);
    }

    /**
     * 从用户问题中提取关键实体名
     */
    public List<String> extractEntitiesFromQuestion(String question) {
        String prompt = """
            请从以下问题中提取关键实体名称（人名、组织、项目、技术等），输出 JSON 数组。
            只输出实体名，不要解释。

            问题：%s
            """.formatted(question);

        String response = chatModel.call(prompt);
        return parseEntityNames(response);
    }
}
```

### 4.3 抽取流程详细设计

#### 4.3.1 异步抽取任务

复用现有 TaskService 体系，新增任务类型：

```java
// GraphExtractionExecutor.java
@Component
@RequiredArgsConstructor
public class GraphExtractionExecutor implements TaskExecutor {

    private final GraphExtractor graphExtractor;
    private final Neo4jUtil neo4jUtil;
    private final EmbeddingService embeddingService;
    private final KnowledgeService knowledgeService;

    @Override
    public String getTaskType() {
        return "GRAPH_EXTRACTION";
    }

    @Override
    public void execute(Task task, ProgressCallback callback) {
        Long knowledgeId = task.getKnowledgeId();
        Long documentId = task.getDocumentId();

        // 1. 获取文档的所有 Chunk
        List<Chunk> chunks = chunkService.listByDocumentId(documentId);
        callback.onProgress(0, chunks.size(), "开始图谱抽取...");

        List<GraphTripleDTO> allTriples = new ArrayList<>();

        // 2. 逐 Chunk 抽取（可控制并发）
        for (int i = 0; i < chunks.size(); i++) {
            Chunk chunk = chunks.get(i);
            try {
                List<GraphTripleDTO> triples = graphExtractor.extract(chunk.getContent());
                allTriples.addAll(triples);
            } catch (Exception e) {
                log.warn("[图谱抽取] Chunk {} 抽取失败: {}", chunk.getId(), e.getMessage());
            }
            callback.onProgress(i + 1, chunks.size(), "已处理 %d/%d 个分片".formatted(i + 1, chunks.size()));
        }

        // 3. 去重 + 写入 Neo4j
        String label = "kb_" + knowledgeId;
        writeTriplesToNeo4j(label, allTriples, documentId, knowledgeId);

        // 4. 对新实体做 Embedding
        embedNewEntities(label, knowledgeId);

        // 5. 更新统计
        GraphStatsVO stats = getStatsFromNeo4j(label);
        knowledgeService.updateGraphStats(knowledgeId, stats.nodeCount(), stats.edgeCount());

        callback.onProgress(chunks.size(), chunks.size(), "图谱抽取完成");
    }
}
```

#### 4.3.2 Neo4j 写入逻辑

```java
// 写入三元组（MERGE 幂等）
private static final String MERGE_TRIPLE_CYPHER = """
    MERGE (h:Entity:`%s` {name: $head})
    ON CREATE SET h.id = $headId,
                  h.entity_type = $headType,
                  h.description = $headDesc,
                  h.source = $source,
                  h.document_id: $documentId,
                  h.knowledge_id: $knowledgeId,
                  h.created_at = datetime(),
                  h.updated_at = datetime()
    ON MATCH SET h.updated_at = datetime()

    MERGE (t:Entity:`%s` {name: $tail})
    ON CREATE SET t.id = $tailId,
                  t.entity_type: $tailType,
                  t.description: $tailDesc,
                  t.source = $source,
                  t.document_id: $documentId,
                  t.knowledge_id: $knowledgeId,
                  t.created_at = datetime(),
                  t.updated_at = datetime()
    ON MATCH SET t.updated_at = datetime()

    MERGE (h)-[r:RELATION {relation_type: $relation}]->(t)
    ON CREATE SET r.id: $relationId,
                  r.description: $relationDesc,
                  r.weight: 1.0,
                  r.source: $source,
                  r.knowledge_id: $knowledgeId,
                  r.created_at = datetime(),
                  r.updated_at = datetime()
    ON MATCH SET r.weight: r.weight + 0.1,
                 r.updated_at = datetime()
    """;
```

### 4.4 RAG 检索集成

#### 4.4.1 图谱检索流程

```java
// GraphServiceImpl.searchForRag()
@Override
public List<String> searchForRag(Long knowledgeId, String question) {
    // 1. 从问题中提取实体名
    List<String> entityNames = graphExtractor.extractEntitiesFromQuestion(question);
    if (entityNames.isEmpty()) {
        return Collections.emptyList();
    }

    String label = "kb_" + knowledgeId;
    List<String> results = new ArrayList<>();

    // 2. 对每个实体，查找 1-2 跳子图
    for (String entityName : entityNames) {
        String cypher = """
            MATCH (n:Entity:`%s` {name: $name})
            OPTIONAL MATCH (n)-[r1]-(m1:Entity:`%s`)
            OPTIONAL MATCH (m1)-[r2]-(m2:Entity:`%s`)
            WHERE m2 <> n
            RETURN n.name AS h1, r1.relation_type AS r1, m1.name AS t1,
                   m1.name AS h2, r2.relation_type AS r2, m2.name AS t2
            """.formatted(label, label, label);

        neo4jUtil.executeRead(tx -> {
            Result result = tx.run(cypher, Map.of("name", entityName));
            while (result.hasNext()) {
                Record record = result.next();
                // 格式化为文本三元组
                if (record.get("r1").asString() != null) {
                    results.add("%s %s %s".formatted(
                        record.get("h1").asString(),
                        record.get("r1").asString(),
                        record.get("t1").asString()));
                }
                if (record.get("r2").asString() != null) {
                    results.add("%s %s %s".formatted(
                        record.get("h2").asString(),
                        record.get("r2").asString(),
                        record.get("t2").asString()));
                }
            }
            return null;
        });
    }

    // 3. 去重 + 限制条数
    return results.stream().distinct().limit(10).collect(Collectors.toList());
}
```

#### 4.4.2 RagService 改造

```java
// RagServiceImpl.ask() 最终形态（三层漏斗）
public RagAnswerVO ask(Long knowledgeId, String question, Long providerId) {
    float[] queryVector = embed(question);

    // 第一层：QA Pair 优先（高分直接返回）
    List<QaPairSearchResultVO> qaResults = qaPairService.searchSimilar(knowledgeId, queryVector, 3, 0.85);
    if (!qaResults.isEmpty() && qaResults.get(0).getScore() >= 0.85) {
        return RagAnswerVO.direct(qaResults.get(0));
    }

    // 第二层 + 第三层：图谱检索 + Chunk 检索（并行）
    CompletableFuture<List<String>> graphFuture = CompletableFuture.supplyAsync(
        () -> graphService.searchForRag(knowledgeId, question));
    CompletableFuture<List<RagSearchResultVO>> chunkFuture = CompletableFuture.supplyAsync(
        () -> searchChunks(knowledgeId, queryVector));

    List<String> graphResults = graphFuture.join();
    List<RagSearchResultVO> chunkResults = chunkFuture.join();

    // 构建上下文：图谱三元组 + Chunk 片段
    StringBuilder context = new StringBuilder();
    if (!graphResults.isEmpty()) {
        context.append("【知识图谱】\n");
        graphResults.forEach(triple -> context.append("- ").append(triple).append("\n"));
        context.append("\n");
    }
    if (!chunkResults.isEmpty()) {
        context.append("【文档参考】\n");
        chunkResults.forEach(chunk -> context.append(chunk.getContent()).append("\n\n"));
    }

    // 调用 LLM
    String answer = callLLM(question, context.toString(), providerId);
    return RagAnswerVO.generated(answer, chunkResults, graphResults);
}
```

### 4.5 前端设计

#### 4.5.1 图谱可视化

使用 @antv/g6（与 Yuxi 一致），核心组件：

```text
src/components/graph/
├── GraphCanvas.vue              # 图谱画布（力导向布局）
├── GraphDetailPanel.vue         # 节点/边详情面板
├── GraphSearchBar.vue           # 搜索栏
├── GraphToolbar.vue             # 工具栏（缩放/全屏/筛选）
└── GraphStatsPanel.vue          # 统计信息面板
```

#### 4.5.2 图谱编辑器

```text
src/components/graph/
├── GraphEditor.vue              # 图谱编辑器（可拖拽连线）
├── NodeCreateModal.vue          # 创建节点弹窗
└── EdgeCreateModal.vue          # 创建边弹窗
```

#### 4.5.3 入口位置

知识库详情页新增 Tab：`图谱`（与 `文档`、`成员`、`问答对` 并列）

#### 4.5.4 节点类型配色

| 类型 | 颜色 | 图标 |
|------|------|------|
| 人物 | #e74c3c (红) | User |
| 组织 | #3498db (蓝) | Building |
| 职位 | #2ecc71 (绿) | Briefcase |
| 项目 | #f39c12 (橙) | Folder |
| 技术 | #9b59b6 (紫) | Code |
| 地点 | #1abc9c (青) | MapPin |
| 概念 | #95a5a6 (灰) | Lightbulb |
| 默认 | #34495e (深灰) | Circle |

---

## 五、API 接口设计

挂在现有 KnowledgeController 下，路径前缀 `/api/knowledge/{knowledgeId}/graph`。

### 5.1 接口列表

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | `/{knowledgeId}/graph/extract` | 触发全量图谱抽取 | DEVELOPER+ |
| POST | `/{knowledgeId}/graph/extract/{documentId}` | 触发单文档图谱抽取 | DEVELOPER+ |
| POST | `/{knowledgeId}/graph/import` | 批量导入三元组 | DEVELOPER+ |
| GET | `/{knowledgeId}/graph/subgraph` | 获取子图（可视化） | MEMBER+ |
| GET | `/{knowledgeId}/graph/stats` | 获取图谱统计 | MEMBER+ |
| DELETE | `/{knowledgeId}/graph` | 清空图谱数据 | MANAGER+ |
| POST | `/{knowledgeId}/graph/nodes` | 手动创建节点 | DEVELOPER+ |
| DELETE | `/{knowledgeId}/graph/nodes/{nodeId}` | 删除节点 | DEVELOPER+ |
| POST | `/{knowledgeId}/graph/edges` | 手动创建边 | DEVELOPER+ |
| DELETE | `/{knowledgeId}/graph/edges/{edgeId}` | 删除边 | DEVELOPER+ |

### 5.2 核心接口详情

#### 触发全量图谱抽取

```
POST /api/knowledge/{knowledgeId}/graph/extract

Response:
{
    "code": 200,
    "message": "图谱抽取任务已提交",
    "data": {
        "taskId": "2056961707612398000"
    }
}
```

#### 获取子图

```
GET /api/knowledge/{knowledgeId}/graph/subgraph?keyword=张三&maxDepth=2&maxNodes=50

Response:
{
    "code": 200,
    "data": {
        "nodes": [
            {
                "id": "2056961707612396000",
                "name": "张三",
                "entityType": "人物",
                "description": "LightBot 项目技术负责人",
                "properties": { ... }
            }
        ],
        "edges": [
            {
                "id": "2056961707612397000",
                "source": "2056961707612396000",
                "target": "2056961707612396001",
                "relationType": "担任",
                "description": "张三担任技术总监职务",
                "weight": 1.0
            }
        ],
        "stats": {
            "nodeCount": 15,
            "edgeCount": 20
        }
    }
}
```

#### 批量导入三元组

```
POST /api/knowledge/{knowledgeId}/graph/import
Content-Type: application/json

Request:
{
    "triples": [
        {"head": "张三", "headType": "人物", "relation": "担任", "tail": "技术总监", "tailType": "职位"},
        {"head": "技术总监", "headType": "职位", "relation": "隶属于", "tail": "研发部", "tailType": "组织"}
    ]
}

Response:
{
    "code": 200,
    "message": "导入成功，共 2 个三元组",
    "data": {
        "nodeCount": 4,
        "edgeCount": 2
    }
}
```

---

## 六、Neo4j 配置与部署

### 6.1 Docker Compose 配置

```yaml
# docker-compose.yml 新增
services:
  neo4j:
    image: neo4j:5.26-community
    container_name: lightbot-neo4j
    ports:
      - "7474:7474"   # HTTP (Browser)
      - "7687:7687"   # Bolt (Driver)
    environment:
      NEO4J_AUTH: neo4j/${NEO4J_PASSWORD:-0123456789}
      NEO4J_PLUGINS: '["apoc", "graph-data-science"]'
      NEO4J_server_memory_heap_max__size: "1G"
    volumes:
      - neo4j_data:/data
    healthcheck:
      test: ["CMD", "neo4j", "status"]
      interval: 10s
      timeout: 5s
      retries: 5

volumes:
  neo4j_data:
```

### 6.2 Spring Boot 配置

```yaml
# application.yml
neo4j:
  uri: bolt://localhost:7687
  username: neo4j
  password: ${NEO4J_PASSWORD:0123456789}
```

### 6.3 Maven 依赖

```xml
<dependency>
    <groupId>org.neo4j.driver</groupId>
    <artifactId>neo4j-java-driver</artifactId>
    <version>5.26.0</version>
</dependency>
```

> 注：使用官方 Driver 而非 Spring Data Neo4j，因为只需要 Cypher 级别操作，不需要 OGM 映射。

---

## 七、与现有功能的集成点

### 7.1 DocumentService

文档向量化完成后，如果知识库 `graphEnabled=true`，自动触发图谱抽取任务：

```java
// DocumentServiceImpl.processDocumentWithProgress() 末尾
if (knowledge.getGraphEnabled()) {
    taskService.createTask("GRAPH_EXTRACTION", knowledgeId, documentId);
}
```

### 7.2 RagService

在 `ask` / `askStream` 方法中集成图谱检索（详见 4.4.2）。

### 7.3 KnowledgeService

新增方法：
- `updateGraphStats(knowledgeId, nodeCount, edgeCount)` — 更新图谱统计
- `enableGraph(knowledgeId)` / `disableGraph(knowledgeId)` — 开关图谱功能

### 7.4 QueryKnowledgeTool

Agent Tool 检索时，如果知识库开启了图谱，同时返回图谱检索结果。

### 7.5 知识库删除

级联删除 Neo4j 中该知识库的全部节点和边：

```cypher
MATCH (n:Entity:`kb_{knowledgeId}`) DETACH DELETE n
```

---

## 八、开发计划

### 阶段一：基础设施

| 任务 | 说明 |
|------|------|
| Docker Compose 新增 Neo4j | 容器配置、健康检查 |
| Neo4jUtil | 客户端封装、事务管理 |
| Neo4jConfig | 连接配置 |
| 建表 SQL | graph_extraction_task 表、knowledge 表加字段 |

### 阶段二：图谱构建

| 任务 | 说明 |
|------|------|
| GraphExtractor | LLM 抽取实体关系 |
| GraphExtractionExecutor | 异步抽取任务执行器 |
| GraphService | CRUD + 抽取 + 导入 |
| Controller 接口 | API 端点 |

### 阶段三：图谱检索

| 任务 | 说明 |
|------|------|
| GraphService.searchForRag | 图谱检索逻辑 |
| RagService 改造 | 三层漏斗集成 |
| QueryKnowledgeTool 改造 | Agent Tool 集成 |

### 阶段四：前端可视化

| 任务 | 说明 |
|------|------|
| GraphCanvas | @antv/g6 画布组件 |
| GraphDetailPanel | 节点/边详情 |
| GraphEditor | 手动编辑器 |
| 知识库详情页 Tab | 图谱入口 |

### 阶段五：高级功能

| 任务 | 说明 |
|------|------|
| 图谱编辑器 | 拖拽连线、节点编辑 |
| 图谱导出 | 导出为 JSONL |
| 图谱合并 | 跨文档实体去重优化 |
| 图谱统计增强 | 类型分布、连通性分析 |

---

## 九、FAQ

### Q: 为什么不直接用 LightRAG？

LightRAG 是 Python 生态，与 LightBot 的 Java 技术栈不兼容。LightBot 使用 SpringAI 作为 AI 框架，自己实现抽取+图谱检索可以深度集成到现有架构中，不受第三方库约束。

### Q: Neo4j 挂了怎么办？

图谱是增强功能，不是核心依赖。RagService 中图谱检索用 `CompletableFuture` 包装，设置超时（如 3s），超时或异常时降级为纯 Chunk 检索，不影响正常问答。

### Q: 抽取质量怎么保证？

1. Prompt 优化：明确实体类型和关系规范
2. 人工校验：前端可视化展示，支持手动编辑/删除
3. 增量更新：同一实体多次出现时 ON MATCH 更新描述，关系权重累加
4. 后续可引入 NER 模型辅助抽取

### Q: 数据量大了 Neo4j 性能如何？

1. 每个知识库独立 Label，查询天然隔离
2. 实体名有唯一约束索引
3. 向量索引支持语义搜索
4. 子图查询限制 maxNodes/maxDepth，防止全图扫描
5. 单个知识库万级节点级别 Neo4j 毫无压力

### Q: 图谱和 QA Pair 会冲突吗？

不会。它们是独立的知识形态，在 RAG 检索中各司其职：
- QA Pair：精确问答，高分直接返回
- 图谱：关系推理，结构化上下文
- Chunk：通用兜底，非结构化文本

三者并行检索，按优先级合并。
