# 工具调用结果结构化渲染方案

> 日期：2026-06-21（更新：2026-06-22）
> 范围：lightbot-server 后端工具返回格式 + lightbot-ui 前端渲染逻辑
> 参考：Yuxi 项目 `backend/package/yuxi/knowledge/schemas.py` + `web/src/components/ToolCallingResult/`

---

## 零、Yuxi 项目工具调用链路分析（参考实现）

### 0.1 架构总览

Yuxi 的工具调用渲染分为 **3 层**：

```
┌─────────────────────────────────────────────────────────────────┐
│  Layer 1: Stream Ingestion（SSE 流接收 + 增量合并）              │
│  useAgentStreamHandler.js → mergeMessageChunk()                 │
├─────────────────────────────────────────────────────────────────┤
│  Layer 2: Message Assembly（消息分组 + 工具结果关联）             │
│  messageProcessor.js → convertToolResultToMessages()            │
│  messageGrouping.js → getConversationDisplayItems()             │
├─────────────────────────────────────────────────────────────────┤
│  Layer 3: Component Rendering（注册表 + 插槽组件渲染）           │
│  toolRegistry.js → ToolCallRenderer.vue → BaseToolCall.vue      │
│  → 21 个专用组件（QueryKbTool、FindKbDocumentTool 等）          │
└─────────────────────────────────────────────────────────────────┘
```

### 0.2 Layer 1: SSE 流接收与增量合并

**文件**：`web/src/composables/useAgentStreamHandler.js`

SSE 事件类型及处理逻辑：

| SSE Event | 作用 | 数据格式 |
|-----------|------|---------|
| `message_start` | 新消息开始 | `{message_id, role}` |
| `message_delta` | 内容增量 | `{content: "chunk text"}` |
| `tool_call_start` | 工具调用开始 | `{tool_call: {id, type:"function", function:{name, arguments:""}}}` |
| `tool_call_delta` | 工具参数增量 | `{tool_call_id, arguments_delta: "partial json"}` |
| `tool_call_end` | 工具调用结束 | `{tool_call_id}` |
| `tool_result` | 工具执行结果 | `{tool_call_id, content: "result string"}` |

**增量合并逻辑**（`mergeMessageChunk`）：
- `tool_call_start`：创建 `tool_calls[index]`，记录 `id`、`name`，`arguments` 初始为空串
- `tool_call_delta`：按 `tool_call_id` 查找对应 tool_call，`arguments += delta`（字符串拼接）
- `tool_call_end`：标记该 tool_call 完成
- `tool_result`：按 `tool_call_id` 关联，存储到对应 tool_call 的 `result` 字段

**关键点**：`arguments` 是**增量字符串拼接**，LLM 分多次 chunk 发送完整 JSON，前端在 `tool_call_end` 时才做 `JSON.parse`。

### 0.3 Layer 2: 消息组装与工具结果关联

**文件**：
- `web/src/utils/messageProcessor.js` — `convertToolResultToMessages()`
- `web/src/utils/messageGrouping.js` — `getConversationDisplayItems()`

**`convertToolResultToMessages`** 处理流程：
1. 遍历 SSE 流中的所有事件
2. 将 `tool_call` 事件与对应的 `tool_result` 事件通过 `tool_call_id` 关联
3. 组装成 `toolCall` 对象：`{id, name, arguments, result}`
4. 返回结构化消息列表

**`getConversationDisplayItems`** 分组逻辑：
- 输入：所有消息（`user`、`assistant`、`tool`）
- 输出：`displayItems` 数组，每项类型为 `message` 或 `tool-group`
- 规则：连续的 `tool` 消息归入同一个 `tool-group`，与触发它们的 `assistant` 消息关联

```javascript
// 分组结果示例
[
  { type: 'message', role: 'user', content: '...' },
  { type: 'message', role: 'assistant', content: '让我搜索一下...' },
  { type: 'tool-group', tools: [
      { id: 'call_001', name: 'query_kb', arguments: {...}, result: '...' },
      { id: 'call_002', name: 'find_kb_document', arguments: {...}, result: '...' }
  ]},
  { type: 'message', role: 'assistant', content: '根据搜索结果...' }
]
```

