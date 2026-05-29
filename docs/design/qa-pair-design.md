# 知识库 - 问答对功能技术设计文档

> 2026-05-29 | 状态：方案设计

---

## 一、功能定位

### 1.1 什么是问答对

问答对（QA Pair）是知识库中的一种**结构化知识单元**，由一个问题和一个标准答案组成。与传统文档（PDF/Markdown）经切片后生成的 Chunk 不同，问答对天然是一问一答的粒度，无需切片。

### 1.2 核心价值

| 场景 | 传统 Chunk | 问答对 |
|------|-----------|--------|
| 用户问"如何重置密码" | 向量检索命中一篇长文档的某个片段，LLM 需要从上下文中提炼答案 | 向量检索直接命中问题，返回预设标准答案，**无需 LLM 合成** |
| 答案准确性 | 依赖 LLM 理解能力，可能幻觉 | 答案由人工/审核确定，**确定性高** |
| 响应速度 | embed → 检索 → LLM 生成（慢） | embed → 检索 → 直接返回（快） |
| 适用场景 | 开放性问题、文档分析 | FAQ、标准操作流程、客服话术 |

### 1.3 与现有功能的关系

```
Knowledge
├── Document（传统文档）
│   └── Chunk → Embedding（切片后向量化）
├── QAPair（问答对）★ 新增
│   └── 直接向量化 question 字段
└── ExampleQuestions（示例问题，仅用于 UI 展示，无答案）
```

- **ExampleQuestions**：存在 Knowledge 的 JSONB 字段中，最多 10 条，仅展示在知识库首页引导用户提问，不含答案，不参与向量检索。
- **EvalBenchmark**：评测基准，用于评估 RAG 效果，有 question + ground_truth，但不参与线上检索。
- **QAPair**：线上检索的一等公民，question 做向量检索，answer 直接返回。

---

## 二、业务流程

### 2.1 生命周期

```
创建 → (向量化中) → 生效 → 编辑/删除
         ↓ 失败
       重试
```

### 2.2 核心操作

#### 2.2.1 创建问答对

**方式一：手动创建**
- 用户在知识库详情页 → 问答对 Tab → 新增
- 填写 question + answer
- 提交后自动触发向量化（异步）

**方式二：批量导入**
- 上传 JSONL 文件，每行格式：`{"question": "...", "answer": "..."}`
- 解析后批量创建，批量向量化

**方式三：AI 生成**
- 选择已有文档 → AI 从文档内容中提取问答对
- 生成后用户可编辑确认，确认后向量化

#### 2.2.2 向量化流程

```
Question 文本 → Embedding Model → 向量存入 embedding 表
```

- 只对 question 做向量化（用户输入匹配的是问题）
- answer 不需要向量化（它是结果，不是检索键）
- 复用现有 EmbeddingService，与 Chunk 向量化共用同一张 embedding 表

#### 2.2.3 检索流程（集成到 RAG）

```
用户提问
  ↓
EmbeddingService.embed(用户问题)
  ↓
┌─ 向量检索 Chunk（现有逻辑）
└─ 向量检索 QA Pair（新增）
  ↓
合并结果，按 score 排序
  ↓
命中 QA Pair 且 score ≥ 阈值？
  ├─ 是 → 直接返回 QA Pair 的 answer（跳过 LLM）
  └─ 否 → 走现有 RAG 流程（Chunk 上下文 + LLM 生成）
```

**关键决策：QA Pair 优先级高于 Chunk**

当 QA Pair 的最高相似度分数超过阈值（如 0.85）时，直接返回预设答案，不调用 LLM。这保证了标准答案的准确性，同时降低了延迟和 token 消耗。

当 QA Pair 分数不够高但 Chunk 检索到相关内容时，将命中的 QA Pair 也作为参考资料注入 LLM 上下文，辅助生成更准确的回答。

### 2.3 权限设计

复用现有 KnowledgeRole 体系：

| 操作 | CREATOR | MANAGER | DEVELOPER | VIEWER |
|------|---------|---------|-----------|--------|
| 查看问答对列表 | ✅ | ✅ | ✅ | ✅ |
| 创建/编辑/删除问答对 | ✅ | ✅ | ✅ | ❌ |
| 批量导入 | ✅ | ✅ | ✅ | ❌ |
| AI 生成 | ✅ | ✅ | ✅ | ❌ |

与文档管理权限一致（DEVELOPER 及以上）。

---

## 三、数据库设计

### 3.1 新增表：qa_pair

