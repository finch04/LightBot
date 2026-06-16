# 问答对集成 Agent 检索链路设计文档

> 2026-06-16 | 状态：方案设计

---

## 一、需求分析

### 1.1 背景

问答对（QA Pair）模块已实现基础 CRUD 和向量检索能力（`QaPairService.searchSimilar()`），`QueryKnowledgeTool` 中也已集成并行的 QA + Chunk 检索。但存在以下问题：

| # | 问题 | 现状 |
|---|------|------|
| 1 | **Agent 链路不可见** | QA 检索和 Chunk 检索在同一个 `query_knowledge` 工具内并行执行，前端只看到一个"知识库检索"步骤，无法区分 QA 命中还是 Chunk 命中 |
| 2 | **配置管理混乱** | QA 参数（qaTopK、qaThreshold）从 `knowledge.config` 读取，与其他检索参数（topK、threshold 等）存在 `queryParams` JSONB 中不一致 |
| 3 | **检索测试不包含 QA** | `RagServiceImpl.search()` 只做 Chunk 检索，不搜索问答对，无法在检索测试中验证 QA 效果 |
| 4 | **QA 优先返回未实装** | 设计文档中提出的 `qaPriority`（高分 QA 直接返回，跳过 LLM）尚未实现 |

### 1.2 需求清单

| # | 需求 | 优先级 |
|---|------|--------|
| R1 | Agent 链路中能清晰看到"问答对查询"步骤及其结果 | P0 |
| R2 | QA 参数（qa_top_k、qa_threshold、qa_priority）统一存入 queryParams | P0 |
| R3 | 检索测试支持 QA Pair 搜索，结果可与 Chunk 结果对比 | P0 |
| R4 | qa_priority=true 时，高分 QA 直接返回答案，不走 LLM 合成 | P1 |

---

## 二、现状分析

### 2.1 数据流（当前）

```
用户提问 Agent
  │
  ├─ LLM 决定调用 query_knowledge(question)
  │
  └─ QueryKnowledgeTool.queryKnowledge()
       ├─ 向量化问题 → queryVector
       │
       ├─ 对每个绑定知识库（并行）：
       │    ├─ Chunk 检索: embeddingService.searchSimilarSql()  ← 线程池
       │    └─ QA 检索:   qaPairService.searchSimilar()         ← 线程池
       │    └─ 合并结果
       │
       └─ 返回合并后的文本给 LLM
```

**问题**：QA 检索和 Chunk 检索在同一个工具调用内，前端只看到一个 `tool_call(query_knowledge)` 事件，内部的 `ToolEventEmitter.emit()` 消息混合在一起，无法区分。

### 2.2 配置读取（当前）

```java
// QueryKnowledgeTool 中：
int topK = parseTopK(knowledge);          // 读 queryParams.final_top_k，fallback config.ragTopK
double threshold = parseThreshold(knowledge); // 读 queryParams.similarity_threshold，fallback config.ragThreshold
int qaTopK = parseQaTopK(knowledge);      // 只读 config.qaTopK，不读 queryParams ❌
double qaThreshold = parseQaThreshold(knowledge); // 只读 config.qaThreshold，不读 queryParams ❌
```

**问题**：QA 参数没有纳入 queryParams 优先级链，与 Chunk 参数的读取逻辑不一致。

### 2.3 检索测试（当前）

```java
// RagServiceImpl.search()：
Map<String, Object> mergedParams = buildSearchParams(knowledge, overrides, question);
List<Map<String, Object>> results = embeddingService.searchSimilarSql(...);  // 只有 Chunk ❌
```

**问题**：检索测试只返回 Chunk 结果，QA Pair 完全不参与。

---

## 三、方案设计

### 3.1 Agent 链路可见性

#### 方案对比

| 方案 | 做法 | 优点 | 缺点 |
|------|------|------|------|
| A. 拆成两个工具 | `query_knowledge`（Chunk）+ `query_qa_pairs`（QA），LLM 分别调用 | 链路最清晰，两个步骤完全独立 | LLM 多一次工具调用，增加延迟；需要 LLM 理解何时调用哪个工具；QA 和 Chunk 的问题向量化重复 |
| B. 同工具内区分状态 | 保持一个 `query_knowledge`，通过 `ToolEventEmitter` 发送不同前缀的状态消息 | 零架构改动，延迟不变 | 链路可见性不如方案 A 清晰 |
| C. 同工具内 + 结构化结果标记 | 在 `tool_result` 中标记 QA 命中数量，前端解析后渲染差异化展示 | 兼顾可见性和性能 | 需要前端解析 tool_result 内容 |

**选定方案：B — 同工具内区分状态**