### 0.4 Layer 3: 组件渲染体系

#### 0.4.1 注册表（toolRegistry.js）

**文件**：`web/src/components/ToolCallingResult/toolRegistry.js`

```javascript
// 工具 ID → 渲染组件 ID 映射
const TOOL_ID_MAP = {
  'query_kb': 'query-kb-tool',
  'open_kb_document': 'open-kb-document-tool',
  'find_kb_document': 'find-kb-document-tool',
  'list_kbs': 'list-kbs-tool',
  'get_mindmap': 'get-mindmap-tool',
  // ... 共 27 个映射
}

// 图标映射
const TOOL_ICON_MAP = {
  'query-kb-tool': SearchOutlined,
  'find-kb-document-tool': FileSearchOutlined,
  // ...
}
```

提供 `normalizeToolName(name)` 工具名标准化函数，处理历史消息中可能存在的工具名不一致问题。

#### 0.4.2 路由器（ToolCallRenderer.vue）

**文件**：`web/src/components/ToolCallingResult/ToolCallRenderer.vue`

```vue
<script setup>
import { TOOL_RENDERERS } from './toolRegistry'

const props = defineProps({
  toolCall: Object  // {id, name, arguments, result}
})

const componentId = computed(() => normalizeToolName(props.toolCall.name))
const rendererComponent = computed(() => TOOL_RENDERERS[componentId.value])
const isPlainText = computed(() => {
  try { JSON.parse(props.toolCall.result); return false } catch { return true }
})
</script>

<template>
  <!-- 有专用组件 → 渲染专用组件 -->
  <component :is="rendererComponent" v-if="rendererComponent" :toolCall="toolCall" />
  <!-- 无专用组件 → 通用 JSON/文本展示 -->
  <BaseToolCall v-else :toolCall="toolCall">
    <pre>{{ toolCall.result }}</pre>
  </BaseToolCall>
</template>
```

**TOOL_RENDERERS 映射**：27 个工具 ID → 21 个 Vue 组件（部分工具共享组件）。

#### 0.4.3 基础壳组件（BaseToolCall.vue）

**文件**：`web/src/components/ToolCallingResult/BaseToolCall.vue`

**设计模式**：Vue 插槽（slot）模式

```
┌─ BaseToolCall ──────────────────────────────────┐
│  [Icon] 工具名称                        [状态]  │
│  ─────────────────────────────────────────────  │
│  <slot name="default">                          │
│    ← 专用组件的内容注入到这里                     │
│  </slot>                                         │
│  ─────────────────────────────────────────────  │
│  [展开/收起详情]                                  │
└──────────────────────────────────────────────────┘
```

提供：
- 统一的折叠/展开交互
- 工具名称和图标展示
- 加载状态处理
- 错误状态降级

#### 0.4.4 知识库工具组件详解

**① QueryKbTool.vue — 知识库检索结果**

解析 `query_kb` 工具返回的 JSON：

```json
{
  "kb_id": "...",
  "results": [
    {
      "id": "chunk_id",
      "file_id": "doc_id",
      "content": "匹配的文本片段...",
      "metadata": { "file_name": "文档名", "score": 0.85 }
    }
  ]
}
```

渲染内容：
- **Chunk 列表**：每个 chunk 显示文件名、匹配分数、内容预览
- **知识图谱**：如果结果包含图谱数据，渲染关联关系图
- 点击 chunk 可展开查看完整内容

**② FindKbDocumentTool.vue — 文档内容定位**

解析 `find_kb_document` 工具返回的 JSON：

```json
{
  "kb_id": "...",
  "file_id": "...",
  "match_mode": "regex",
  "total_matches": 5,
  "windows": [
    {
      "start_line": 10,
      "end_line": 15,
      "lines": [
        { "line_num": 12, "text": "匹配行内容", "matched": true }
      ]
    }
  ]
}
```