```sql
-- 问答对表
CREATE TABLE qa_pair (
    id              BIGINT          NOT NULL,
    knowledge_id    BIGINT          NOT NULL,
    question        TEXT            NOT NULL,
    answer          TEXT            NOT NULL,
    source          VARCHAR(20)     NOT NULL DEFAULT 'manual',
    status          VARCHAR(20)     NOT NULL DEFAULT 'pending',
    token_count     INTEGER         NOT NULL DEFAULT 0,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE INDEX idx_qa_pair_knowledge_id ON qa_pair (knowledge_id);
CREATE INDEX idx_qa_pair_status ON qa_pair (status);
COMMENT ON TABLE qa_pair IS '知识库问答对';
COMMENT ON COLUMN qa_pair.source IS '来源：manual-手动创建、import-批量导入、ai-AI生成';
COMMENT ON COLUMN qa_pair.status IS '状态：pending-待向量化、vectorizing-向量化中、active-生效、failed-失败';
```

### 3.2 复用表：embedding

现有 embedding 表已支持 chunk_id 作为外键。问答对需要关联，有两种方案：

**方案对比：**

| 方案 | 做法 | 优点 | 缺点 |
|------|------|------|------|
| A. 新增 qa_pair_id 列 | `ALTER TABLE embedding ADD COLUMN qa_pair_id BIGINT` | 关系清晰，查询方便 | 需要改表结构，chunk_id 和 qa_pair_id 互斥 |
| B. 创建虚拟 Chunk | 为每个 QA Pair 创建一条 Chunk 记录，复用现有 chunk_id 关联 | 零改动，完全复用现有检索逻辑 | Chunk 表语义被污染，多了无意义的中间记录 |

**选定方案：A — 新增 qa_pair_id 列**

理由：
1. 语义清晰，Chunk 是文档切片，QA Pair 是独立知识单元，不应混为一谈
2. embedding 表是基础设施层，增加一个外键列成本极低
3. 检索 SQL 只需加一个 OR 条件，不影响现有性能

```sql
ALTER TABLE embedding ADD COLUMN qa_pair_id BIGINT;
COMMENT ON COLUMN embedding.qa_pair_id IS '关联问答对ID，与chunk_id互斥';
```

### 3.3 字段说明

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | 雪花算法 |
| knowledge_id | BIGINT | ✅ | 所属知识库 |
| question | TEXT | ✅ | 问题内容 |
| answer | TEXT | ✅ | 标准答案 |
| source | VARCHAR(20) | ✅ | 来源枚举：manual / import / ai |
| status | VARCHAR(20) | ✅ | 状态枚举：pending / vectorizing / active / failed |
| token_count | INTEGER | ✅ | question 的 token 数量 |
| create_time | TIMESTAMP | ✅ | 创建时间（自动填充） |
| update_time | TIMESTAMP | ✅ | 更新时间（自动填充） |
| deleted | SMALLINT | ✅ | 逻辑删除（0/1） |

### 3.4 ER 关系

```
Knowledge (1) ---< (N) QAPair (1) --- (1) Embedding
    |
    +---< (N) Document (1) ---< (N) Chunk (1) --- (1) Embedding
```

---

## 四、技术架构

### 4.1 后端模块结构

```
com.lightbot
├── entity/QaPair.java                    # 实体类
├── enums/QaPairSource.java               # 来源枚举
├── enums/QaPairStatus.java               # 状态枚举
├── mapper/QaPairMapper.java              # Mapper 接口
├── service/QaPairService.java            # Service 接口
├── service/impl/QaPairServiceImpl.java   # Service 实现
├── dto/QaPairCreateDTO.java              # 创建 DTO
├── dto/QaPairUpdateDTO.java              # 更新 DTO
├── dto/QaPairVO.java                     # 返回 VO
├── dto/QaPairImportDTO.java              # 批量导入 DTO
└── controller/KnowledgeController.java   # 复用现有 Controller，新增方法
```

### 4.2 核心类设计

#### 4.2.1 Entity

```java
@Data
@TableName(value = "qa_pair", autoResultMap = true)
@Schema(description = "知识库问答对")
public class QaPair {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("knowledge_id")
    @Schema(description = "知识库ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long knowledgeId;

    @TableField("question")
    @Schema(description = "问题内容")
    private String question;

    @TableField("answer")
    @Schema(description = "标准答案")
    private String answer;

    @TableField("source")
    @Schema(description = "来源")
    private QaPairSource source;

    @TableField("status")
    @Schema(description = "状态")
    private QaPairStatus status;

    @TableField("token_count")
    @Schema(description = "问题token数量")
    private Integer tokenCount;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @TableField("deleted")
    @TableLogic
    @Schema(description = "逻辑删除标记")
    private Integer deleted;
}
```