理由：
1. QA 检索和 Chunk 检索共享同一个 `queryVector`，拆成两个工具会导致重复向量化
2. 两者并行执行，总延迟 = max(chunk延迟, qa延迟)，拆开后延迟 = chunk延迟 + qa延迟
3. 通过 `ToolEventEmitter` 发送带前缀的状态消息，前端已有 `tool_status` 事件渲染能力，改动最小

**实现方式**：

```java
// QueryKnowledgeTool 中，对每个知识库的检索流程：

ToolEventEmitter.emit("正在检索知识库「" + kbName + "」的问答对...");

// QA 检索
CompletableFuture<List<Map<String, Object>>> qaFuture = CompletableFuture.supplyAsync(() -> {
    // ...QA 检索逻辑
}, SEARCH_EXECUTOR);

ToolEventEmitter.emit("正在检索知识库「" + kbName + "」的文档块...");

// Chunk 检索
CompletableFuture<List<Map<String, Object>>> chunkFuture = CompletableFuture.supplyAsync(() -> {
    // ...Chunk 检索逻辑
}, SEARCH_EXECUTOR);

// 合并后
ToolEventEmitter.emit("知识库「" + kbName + "」: 问答对命中 " + qaResults.size() + " 条, 文档块命中 " + chunkResults.size() + " 条");
```

前端 `ToolCallsGroupComponent` 已经渲染 `tool_status` 事件，无需改动前端。

### 3.2 配置管理统一

将 QA 参数从 `knowledge.config` 迁移到 `queryParams` JSONB，与 Chunk 参数统一管理。

#### queryParams 新增字段

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `qa_enabled` | boolean | true | 是否启用问答对检索 |
| `qa_top_k` | int | 3 | QA 检索返回条数 |
| `qa_threshold` | double | 0.85 | QA 命中阈值（高于此分数才返回） |
| `qa_priority` | boolean | true | 高分 QA 是否优先返回（跳过 LLM） |

#### 优先级链

```
overrides（运行时覆盖，检索测试用）
  > queryParams（持久化配置）
  > config（旧格式兼容）
  > 硬编码默认值
```

#### QueryKnowledgeTool 改造

```java
private int resolveQaTopK(Knowledge knowledge) {
    // 1. queryParams 优先
    Map<String, Object> qp = parseQueryParams(knowledge);
    if (qp.get("qa_top_k") instanceof Number n) return n.intValue();
    // 2. 兼容旧 config
    if (knowledge.getConfig() != null) {
        try {
            var node = new ObjectMapper().readTree(knowledge.getConfig());
            if (node.has("qaTopK")) return node.get("qaTopK").asInt(3);
        } catch (Exception ignored) {}
    }
    return 3;
}

private double resolveQaThreshold(Knowledge knowledge) {
    Map<String, Object> qp = parseQueryParams(knowledge);
    if (qp.get("qa_threshold") instanceof Number n) return n.doubleValue();
    if (knowledge.getConfig() != null) {
        try {
            var node = new ObjectMapper().readTree(knowledge.getConfig());
            if (node.has("qaThreshold")) return node.get("qaThreshold").asDouble(0.85);
        } catch (Exception ignored) {}
    }
    return 0.85;
}

private boolean resolveQaEnabled(Knowledge knowledge) {
    Map<String, Object> qp = parseQueryParams(knowledge);
    if (qp.get("qa_enabled") instanceof Boolean b) return b;
    return true;  // 默认启用
}

private boolean resolveQaPriority(Knowledge knowledge) {
    Map<String, Object> qp = parseQueryParams(knowledge);
    if (qp.get("qa_priority") instanceof Boolean b) return b;
    return true;  // 默认优先
}
```

#### 前端 QueryParamsModal 新增

在检索配置弹窗中新增"问答对检索"区域：

```html
<a-divider style="margin: 12px 0" />
<a-form-item>
  <template #label>
    <span>启用问答对</span>
    <a-tooltip title="开启后检索时同时搜索问答对，高匹配度的问答对可直接返回标准答案">
      <QuestionCircleOutlined class="field-tip-icon" />
    </a-tooltip>
  </template>
  <a-switch v-model:checked="form.qa_enabled" />
</a-form-item>

<template v-if="form.qa_enabled">
  <a-form-item label="QA 返回数量">
    <a-input-number v-model:value="form.qa_top_k" :min="1" :max="20" style="width: 100%" />
  </a-form-item>

  <a-form-item label="QA 命中阈值">
    <a-input-number v-model:value="form.qa_threshold" :min="0" :max="1" :step="0.05" style="width: 100%" />
  </a-form-item>

  <a-form-item>
    <template #label>
      <span>QA 优先返回</span>
      <a-tooltip title="开启后，当问答对相似度超过阈值时直接返回标准答案，不经过大模型合成">
        <QuestionCircleOutlined class="field-tip-icon" />
      </a-tooltip>
    </template>
    <a-switch v-model:checked="form.qa_priority" />
  </a-form-item>
</template>
```

