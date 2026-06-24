# 级联删除修复清单

> 创建时间：2026-06-24
> 状态：实施中

---

## 背景

经全量后端代码审查，发现 10 处父子实体删除时未级联清理子记录，导致孤儿数据积累。
本文档列出所有需要修复的级联删除关系、当前状态、修复方案。

---

## 设计原则：扁平编排

**核心思想：谁的孩子谁自己删，不传递调用链；DB 软删先行返回，外部资源异步清理。**

```
AgentServiceImpl.deleteById(agentId)
  ├── agentVersionService.removeByAgentId(agentId)     // 软删，秒完
  ├── chatSessionService.removeByAgentId(agentId)       // 软删，秒完
  └── removeById(agentId)                               // 软删，秒完

// Session 的子记录由 Session 自己清理，Agent 不关心
```

### 规则

| 规则 | 做法 |
|------|------|
| **只删直接子级** | Agent 只删 AgentVersion + ChatSession，不穿透到 Message/ToolCall |
| **批量 SQL 优先** | 禁止 for 循环逐条删除，用 `remove(wrapper)` 或 `update(wrapper)` 一条 SQL 搞定 |
| **DB 操作用软删** | `@TableLogic` 的表直接 `remove(wrapper)`，走逻辑删除，毫秒级 |
| **外部资源 try-catch** | MinIO/Neo4j/Milvus 删除失败只记 warn，不阻塞主流程 |
| **物理删除的子表独立事务** | ToolCall 等无软删除的表，用 `@Transactional(propagation = REQUIRES_NEW)` 隔离 |

### 代码模式

```java
// 父服务：只管直接子级，不穿透
@Override
public void deleteById(Long id) {
    // 1. 批量软删直接子记录（一条 SQL，失败不阻塞）
    safeRemove(() -> agentVersionService.removeByAgentId(id), "AgentVersion");
    safeRemove(() -> chatSessionService.removeByAgentId(id), "ChatSession");
    // 2. 清理外部资源
    safeRemove(() -> deleteOldAvatar(getById(id).getAvatar()), "MinIO头像");
    // 3. 软删自己
    removeById(id);
}

private void safeRemove(Runnable action, String label) {
    try {
        action.run();
    } catch (Exception e) {
        log.warn("[{}] 清理失败，跳过: {}", label, e.getMessage());
    }
}
```

```java
// 子服务：批量删除，一条 SQL
public void removeByAgentId(Long agentId) {
    remove(new LambdaQueryWrapper<ChatSession>().eq(ChatSession::getAgentId, agentId));
}
```

---

## 修复清单

### 1. Agent → AgentVersion + ChatSession

| 项目 | 内容 |
|------|------|
| 父实体 | `Agent`（`agent` 表） |
| 子实体 | `AgentVersion`（FK: `agent_id`）+ `ChatSession`（FK: `agent_id`） |
| 当前行为 | `AgentServiceImpl.deleteById()` 仅删除 Agent 本体 + MinIO 头像 |
| 问题 | AgentVersion 成为孤儿；孤儿会话打开时 Agent 不存在，前端报错 |
| 修复方案 | 注入 `AgentVersionService` + `ObjectProvider<ChatSessionService>`，批量软删 |
| 涉及文件 | `AgentServiceImpl.java` |

### 2. Knowledge → QaPair + KnowledgeGraph

| 项目 | 内容 |
|------|------|
| 父实体 | `Knowledge`（`knowledge` 表） |
| 子实体 | `QaPair`（FK: `knowledge_id`）+ `KnowledgeGraph` + `GraphDocument` + Neo4j |
| 当前行为 | 已级联删除 Document + KnowledgeMember，但**不清理 QaPair 和图数据** |
| 问题 | QA 对及 Milvus 向量成为孤儿；Neo4j 残留图节点 |
| 修复方案 | 注入 `QaPairService` + `GraphService`。QaPair 需逐条删（要清理 Milvus embedding），图数据调用已有 `deleteByKnowledgeId()` |
| 涉及文件 | `KnowledgeServiceImpl.java` |
| 注意 | QaPair 删除需清理 Milvus，不能用纯 SQL 批量删；Graph 删除失败不阻塞 |

### 3. Document → Neo4j 图节点