#### 4.2.2 Service 接口

```java
public interface QaPairService extends IService<QaPair> {

    /**
     * 创建问答对并触发向量化
     */
    QaPairVO create(QaPairCreateDTO dto);

    /**
     * 更新问答对（如果 question 变更，重新向量化）
     */
    QaPairVO update(QaPairUpdateDTO dto);

    /**
     * 分页查询问答对列表
     */
    Page<QaPairVO> listByKnowledgeId(Long knowledgeId, int pageNum, int pageSize, String keyword);

    /**
     * 删除问答对（级联删除 embedding）
     */
    void deleteById(Long id);

    /**
     * 批量导入问答对
     */
    List<QaPairVO> batchImport(Long knowledgeId, List<QaPairImportDTO> items);

    /**
     * 触发单个问答对的向量化
     */
    void vectorize(Long qaPairId);

    /**
     * 批量向量化（用于导入后批量处理）
     */
    void batchVectorize(List<Long> qaPairIds);

    /**
     * 向量检索：在指定知识库中搜索最相关的问答对
     */
    List<QaPairSearchResultVO> searchSimilar(Long knowledgeId, float[] queryVector, int topK, double threshold);
}
```

#### 4.2.3 检索结果 VO

```java
@Data
@Schema(description = "问答对检索结果")
public class QaPairSearchResultVO {

    @Schema(description = "问答对ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "问题")
    private String question;

    @Schema(description = "答案")
    private String answer;

    @Schema(description = "相似度分数")
    private Double score;
}
```

### 4.3 检索集成方案

#### 4.3.1 EmbeddingMapper 新增方法

```java
/**
 * 向量检索问答对（SQL层阈值过滤）
 */
@Select("""
    SELECT qp.id, qp.question, qp.answer,
           1 - (e.vector <=> #{queryVector}::vector) AS score
    FROM embedding e
    JOIN qa_pair qp ON e.qa_pair_id = qp.id
    WHERE qp.knowledge_id = #{knowledgeId}
      AND qp.deleted = 0
      AND qp.status = 'active'
      AND (1 - (e.vector <=> #{queryVector}::vector)) >= #{threshold}
    ORDER BY e.vector <=> #{queryVector}::vector
    LIMIT #{topK}
    """)
List<Map<String, Object>> searchSimilarQaPairs(
    @Param("knowledgeId") Long knowledgeId,
    @Param("queryVector") String queryVector,
    @Param("topK") int topK,
    @Param("threshold") double threshold
);
```

#### 4.3.2 RagService 改造

在现有 `ask` / `askStream` 方法中增加 QA Pair 检索分支：

```java
// 伪代码：RagServiceImpl.ask() 改造
public RagAnswerVO ask(Long knowledgeId, String question, Long providerId) {
    float[] queryVector = embed(question);

    // 1. 并行检索 Chunk 和 QA Pair
    CompletableFuture<List<RagSearchResultVO>> chunkFuture =
        CompletableFuture.supplyAsync(() -> searchChunks(knowledgeId, queryVector));
    CompletableFuture<List<QaPairSearchResultVO>> qaFuture =
        CompletableFuture.supplyAsync(() -> qaPairService.searchSimilar(knowledgeId, queryVector, 3, 0.85));

    List<RagSearchResultVO> chunkResults = chunkFuture.join();
    List<QaPairSearchResultVO> qaResults = qaFuture.join();

    // 2. QA Pair 命中且高分 → 直接返回，不调 LLM
    if (!qaResults.isEmpty() && qaResults.get(0).getScore() >= 0.85) {
        return RagAnswerVO.direct(qaResults.get(0));
    }

    // 3. 否则走现有 RAG 流程，QA Pair 结果也注入上下文
    String context = buildContext(chunkResults, qaResults);
    String answer = callLLM(question, context, providerId);
    return RagAnswerVO.generated(answer, chunkResults, qaResults);
}
```

### 4.4 前端页面设计

#### 4.4.1 入口位置

知识库详情页新增 Tab：`问答对`（与 `文档`、`成员`、`评测` 并列）

#### 4.4.2 列表页

| 列 | 说明 |
|----|------|
| 问题 | 问题内容，超长截断 |
| 答案 | 答案预览，超长截断 |
| 来源 | manual / import / ai 标签 |
| 状态 | pending(黄) / vectorizing(蓝) / active(绿) / failed(红) |
| 创建时间 | 时间戳 |
| 操作 | 编辑、删除 |

