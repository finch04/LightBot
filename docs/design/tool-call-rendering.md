# 工具调用结果结构化渲染方案

> 日期：2026-06-21
> 范围：lightbot-server 后端工具返回格式 + lightbot-ui 前端渲染逻辑
> 参考：Yuxi 项目 `backend/package/yuxi/knowledge/schemas.py` + `web/src/components/ToolCallingResult/`

---

## 一、需求分析

### 1.1 现状

| 维度 | 现状 |
|------|------|
| 后端返回格式 | 工具返回格式化文本（`sb.toString()`），非结构化 JSON |
| 前端渲染方式 | 所有工具结果统一用 `<pre>{{ evt.result }}</pre>` 原样展示 |
| 问题 | 前端无法解析具体字段，无法做差异化渲染 |

### 1.2 目标

- 后端工具返回 **JSON 对象**（成功时）或 **纯文本**（错误时）
- 前端按 `JSON.parse(event.result)` 读取具体字段
- 根据字段（如 `result_type`、`mode`）做差异化渲染

### 1.3 Yuxi 的做法（参考）

| 工具 | 返回 | 成功格式 | 错误格式 |
|------|------|---------|---------|
| `query_kb` | `dict` | `{kb_id, results: [{id, file_id, content, metadata}]}` | 纯文本 |
| `open_kb_document` | `dict` | `{kb_id, file_id, start_line, end_line, total_lines, content, ...}` | 纯文本 |
| `find_kb_document` | `dict` | `{kb_id, file_id, match_mode, total_matches, windows: [...]}` | 纯文本 |

**模式**：Pydantic Schema 定义输出结构 → `.model_dump()` 序列化为 dict → LangChain 自动转 JSON

---

## 二、后端工具 JSON 结构设计

### 2.1 query_knowledge — 知识库检索

**当前**：返回 `String`（格式化文本）
**改为**：返回 `String`（JSON 字符串）

```json
{
  "total": 3,
  "qa_answer": null,
  "results": [
    {
      "result_type": "chunk",
      "document_id": 1234567890,
      "document_name": "张三的简历",
      "content": "张三，男，1990年出生...",
      "score": 0.85,
      "knowledge_id": 100,
      "knowledge_name": "HR文档库"
    },
    {
      "result_type": "qa_pair",
      "question": "张三是谁",
      "answer": "张三是公司的高级工程师...",
      "score": 0.92,
      "knowledge_id": 100,
      "knowledge_name": "HR文档库"
    }
  ]
}
```

**QA 优先命中时**：
```json
{
  "total": 1,
  "qa_answer": "张三是公司的高级工程师...",
  "results": []
}
```

**错误/无结果时**：返回纯文本（不包裹 JSON）
```
"该智能体未绑定任何知识库，无法检索。"
```

### 2.2 find_in_document — 文档内容定位

**搜索模式**（query 非空）：
```json
{
  "mode": "search",
  "query": "张三",
  "total_matches": 3,
  "documents": [
    {
      "document_id": 1234567890,
      "document_name": "张三的简历",
      "match_count": 2,
      "matches": [
        {
          "line_num": 15,
          "context_lines": [
            {"line_num": 14, "text": "员工基本信息：", "matched": false},
            {"line_num": 15, "text": "姓名：张三", "matched": true},
            {"line_num": 16, "text": "职位：高级工程师", "matched": false}
          ]
        }
      ]
    }
  ]
}
```

**原文翻页模式**（query 为空，documentId 非空）：
```json
{
  "mode": "open",
  "document_id": 1234567890,
  "document_name": "张三的简历",
  "total_lines": 45,
  "start_line": 1,
  "end_line": 20,
  "has_more": true,
  "next_offset": 20,
  "content": "员工基本信息\n姓名：张三\n职位：高级工程师\n..."
}
```

**错误时**：纯文本
```
"文档不存在: 1234567890"
```

### 2.3 search_documents — 文档名称搜索

```json
{
  "total": 2,
  "documents": [
    {
      "document_id": 1234567890,
      "document_name": "张三的简历",
      "knowledge_id": 100,
      "knowledge_name": "HR文档库"
    },
    {
      "document_id": 1234567891,
      "document_name": "张三的绩效评估",
      "knowledge_id": 100,
      "knowledge_name": "HR文档库"
    }
  ]
}
```

**错误/无结果时**：纯文本
```
"未在知识库中找到文件名包含「张三」的文档。"
```

---

## 三、后端改动清单

### 3.1 QueryKnowledgeTool.java

**改动点**：
1. 成功时返回 `objectMapper.writeValueAsString(resultMap)` 替代 `sb.toString()`
2. 构建 `Map<String, Object>` 结构：`{total, qa_answer, results: [...]}`
3. 错误/无结果时保持纯文本返回（LLM 需要可读的错误信息）