| 项目 | 内容 |
|------|------|
| 父实体 | `Document`（`document` 表） |
| 子实体 | Neo4j 图节点 + `graph_document` 表记录 |
| 当前行为 | 已清理 Chunk/Embedding/DocumentVersion/MinIO，但**不清理图数据** |
| 问题 | 图谱查询返回已删除文档的实体 |
| 修复方案 | 注入 `GraphService`（`ObjectProvider`），调用已有的 `deleteByDocumentId()` |
| 涉及文件 | `DocumentServiceImpl.java` |

### 4. Message → ToolCall

| 项目 | 内容 |
|------|------|
| 父实体 | `Message`（`message` 表） |
| 子实体 | `ToolCall`（`tool_calls` 表，FK: `message_id`） |
| 当前行为 | `deleteBySessionId()` 和 `deleteMessage()` 均不清理 ToolCall |
| 问题 | **最高频孤儿数据来源**，每次工具调用都写入，长期积累占大量空间 |
| 修复方案 | 注入 `ToolCallMapper`，批量物理删除 `tool_calls WHERE message_id IN (SELECT id FROM message WHERE session_id = ?)` |
| 涉及文件 | `MessageServiceImpl.java` |
| 子表删除方式 | 物理删除（无 `@TableLogic`） |

### 5. ModelProvider → Model

| 项目 | 内容 |
|------|------|
| 父实体 | `ModelProvider`（`model_provider` 表） |
| 子实体 | `Model`（`model` 表，FK: `provider_id`） |
| 当前行为 | `deleteById()` 仅删除 Provider 本体 + 清缓存 |
| 问题 | 前端模型选择器展示已删除 Provider 下的模型 |
| 修复方案 | 注入 `ModelService`，批量软删 `model WHERE provider_id = ?` |
| 涉及文件 | `ModelProviderServiceImpl.java` |

### 6. Prompt → PromptVersion

| 项目 | 内容 |
|------|------|
| 父实体 | `Prompt`（`prompt` 表） |
| 子实体 | `PromptVersion`（`prompt_version` 表，FK: `prompt_key`） |
| 当前行为 | `deleteById()` 仅删除 Prompt 本体 |
| 问题 | Prompt 管理页面展示已删除 Prompt 的历史版本 |
| 修复方案 | 注入 `PromptVersionService`，批量软删 `prompt_version WHERE prompt_key = ?` |
| 涉及文件 | `PromptServiceImpl.java` |
| 注意 | 关联字段是字符串 `promptKey`，不是数字 ID |

### 7. EvalExperiment → EvalExperimentResult

| 项目 | 内容 |
|------|------|
| 父实体 | `EvalExperiment`（`eval_experiment` 表） |
| 子实体 | `EvalExperimentResult`（`eval_experiment_result` 表，FK: `experiment_id`） |
| 当前行为 | `deleteById()` 仅删除实验本体。`restart()` 已有清理逻辑可参考 |
| 问题 | 评测结果成为孤儿 |
| 修复方案 | 注入 `EvalExperimentResultService`，批量软删。`restart()` 中已有 `removeByExperimentId()` 可复用 |
| 涉及文件 | `EvalExperimentServiceImpl.java` |

---

## 修复优先级

| 优先级 | 项 | 理由 |
|--------|-----|------|
| P0 | #4 Message → ToolCall | 最高频写入，孤儿数据增长最快 |
| P0 | #1 Agent → ChatSession | 孤儿会话导致前端报错 |
| P1 | #2 Knowledge → QaPair | 占用 Milvus 存储，影响检索 |
| P1 | #3 Document → Neo4j 图节点 | 图谱查询返回脏数据 |
| P1 | #5 ModelProvider → Model | 前端展示异常 |
| P2 | #1 Agent → AgentVersion | 孤儿版本数据 |
| P2 | #6 Prompt → PromptVersion | 孤儿版本数据 |
| P2 | #7 EvalExperiment → EvalExperimentResult | 孤儿评测结果 |

---

## 涉及文件汇总（级联删除）