渲染内容：
- **匹配窗口列表**：每个窗口显示行号范围
- **行内高亮**：匹配行用黄色背景标记
- 使用共享组件 `KbDocumentPreview.vue` 渲染文档预览

**③ OpenKbDocumentTool.vue — 文档内容预览**

解析 `open_kb_document` 工具返回的 JSON：

```json
{
  "kb_id": "...",
  "file_id": "...",
  "start_line": 1,
  "end_line": 100,
  "total_lines": 500,
  "content": "文档完整内容..."
}
```

渲染内容：
- **文档内容**：带行号的代码/文档预览
- **翻页控件**：如果 `end_line < total_lines`，显示"加载更多"
- 使用共享组件 `KbDocumentPreview.vue`

**④ ListKbsTool.vue — 知识库列表**

```json
{
  "kbs": [
    { "kb_id": "...", "name": "HR文档库", "doc_count": 42 },
    { "kb_id": "...", "name": "技术文档", "doc_count": 156 }
  ]
}
```

渲染：卡片列表，每张卡片显示知识库名称和文档数量。

**⑤ GetMindmapTool.vue — 思维导图**

```json
{
  "mindmap": "# 标题\n## 分支1\n- 要点\n## 分支2\n..."
}
```

渲染：`<pre>` 预格式化文本展示 Markdown 结构的思维导图。

#### 0.4.5 共享组件：KbDocumentPreview.vue

**文件**：`web/src/components/ToolCallingResult/tools/KbDocumentPreview.vue`

被 `FindKbDocumentTool` 和 `OpenKbDocumentTool` 共享，提供：
- 带行号的文档内容展示
- 匹配行高亮
- 折叠/展开上下文

### 0.5 Yuxi 工具返回格式总结

| 工具 | 后端返回方式 | 成功格式 | 错误格式 |
|------|------------|---------|---------|
| `query_kb` | Pydantic `model_dump()` → dict | `{kb_id, results: [{id, file_id, content, metadata}]}` | 纯文本 |
| `open_kb_document` | Pydantic `model_dump()` → dict | `{kb_id, file_id, start_line, end_line, total_lines, content}` | 纯文本 |
| `find_kb_document` | Pydantic `model_dump()` → dict | `{kb_id, file_id, match_mode, total_matches, windows: [...]}` | 纯文本 |
| `list_kbs` | 直接构建 dict | `{kbs: [{kb_id, name, doc_count}]}` | 纯文本 |
| `get_mindmap` | 直接构建 dict | `{mindmap: "markdown string"}` | 纯文本 |

**后端不包装额外层**：工具直接返回 dict（JSON），LangChain/LangGraph 序列化后通过 SSE 的 `tool_result` 事件发送，前端 `JSON.parse` 后直接使用。

### 0.6 LightBot vs Yuxi 差异对比

| 维度 | Yuxi | LightBot | 影响 |
|------|------|----------|------|
| 后端框架 | Python + LangChain | Java + SpringAI | JSON 序列化方式不同 |
| SSE 协议 | 自定义 SSE 事件类型 | SpringAI ChatClient SSE | 事件字段名不同 |
| 工具注册 | LangChain Tool 装饰器 | Java Tool 接口 | 无影响，返回格式一致即可 |
| 消息存储 | MongoDB | PostgreSQL JSONB | 无影响 |
| 前端框架 | Vue 3 + Pinia | Vue 3 + Pinia | 基本一致 |

**核心一致点**：工具返回 JSON → SSE 传输 → 前端 JSON.parse → 注册表路由 → 专用组件渲染。

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

详见 **第零章 Yuxi 项目工具调用链路分析**。

核心模式：Pydantic Schema 定义输出结构 → `.model_dump()` 序列化为 dict → LangChain 自动转 JSON → SSE `tool_result` 事件传输 → 前端 `JSON.parse` → 注册表路由 → 专用组件渲染。

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

