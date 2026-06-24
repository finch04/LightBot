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

## 涉及文件汇总

| 文件 | 修改内容 |
|------|----------|
| `AgentServiceImpl.java` | 新增 AgentVersion + ChatSession 级联删除 |
| `KnowledgeServiceImpl.java` | 新增 QaPair + KnowledgeGraph 级联删除 |
| `DocumentServiceImpl.java` | 新增 Neo4j 图节点级联删除 |
| `MessageServiceImpl.java` | 新增 ToolCall 级联删除 |
| `ModelProviderServiceImpl.java` | 新增 Model 级联删除 |
| `PromptServiceImpl.java` | 新增 PromptVersion 级联删除 |
| `EvalExperimentServiceImpl.java` | 新增 EvalExperimentResult 级联删除 |