| 文件 | 修改内容 |
|------|----------|
| `AgentServiceImpl.java` | 新增 AgentVersion + ChatSession 级联删除 |
| `KnowledgeServiceImpl.java` | 新增 QaPair + KnowledgeGraph 级联删除 |
| `DocumentServiceImpl.java` | 新增 Neo4j 图节点级联删除 |
| `MessageServiceImpl.java` | 新增 ToolCall 级联删除 |
| `ModelProviderServiceImpl.java` | 新增 Model 级联删除 |
| `PromptServiceImpl.java` | 新增 PromptVersion 级联删除 |
| `EvalExperimentServiceImpl.java` | 新增 EvalExperimentResult 级联删除 |

---

# 循环 SQL 优化清单

> 创建时间：2026-06-24
> 状态：部分已修复

## 背景

全量审查后端代码，发现 16 处 for 循环内调用 SQL 的 N+1 问题。
按影响面和调用频率分为 HIGH / MEDIUM / LOW 三级。

### 已修复标记

| 标记 | 含义 |
|------|------|
| **已修复** | 代码已修改，编译通过 |
| **跳过** | 有技术限制或副作用，无法优化 |
| 待修复 | 尚未处理 |

## 优化原则

| 原则 | 做法 |
|------|------|
| **批量查询替代逐个查询** | `listByIds(ids)` + `Map` 查找，一次 SQL 替代 N 次 |
| **批量写入替代逐个写入** | `saveBatch(list)` / `updateBatchById(list)` / `remove(wrapper)` |
| **提升不变量到循环外** | 循环内不变的查询/对象提到循环前 |
| **不影响原有业务逻辑** | 优化仅改 SQL 调用方式，不改变业务语义和返回值 |

---

## 优化清单

### HIGH — 查询频繁或数据量大

#### 1. EvalExperimentServiceImpl.enrichExperiment — 评估器批量查询 ✅已修复

| 项目 | 内容 |
|------|------|
| 位置 | `EvalExperimentServiceImpl.java` enrichExperiment 方法 |
| 问题 | `forEach` 遍历评估器配置，每个调 `evaluatorVersionService.getById()` + `evaluatorService.getById()`，一页实验列表 10 条 × 5 评估器 = 140 次查询 |
| 修复方案 | 提取所有 `evaluatorVersionId` → `evaluatorVersionService.listByIds()` → 提取所有 `evaluatorId` → `evaluatorService.listByIds()` → 构建 Map 查找 |
| 状态 | **已修复** |

#### 1-b. EvalExperimentServiceImpl.enrichExperiment — 数据集 N+1 待修复

| 项目 | 内容 |
|------|------|
| 位置 | `EvalExperimentServiceImpl.java` enrichExperiment 方法 `datasetService.getById()` |
| 问题 | `list` 方法 `forEach(this::enrichExperiment)` 内每个实验单独查 `datasetService.getById(experiment.getDatasetId())`，一页 10 条 = 10 次查询 |
| 修复方案 | 在 `list` 方法中先收集所有 `datasetId` → `datasetService.listByIds()` → 构建 Map，传入 `enrichExperiment` 使用 |
| 涉及文件 | `EvalExperimentServiceImpl.java` |
| 状态 | **已修复** |

#### 2. SearchDocumentsTool.searchDocuments ✅已修复

| 项目 | 内容 |
|------|------|
| 位置 | `SearchDocumentsTool.java` L76-86 |
| 问题 | 遍历 `knowledgeIds`，每个调 `knowledgeService.getById()` + `documentService.listByKnowledgeIdInternal()`，绑定 5 个知识库 = 10 次查询 |
| 修复方案 | `knowledgeService.listByIds(knowledgeIds)` 一次查全 → `documentService.listByKnowledgeIds(knowledgeIds)` 一次查全 → 内存匹配 |
| 状态 | **已修复** |

#### 3. KnowledgeTools.listKnowledgeBases ✅已修复

| 项目 | 内容 |
|------|------|
| 位置 | `KnowledgeTools.java` L63-74 |
| 问题 | 遍历 `knowledgeIds`，每个调 `knowledgeService.getById()`，绑定 5 个知识库 = 5 次查询 |
| 修复方案 | `knowledgeService.listByIds(knowledgeIds)` 一次查全 → `Map` 查找 |
| 状态 | **已修复** |

#### 13. GraphServiceImpl.searchForRag — Neo4j 循环查询 待修复