### 3.3 检索测试集成

#### RagServiceImpl.search() 改造

```java
@Override
public List<RagSearchResultVO> search(Long knowledgeId, String question, Map<String, Object> overrides) {
    // ... 现有校验和参数解析 ...

    float[] queryVector = embedText(question);

    // 并行检索 Chunk 和 QA Pair
    Map<String, Object> mergedParams = buildSearchParams(knowledge, overrides, question);

    CompletableFuture<List<Map<String, Object>>> chunkFuture = CompletableFuture.supplyAsync(() ->
        ((EmbeddingServiceImpl) embeddingService).searchSimilarSql(knowledgeId, queryVector, topK, threshold, mergedParams)
    );

    boolean qaEnabled = resolveQaEnabled(knowledge, overrides);
    CompletableFuture<List<QaPairSearchResultVO>> qaFuture = qaEnabled
        ? CompletableFuture.supplyAsync(() -> {
            int qaTopK = resolveQaTopK(knowledge, overrides);
            double qaThreshold = resolveQaThreshold(knowledge, overrides);
            return qaPairService.searchSimilar(knowledgeId, queryVector, qaTopK, qaThreshold);
        })
        : CompletableFuture.completedFuture(List.of());

    List<Map<String, Object>> chunkResults = chunkFuture.join();
    List<QaPairSearchResultVO> qaResults = qaFuture.join();

    // 转为统一的 RagSearchResultVO
    List<RagSearchResultVO> voList = new ArrayList<>();
    int rank = 0;

    // QA 结果排在前面（优先展示）
    for (QaPairSearchResultVO qa : qaResults) {
        RagSearchResultVO vo = new RagSearchResultVO();
        vo.setContent("【问答对】Q: " + qa.getQuestion() + "\nA: " + qa.getAnswer());
        vo.setRank(++rank);
        vo.setScore(qa.getScore());
        vo.setDocumentName("问答对");
        vo.setDocumentId(null);
        voList.add(vo);
    }

    // Chunk 结果
    for (Map<String, Object> row : chunkResults) {
        RagSearchResultVO vo = new RagSearchResultVO();
        vo.setContent((String) row.get("content"));
        vo.setRank(++rank);
        Object score = row.get("score");
        vo.setScore(score != null ? Math.round(((Number) score).doubleValue() * 10000.0) / 10000.0 : null);
        vo.setDocumentName((String) row.get("document_name"));
        Object documentId = row.get("document_id");
        vo.setDocumentId(documentId != null ? ((Number) documentId).longValue() : null);
        voList.add(vo);
    }

    return voList;
}
```

#### 前端检索测试展示

当前检索测试结果卡片显示 `documentName`、`score`、`content`。QA Pair 结果的 `documentName` 为"问答对"，前端已有条件判断：

```javascript
// 现有逻辑（KnowledgeDetail.vue 检索测试结果渲染）
// documentName 为 "问答对" 时，卡片样式可区分
```

需要在结果卡片中增加类型标识，让用户区分 QA 结果和 Chunk 结果：

```html
<a-tag v-if="item.documentName === '问答对'" color="blue" size="small">QA</a-tag>
```

### 3.4 QA 优先返回（qa_priority）

#### 实现位置

`QueryKnowledgeTool.queryKnowledge()` 中，合并 QA 和 Chunk 结果后、返回给 LLM 之前。

```java
// 合并结果后
List<Map<String, Object>> allKbResults = new ArrayList<>();
allKbResults.addAll(qaResults);
allKbResults.addAll(chunkResults);

// QA 优先返回判断
boolean qaPriority = resolveQaPriority(knowledge);
if (qaPriority && !qaResults.isEmpty()) {
    double topQaScore = ((Number) qaResults.get(0).get("score")).doubleValue();
    if (topQaScore >= resolveQaThreshold(knowledge)) {
        // 高分 QA 直接返回，不等 Chunk 结果
        log.info("[Tool:query_knowledge] QA优先返回: score={}, question={}", topQaScore, qaResults.get(0).get("question"));
        ToolEventEmitter.emit("命中高匹配问答对（相似度 " + String.format("%.2f", topQaScore) + "），直接返回标准答案");
        // 返回 QA 答案，LLM 不需要再合成
        String answer = (String) qaResults.get(0).get("answer");
        return "【问答对命中】\n问题：" + qaResults.get(0).get("question") + "\n答案：" + answer;
    }
}
```

#### 注意事项