顶部操作栏：新增、批量导入（JSONL）、AI 生成

#### 4.4.3 创建/编辑弹窗

- Question：textarea，必填
- Answer：textarea（支持 Markdown），必填
- 提交后异步向量化，列表状态显示 vectorizing → active

#### 4.4.4 批量导入弹窗

- 上传 JSONL 文件
- 预览解析结果（表格展示 question/answer）
- 确认导入

---

## 五、API 接口设计

所有接口挂在现有 KnowledgeController 下，路径前缀 `/api/knowledge/{knowledgeId}/qa-pairs`。

### 5.1 接口列表

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | `/{knowledgeId}/qa-pairs` | 创建问答对 | DEVELOPER+ |
| PUT | `/qa-pairs/{id}` | 更新问答对 | DEVELOPER+ |
| GET | `/{knowledgeId}/qa-pairs` | 分页查询 | MEMBER+ |
| DELETE | `/qa-pairs/{id}` | 删除问答对 | DEVELOPER+ |
| POST | `/{knowledgeId}/qa-pairs/batch-import` | 批量导入 | DEVELOPER+ |
| POST | `/{knowledgeId}/qa-pairs/ai-generate` | AI 生成 | DEVELOPER+ |

### 5.2 接口详情

#### 创建问答对

```
POST /api/knowledge/{knowledgeId}/qa-pairs

Request:
{
    "question": "如何重置密码？",
    "answer": "1. 点击登录页的「忘记密码」\n2. 输入注册邮箱\n3. 查收重置邮件并点击链接\n4. 设置新密码"
}

Response:
{
    "code": 200,
    "data": {
        "id": "2056961707612393473",
        "knowledgeId": "2056961707612393000",
        "question": "如何重置密码？",
        "answer": "1. 点击登录页的「忘记密码」...",
        "source": "manual",
        "status": "pending",
        "tokenCount": 12,
        "createTime": "2026-05-29T10:00:00"
    }
}
```

#### 批量导入

```
POST /api/knowledge/{knowledgeId}/qa-pairs/batch-import
Content-Type: application/json

Request:
{
    "items": [
        {"question": "如何重置密码？", "answer": "..."},
        {"question": "如何联系客服？", "answer": "..."}
    ]
}

Response:
{
    "code": 200,
    "data": 2
}
```

#### 分页查询

```
GET /api/knowledge/{knowledgeId}/qa-pairs?pageNum=1&pageSize=20&keyword=密码

Response:
{
    "code": 200,
    "data": {
        "records": [...],
        "total": 50,
        "pageNum": 1,
        "pageSize": 20
    }
}
```

---

## 六、向量化与检索策略

### 6.1 向量化策略

| 维度 | 决策 |
|------|------|
| 向量化对象 | 仅 question（answer 是结果，不是检索键） |
| 向量化模型 | 复用知识库配置的 embeddingModel |
| 向量维度 | 与模型一致（如 1536） |
| 异步执行 | 创建后异步向量化，通过 status 字段跟踪进度 |

### 6.2 检索策略

| 参数 | 默认值 | 说明 |
|------|--------|------|
| qaTopK | 3 | QA Pair 检索返回条数（不需要太多，命中 1-2 条即可） |
| qaThreshold | 0.85 | QA Pair 命中阈值（比 Chunk 的 0.5 高，要求高置信度才直接返回答案） |
| qaPriority | true | 是否启用 QA 优先返回（高分 QA 直接返回，跳过 LLM） |

以上参数支持在 knowledge.config JSONB 中配置，提供灵活性。

### 6.3 检索流程图

```
用户提问
  │
  ├─→ EmbeddingService.embed(question) → queryVector
  │
  ├──→ embeddingService.searchSimilarSql(knowledgeId, queryVector, topK=5, threshold=0.5)
  │      → List<ChunkResult>
  │
  └──→ qaPairService.searchSimilar(knowledgeId, queryVector, qaTopK=3, qaThreshold=0.85)
         → List<QaPairResult>
  │
  ▼
合并判断：
  ├─ qaPairResults 非空 且 score ≥ 0.85
  │    → 直接返回 { type: "qa", answer: qa.answer, references: [qa] }
  │
  └─ 否则
       → 构建 context = chunkResults + qaPairResults
       → 调用 LLM 生成回答
       → 返回 { type: "generated", answer: llm.answer, references: chunkResults + qaPairResults }
```