| 项目 | 内容 |
|------|------|
| 位置 | `GraphServiceImpl.java` L282-312 |
| 问题 | AI 抽取的实体名称列表（3-10 个），每个实体单独执行一条 Cypher 查询（含 2 个 OPTIONAL MATCH 3 跳关系），Agent 对话 RAG 检索高频路径 |
| 修复方案 | 将多个实体名合并为一条 Cypher：`WHERE n.name IN $names`，一次查询返回所有实体的关系，内存分组处理 |
| 涉及文件 | `GraphServiceImpl.java` |
| 状态 | **已修复** |

#### 14. EvalExperimentServiceImpl.executeExperiment — 结果逐条 INSERT 待修复

| 项目 | 内容 |
|------|------|
| 位置 | `EvalExperimentServiceImpl.java` executeExperiment 方法 |
| 问题 | 双重循环：数据项 × 评估器，每次调 `experimentResultService.save(result)` 单条 INSERT + 每个数据项调 `updateById(experiment)` 更新进度。100 条 × 5 评估器 = 500 次 INSERT + 100 次 UPDATE |
| 修复方案 | 结果收集到 List → 每个数据项结束后 `saveBatch` 批量 INSERT；进度更新改为每 10% 或每 N 条才 UPDATE 一次 |
| 涉及文件 | `EvalExperimentServiceImpl.java` |
| 状态 | **已修复** |

### MEDIUM — 后台任务或中等频率

#### 4. DocumentServiceImpl.processDocumentWithProgress — saveChunk 循环 ✅已修复

| 项目 | 内容 |
|------|------|
| 位置 | `DocumentServiceImpl.java` L294-298 |
| 问题 | 分块后逐个 `chunkService.saveChunk()` 插入 |
| 修复方案 | 收集到 List 后 `chunkService.saveBatch(chunks)` 批量插入 |
| 状态 | **已修复** |

#### 5. EvalExperimentServiceImpl.executeExperiment — 评估器重复查询 ✅已修复

| 项目 | 内容 |
|------|------|
| 位置 | `EvalExperimentServiceImpl.java` executeExperiment 方法内层循环 |
| 问题 | 外层遍历数据项 × 内层遍历评估器，每个评估器每次调 `evaluatorVersionService.getById()` + `evaluatorService.getById()` |
| 修复方案 | 在循环外预加载所有评估器版本和评估器信息到 Map，循环内直接查 Map |
| 状态 | **已修复** |

#### 6. ChatSessionServiceImpl.deleteSessions

| 项目 | 内容 |
|------|------|
| 位置 | `ChatSessionServiceImpl.java` L268-274 |
| 问题 | 遍历 ids 逐个调 `messageService.deleteBySessionId()` 和 `llmTraceService.deleteBySessionId()` |
| 修复方案 | 暂不改动 — 每个 session 的消息需清理 MinIO 资源，无法纯 SQL 批量删 |
| 状态 | **跳过**（有 MinIO 副作用） |

#### 7. ChatSessionServiceImpl.deleteByAgentId

| 项目 | 内容 |
|------|------|
| 位置 | `ChatSessionServiceImpl.java` L291-297 |
| 问题 | 遍历 sessions 逐个调 `deleteSession()` |
| 修复方案 | 同 #6，**跳过**（有 MinIO 副作用） |
| 状态 | **跳过**（有 MinIO 副作用） |

#### 8. AgentServiceImpl.setDefaultAgent ✅已修复

| 项目 | 内容 |
|------|------|
| 位置 | `AgentServiceImpl.java` L584-590 |
| 问题 | 查询当前默认 Agent 列表后逐个 `updateById` 清除默认标记 |
| 修复方案 | 用 `lambdaUpdate().set(Agent::getIsDefault, false).update()` 一条 SQL |
| 状态 | **已修复** |

#### 9. GraphExtractionExecutor.execute — GraphDocument 批量更新

| 项目 | 内容 |
|------|------|
| 位置 | `GraphExtractionExecutor.java` L118-125 |
| 问题 | 遍历 `graphDocIds`，每个调 `selectById` + `updateById` |
| 修复方案 | `selectBatchByIds` + `updateBatchById` |
| 状态 | **跳过** — GraphDocumentMapper 继承 BaseMapper，不支持批量方法；N 通常很小 |

#### 15. KnowledgeServiceImpl.deleteById — 循环删除文档