### 4.1 渲染架构（对齐 Yuxi）

参考 Yuxi 的 3 层架构，LightBot 前端改造如下：

**Layer 1 — SSE 流接收**（已有，无需改动）：
- `chatService.js` 的 SSE 处理已实现 `tool_call` 和 `tool_result` 事件的接收
- `tool_call` 的 `arguments` 增量拼接逻辑已实现

**Layer 2 — 消息组装**（已有，无需改动）：
- `Chat.vue` 中已实现 tool_call 和 tool_result 的关联存储

**Layer 3 — 组件渲染**（需改造）：
- 对齐 Yuxi 的注册表 + 插槽模式
- `ToolCallRenderer.vue` 根据工具名路由到专用组件
- `BaseToolCall.vue` 提供统一壳（折叠/展开、图标、状态）
- 各专用组件解析 JSON 后渲染结构化内容

### 4.2 渲染组件改造

各组件解析 JSON 而非正则匹配文本（对齐 Yuxi 的 `JSON.parse` + 降级模式）：

```vue
<!-- QueryKnowledgeResult.vue -->
<script setup>
const parsed = computed(() => {
  try {
    return JSON.parse(props.toolCall.result)
  } catch {
    return null // 降级到纯文本展示（兼容历史消息）
  }
})

// 纯文本降级（错误信息、无结果提示）
const isPlainText = computed(() => !parsed.value)

// 结构化数据
const qaAnswer = computed(() => parsed.value?.qa_answer)
const items = computed(() => parsed.value?.results || [])
</script>
```

### 4.3 降级策略

```
JSON.parse(event.result)
├── 成功 → 按字段渲染
└── 失败 → <pre>{{ event.result }}</pre> 原样展示
```

错误信息（如 "该智能体未绑定任何知识库"）是纯文本，JSON.parse 会失败，自动降级到 `<pre>` 展示。

### 4.4 文件清单

| 操作 | 文件 | 说明 |
|------|------|------|
| **后端修改** | `QueryKnowledgeTool.java` | 返回 JSON 替代格式化文本 |
| **后端修改** | `FindInDocumentTool.java` | 返回 JSON 替代格式化文本 |
| **后端修改** | `SearchDocumentsTool.java` | 返回 JSON 替代格式化文本 |
| 前端已有 | `tools/QueryKnowledgeResult.vue` | 改为解析 JSON（对齐 Yuxi QueryKbTool） |
| 前端已有 | `tools/FindInDocumentResult.vue` | 改为解析 JSON（对齐 Yuxi FindKbDocumentTool） |
| 前端已有 | `tools/SearchDocumentsResult.vue` | 改为解析 JSON |
| 前端已有 | `ToolCallRenderer.vue` | 不变 |
| 前端已有 | `toolRegistry.js` | 不变 |
| 前端已有 | `ToolCallsGroupComponent.vue` | 不变 |

**参考 Yuxi 的共享组件模式**：
- Yuxi 的 `KbDocumentPreview.vue` 被 `FindKbDocumentTool` 和 `OpenKbDocumentTool` 共享
- LightBot 可类似地将文档预览（行号 + 内容 + 高亮）提取为共享组件
- `FindInDocumentResult.vue` 的搜索模式和原文模式可拆分为两个子组件

---

## 五、难点与风险

### 5.1 LLM 可读性

**问题**：工具返回 JSON 对象，LLM 需要理解 JSON 内容来生成回复。JSON 比格式化文本更难读。

**对策**：
- **Yuxi 实践验证**：Yuxi 全部 5 个知识库工具均返回 JSON dict，LLM（GPT-4/Claude）天然能理解，无需额外处理
- JSON 字段名语义清晰（`content`、`score`、`document_name`），LLM 可直接引用
- Yuxi 的 Pydantic Schema 本身就是给 LangChain 的 tool description，字段名即语义描述
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

*文档更新时间: 2026-06-22*