---

## 七、与现有功能的集成点

### 7.1 知识库统计

在 Knowledge 实体中新增 `qaPairCount` 字段，通过 `updateStats` 方法维护：

```java
// KnowledgeServiceImpl.updateStats 增加 qaPairCount
void updateStats(Long knowledgeId, int docDelta, int chunkDelta, long tokenDelta, int qaDelta);
```

### 7.2 知识库删除

在 `KnowledgeServiceImpl.deleteById` 中增加级联删除 QA Pair 和关联 Embedding：

```java
// 1. 删除所有 QA Pair 关联的 Embedding
embeddingService.deleteByQaPairKnowledgeId(knowledgeId);
// 2. 删除所有 QA Pair
qaPairService.removeByKnowledgeId(knowledgeId);
```

### 7.3 Agent Tool 调用

`QueryKnowledgeTool` 检索时同时搜索 QA Pair，合并结果返回。

### 7.4 导出功能

支持将 QA Pair 导出为 JSONL 文件（用于备份或迁移到其他知识库）。

---

## 八、开发计划

### 阶段一：基础 CRUD + 向量化（后端）

| 任务 | 文件 | 说明 |
|------|------|------|
| 建表 SQL | `docs/sql/2026-05-29-001.sql` | qa_pair 表 + embedding 表加列 |
| Entity | `entity/QaPair.java` | 实体类 |
| Enums | `enums/QaPairSource.java`, `QaPairStatus.java` | 枚举 |
| Mapper | `mapper/QaPairMapper.java` | Mapper 接口 |
| Service | `service/QaPairService.java` + `impl/QaPairServiceImpl.java` | CRUD + 向量化 |
| Controller | 在 `KnowledgeController.java` 新增方法 | API 接口 |
| DTO/VO | `dto/QaPairCreateDTO.java` 等 | 数据传输对象 |

### 阶段二：检索集成

| 任务 | 文件 | 说明 |
|------|------|------|
| EmbeddingMapper | `mapper/EmbeddingMapper.java` | 新增 QA 向量检索 SQL |
| RagService | `service/impl/RagServiceImpl.java` | 集成 QA 优先检索逻辑 |
| QueryKnowledgeTool | `tool/systemtool/QueryKnowledgeTool.java` | Tool 检索合并 QA |

### 阶段三：前端

| 任务 | 文件 | 说明 |
|------|------|------|
| API | `src/api/qa-pair.js` | 接口封装 |
| 列表页 | `src/components/knowledge/QaPairList.vue` | 问答对列表 |
| 创建弹窗 | `src/components/knowledge/QaPairForm.vue` | 创建/编辑表单 |
| 导入弹窗 | `src/components/knowledge/QaPairImport.vue` | JSONL 导入 |

### 阶段四：高级功能

| 任务 | 说明 |
|------|------|
| AI 生成 | 从文档内容提取问答对 |
| 导出 | 导出为 JSONL |
| 批量操作 | 批量删除、批量重新向量化 |

---

## 九、FAQ

### Q: 为什么不复用现有的 QaChunkStrategy？

QaChunkStrategy 是文档**切片策略**，用于将包含 Q:/A: 格式的文档按问答对切片。它是文档处理管线的一环，产出的是 Chunk。而问答对功能是独立的知识管理入口，用户直接创建/导入问答对，不需要经过"上传文档 → 切片"的流程。

### Q: 为什么不把 QA Pair 存成 Chunk？

语义不同。Chunk 是文档的切片片段，QA Pair 是独立的结构化知识。混在一起会导致：
- Chunk 查询需要额外判断类型
- 统计信息混乱（chunkCount 包含了 QA）
- 后续无法针对 QA Pair 做差异化优化（如答案格式化、命中率统计）

### Q: 为什么 threshold 设为 0.85 而不是和 Chunk 一样用 0.5？

QA Pair 的特点是问题很具体，如果用户的问题和 QA Pair 的 question 高度相似（>0.85），那答案大概率是准确的，可以直接返回。如果 threshold 太低（如 0.5），可能出现"相似但不相关"的 QA 被错误命中，返回不相关的标准答案，体验反而更差。

### Q: QA Pair 的答案什么时候需要 LLM 参与？

- 高分命中（≥0.85）：直接返回标准答案，不调 LLM
- 低分命中或未命中：QA Pair 作为参考资料注入 LLM 上下文，辅助生成回答
- 未来可扩展：QA Pair 答案作为"骨架"，LLM 在此基础上补充个性化内容