| 项目 | 内容 |
|------|------|
| 位置 | `KnowledgeServiceImpl.java` L175-L182 |
| 问题 | 遍历知识库下所有文档，逐个调 `documentService.deleteDocument(doc.getId())`，每个文档内部多次 SQL（查 chunk/embedding/版本 + 删除） |
| 修复方案 | 每个文档的删除涉及 MinIO/Neo4j 等外部资源清理，无法纯 SQL 批量化。保持现状 |
| 状态 | **跳过**（有外部资源副作用） |

#### 16. QaPairServiceImpl.batchVectorize — 循环逐个向量化

| 项目 | 内容 |
|------|------|
| 位置 | `QaPairServiceImpl.java` L281-293 |
| 问题 | 遍历 `qaPairIds`，逐个调 `vectorize(qaPairId)`，每个内部：getById + checkPermission + delete旧向量 + asyncVectorize |
| 修复方案 | 批量查询所有 QaPair → 统一权限校验 → 批量删除旧向量 → 逐个触发异步向量化（向量化本身是异步的，无法批量化） |
| 涉及文件 | `QaPairServiceImpl.java` |
| 状态 | 待修复（部分可优化：批量查询+批量删旧向量） |

### LOW — 低频操作或收益小

#### 10. DocumentServiceImpl.uploadDocuments

| 项目 | 内容 |
|------|------|
| 位置 | `DocumentServiceImpl.java` L174-178 |
| 问题 | 遍历文件列表逐个调 `uploadDocument()` |
| 修复方案 | 每个文件需独立校验、生成路径、创建任务，难以批量化 |
| 状态 | **跳过** |

#### 11. DocumentServiceImpl.processDocumentWithProgress — 错误路径 ✅已修复

| 项目 | 内容 |
|------|------|
| 位置 | `DocumentServiceImpl.java` L363-367 |
| 问题 | 批量 Embedding 失败时逐个 `chunkService.updateById(chunk)` 标记失败 |
| 修复方案 | `chunkService.updateBatchById(batch)` 批量更新 |
| 状态 | **已修复** |

#### 12. AgentVersionServiceImpl.migrateLegacyIfNeeded — 迁移插入

| 项目 | 内容 |
|------|------|
| 位置 | `AgentVersionServiceImpl.java` L846-877 |
| 问题 | 遍历历史版本逐个 `agentVersionMapper.insert()` |
| 修复方案 | 收集到 List 后 `saveBatch(list)` 批量插入 |
| 状态 | **跳过** — AgentVersionServiceImpl 未继承 ServiceImpl，无 `saveBatch`；低频迁移仅执行一次 |

#### 17. GraphServiceImpl.doExtract — 循环 INSERT/UPDATE GraphDocument

| 项目 | 内容 |
|------|------|
| 位置 | `GraphServiceImpl.java` L130-151 |
| 问题 | 遍历 `targetDocIds`，已有记录调 `updateById`，新记录调 `insert` |
| 修复方案 | 分为 updateList 和 insertList 后批量操作 |
| 状态 | **跳过** — 单文档抽取 N=1，多文档不走此分支，收益极小 |

---

## 修复优先级

| 优先级 | 项 | 状态 | 理由 |
|--------|-----|------|------|
| HIGH | #1 enrichExperiment 评估器 | ✅已修复 | 列表页每页触发 |
| HIGH | #1-b enrichExperiment 数据集 | ✅已修复 | 列表页 N+1 |
| HIGH | #2 SearchDocuments | ✅已修复 | 工具调用频率高 |
| HIGH | #3 KnowledgeTools | ✅已修复 | 工具调用频率高 |
| HIGH | #13 searchForRag Neo4j | ✅已修复 | Agent 对话 RAG 高频路径 |
| HIGH | #14 executeExperiment save | ✅已修复 | 后台任务 SQL 量极大 |
| MEDIUM | #4 saveChunk | ✅已修复 | 入库流程 |
| MEDIUM | #5 executeExperiment 评估器查询 | ✅已修复 | 后台任务 |
| MEDIUM | #8 setDefaultAgent | ✅已修复 | 单条 SQL 替代循环 |
| MEDIUM | #16 batchVectorize | 待修复 | 批量操作可优化部分 |
| LOW | #11 错误路径 updateById | ✅已修复 | 仅错误时触发 |
| SKIP | #6, #7, #9, #10, #12, #15, #17 | 跳过 | 有副作用/技术限制/收益小 |