1. **QA 优先返回发生在知识库级别**：如果 Agent 绑定了多个知识库，某个知识库的 QA 高分命中不会阻止其他知识库的 Chunk 检索。最终结果合并后，如果存在高分 QA，整体返回 QA 答案。
2. **qa_priority=false 时**：QA 结果和 Chunk 结果合并后一起交给 LLM 合成，QA 仅作为参考资料。
3. **多条高分 QA**：取分数最高的 1 条返回，避免答案冲突。

---

## 四、难点分析

### 4.1 性能

| 场景 | 分析 | 结论 |
|------|------|------|
| QA Pair 数量少（<1000） | pgvector 余弦相似度搜索在千级数据量下 <10ms | 无性能问题 |
| QA Pair 数量中等（1000-10000） | 向量索引（IVFFlat/HNSW）保证查询在 50ms 内 | 可接受 |
| QA Pair 数量大（>10000） | 需要确认 `embedding` 表是否有 `qa_pair_id` 索引 | 需加索引 |
| QA + Chunk 并行 | 当前已是并行执行，总延迟 = max(两者) | 无额外开销 |

**优化措施**：
- 确保 `embedding` 表有 `qa_pair_id` 列的 B-tree 索引
- QA 检索的 SQL 已在 `EmbeddingMapper.searchSimilarQaPairs()` 中做了 SQL 层阈值过滤，不会返回全量数据

### 4.2 QA 优先返回的边界情况

| 场景 | 处理 |
|------|------|
| 多知识库，一个 QA 命中，另一个无 QA | 正常，QA 答案直接返回 |
| 多知识库，多个 QA 命中 | 取所有 QA 中分数最高的 |
| QA 命中但答案过短/过长 | 不做长度限制，信任用户配置的标准答案 |
| qa_threshold 设太低导致误命中 | 用户自行调整阈值，默认 0.85 已较高 |

### 4.3 配置迁移

| 风险 | 应对 |
|------|------|
| 旧数据 config 中有 qaTopK/qaThreshold | 读取时 queryParams 优先，fallback 到 config，无破坏性 |
| 新旧配置同时存在 | queryParams 优先级高于 config，不会冲突 |

---

## 五、涉及文件

| 文件 | 改动类型 | 说明 |
|------|----------|------|
| `tool/systemtool/QueryKnowledgeTool.java` | 修改 | QA 参数读取迁移到 queryParams；状态消息细化；qa_priority 逻辑 |
| `service/impl/RagServiceImpl.java` | 修改 | search() 增加 QA 并行检索 |
| `service/impl/QaPairServiceImpl.java` | 不变 | searchSimilar() 已实现 |
| `components/QueryParamsModal.vue` | 修改 | 新增 QA 配置区域 |
| `views/KnowledgeDetail.vue` | 修改 | 检索测试结果区分 QA/Chunk 标签 |
| `dto/RagSearchResultVO.java` | 可选修改 | 新增 resultType 字段区分来源 |

---

## 六、实施步骤

### Step 1：配置统一（后端）

QueryKnowledgeTool 中的 `parseQaTopK` / `parseQaThreshold` 改为 `resolveQaTopK` / `resolveQaThreshold`，纳入 queryParams 优先级链。新增 `resolveQaEnabled` / `resolveQaPriority`。

### Step 2：状态消息细化（后端）

QueryKnowledgeTool 中，QA 检索和 Chunk 检索分别发送 `ToolEventEmitter.emit()` 状态消息，合并后发送统计消息。

### Step 3：QA 优先返回（后端）

QueryKnowledgeTool 中，合并结果后判断 qa_priority + qa_threshold，高分 QA 直接返回答案文本。

### Step 4：检索测试集成（后端）

RagServiceImpl.search() 增加 QA 并行检索，结果合并后统一返回。RagSearchResultVO 可选新增 resultType 字段。

### Step 5：前端配置（QueryParamsModal）

新增 QA 配置区域（qa_enabled、qa_top_k、qa_threshold、qa_priority），默认值与后端一致。

### Step 6：前端展示（KnowledgeDetail）

检索测试结果中，QA 结果显示 "问答对" 标签，与 Chunk 结果视觉区分。

---

## 七、验证方式

1. **Agent 链路**：Agent 调用知识库检索时，前端工具面板显示"正在检索问答对..."、"问答对命中 X 条"等状态消息
2. **QA 优先返回**：创建一个问答对，用高度相似的问题提问 Agent，确认直接返回标准答案而非 LLM 合成
3. **检索测试**：在知识库详情的检索测试 Tab 中，搜索一个问题，确认结果中同时包含 QA 和 Chunk 结果
4. **配置管理**：在检索配置弹窗中修改 QA 参数，保存后重新检索，确认参数生效
5. **向后兼容**：旧知识库 config 中有 qaTopK/qaThreshold 的，确认仍能正常读取