**关键代码**：
```java
// 替代 sb.toString()
Map<String, Object> output = new HashMap<>();
output.put("total", allResults.size());
output.put("qa_answer", null);
output.put("results", allResults.stream().map(row -> {
    Map<String, Object> item = new HashMap<>();
    item.put("result_type", row.get("result_type"));
    item.put("content", row.get("content"));
    item.put("score", row.get("score"));
    // chunk 特有字段
    if (RagResultType.CHUNK.equals(row.get("result_type"))) {
        item.put("document_id", row.get("document_id"));
        item.put("document_name", row.get("document_name"));
    }
    // qa_pair 特有字段
    if (RagResultType.QA_PAIR.equals(row.get("result_type"))) {
        item.put("question", row.get("question"));
        item.put("answer", row.get("answer"));
    }
    item.put("knowledge_id", row.get("knowledge_id"));
    return item;
}).toList());
return objectMapper.writeValueAsString(output);
```

**QA 优先命中时**：
```java
if (qaPriorityHit != null) {
    Map<String, Object> output = new HashMap<>();
    output.put("total", 1);
    output.put("qa_answer", qaPriorityHit.get("answer"));
    output.put("results", List.of());
    return objectMapper.writeValueAsString(output);
}
```

### 3.2 FindInDocumentTool.java

**改动点**：
1. 搜索模式：构建 `{mode:"search", total_matches, documents: [...]}`
2. 原文模式：构建 `{mode:"open", document_id, total_lines, start_line, end_line, content, ...}`
3. 错误时保持纯文本

### 3.3 SearchDocumentsTool.java

**改动点**：
1. 成功时返回 `{total, documents: [{document_id, document_name, knowledge_id, knowledge_name}]}`
2. 错误/无结果时保持纯文本

---

## 四、前端改动清单

### 4.1 渲染组件改造

各组件解析 JSON 而非正则匹配文本：

```vue
<!-- QueryKnowledgeResult.vue -->
<script setup>
const parsed = computed(() => {
  try {
    return JSON.parse(props.event.result)
  } catch {
    return null // 降级到纯文本展示
  }
})

// 纯文本降级（错误信息、无结果提示）
const isPlainText = computed(() => !parsed.value)
const plainText = computed(() => props.event.result)

// 结构化数据
const qaAnswer = computed(() => parsed.value?.qa_answer)
const items = computed(() => parsed.value?.results || [])
</script>
```

### 4.2 降级策略

```
JSON.parse(event.result)
├── 成功 → 按字段渲染
└── 失败 → <pre>{{ event.result }}</pre> 原样展示
```

错误信息（如 "该智能体未绑定任何知识库"）是纯文本，JSON.parse 会失败，自动降级到 `<pre>` 展示。

### 4.3 文件清单

| 操作 | 文件 | 说明 |
|------|------|------|
| **后端修改** | `QueryKnowledgeTool.java` | 返回 JSON 替代格式化文本 |
| **后端修改** | `FindInDocumentTool.java` | 返回 JSON 替代格式化文本 |
| **后端修改** | `SearchDocumentsTool.java` | 返回 JSON 替代格式化文本 |
| 前端已有 | `tools/QueryKnowledgeResult.vue` | 改为解析 JSON |
| 前端已有 | `tools/FindInDocumentResult.vue` | 改为解析 JSON |
| 前端已有 | `tools/SearchDocumentsResult.vue` | 改为解析 JSON |
| 前端已有 | `ToolCallRenderer.vue` | 不变 |
| 前端已有 | `toolRegistry.js` | 不变 |
| 前端已有 | `ToolCallsGroupComponent.vue` | 不变 |

---

## 五、难点与风险

### 5.1 LLM 可读性

**问题**：工具返回 JSON 对象，LLM 需要理解 JSON 内容来生成回复。JSON 比格式化文本更难读。

**对策**：
- 参考 Yuxi：LLM 天然能理解 JSON，不需要额外处理
- JSON 字段名语义清晰（`content`、`score`、`document_name`），LLM 可直接引用
- 如果实测 LLM 理解困难，可在工具 description 中补充返回格式说明

### 5.2 后端 SEARCH_RESULTS_MAP 兼容

**问题**：`QueryKnowledgeTool.getSearchResults()` 返回 `List<Map<String, Object>>`，被 `ChatService` 读取后存入消息 metadata。改为 JSON 返回后，`SEARCH_RESULTS_MAP` 中存储的数据结构不变（仍是 Map 列表），但返回给 LLM 的是 JSON 字符串。

**对策**：
- `SEARCH_RESULTS_MAP` 存储逻辑不变（存原始 Map 列表）
- 仅改变 `return` 语句的序列化方式
- `ChatService` 读取 `getSearchResults()` 的逻辑不变

### 5.3 前端降级兼容

**问题**：历史消息中的 tool_result 是格式化文本，新版本是 JSON。

**对策**：
- 前端 `JSON.parse` 失败时自动降级到 `<pre>` 展示
- 历史消息无需迁移，自然兼容

---

## 六、实施计划

| 阶段 | 内容 | 预估工时 |
|------|------|---------|
| 1 | 后端：QueryKnowledgeTool 返回 JSON | 0.5 天 |
| 2 | 后端：FindInDocumentTool 返回 JSON | 0.5 天 |
| 3 | 后端：SearchDocumentsResult 返回 JSON | 0.5 小时 |
| 4 | 前端：改造 3 个渲染组件解析 JSON | 0.5 天 |
| 5 | 联调测试 | 0.5 天 |

**总计：约 2 人天**

---

*文档更新时间: 2026-06-21*
