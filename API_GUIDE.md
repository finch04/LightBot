# LightBot API Guide

RESTful API 设计规范与接口文档。

---

## 目录

- [1. RESTful 规范](#1-restful-规范)
- [2. URI 规范](#2-uri-规范)
- [3. Response 规范](#3-response-规范)
- [4. ErrorCode 规范](#4-errorcode-规范)
- [5. 分页规范](#5-分页规范)
- [6. Streaming 规范](#6-streaming-规范)
- [7. SSE 规范](#7-sse-规范)
- [8. WebSocket 规范](#8-websocket-规范)
- [9. Agent API](#9-agent-api)
- [10. Workflow API](#10-workflow-api)
- [11. Tool API](#11-tool-api)
- [12. MCP API](#12-mcp-api)
- [13. Knowledge API](#13-knowledge-api)
- [14. Model API](#14-model-api)

---

## 1. RESTful 规范

### 1.1 HTTP 方法语义

| 方法 | 语义 | 幂等 | 安全 | 示例 |
|------|------|------|------|------|
| `GET` | 查询资源 | Yes | Yes | `GET /api/v1/agents` |
| `POST` | 创建资源 / 触发操作 | No | No | `POST /api/v1/agents` |
| `PUT` | 全量更新资源 | Yes | No | `PUT /api/v1/agents/{id}` |
| `PATCH` | 部分更新资源 | Yes | No | `PATCH /api/v1/agents/{id}` |
| `DELETE` | 删除资源 | Yes | No | `DELETE /api/v1/agents/{id}` |

### 1.2 方法选用原则

```
查询列表          → GET    /resources
查询单个          → GET    /resources/{id}
创建              → POST   /resources
全量替换          → PUT    /resources/{id}
部分字段更新      → PATCH  /resources/{id}
删除              → DELETE /resources/{id}
触发异步操作      → POST   /resources/{id}/actions/xxx
子资源列表        → GET    /resources/{id}/sub-resources
```

### 1.3 版本策略

采用 URI 路径版本：

```
/api/v1/agents
/api/v2/agents
```

| 策略 | 说明 |
|------|------|
| **主版本号** | 不兼容变更时递增（v1 → v2） |
| **次版本号** | 向后兼容新增功能，不体现在 URI 中 |
| **废弃标记** | 响应头 `Deprecation: true` + `Sunset: <date>` |

---

## 2. URI 规范

### 2.1 命名规则

```
✅ 正确
/api/v1/agents
/api/v1/agents/{agent-id}/tools
/api/v1/knowledge-bases/{kb-id}/documents

❌ 错误
/api/v1/getAgents           ← 动词
/api/v1/Agent               ← 大写
/api/v1/agent_list          ← 下划线
/api/v1/agents/{id}/getTools ← 嵌套动词
```

### 2.2 URI 结构

```
/api/{version}/{resource}
/api/{version}/{resource}/{resource-id}
/api/{version}/{resource}/{resource-id}/{sub-resource}
/api/{version}/{resource}/{resource-id}/actions/{action}
```

### 2.3 资源命名表

| 资源 | 单数 URI | 复数 URI |
|------|---------|---------|
| Agent | `/agents/{id}` | `/agents` |
| Workflow | `/workflows/{id}` | `/workflows` |
| Tool | `/tools/{id}` | `/tools` |
| MCP Server | `/mcp-servers/{id}` | `/mcp-servers` |
| Knowledge Base | `/knowledge-bases/{id}` | `/knowledge-bases` |
| Document | `/knowledge-bases/{kb-id}/documents/{doc-id}` | `/knowledge-bases/{kb-id}/documents` |
| Model Provider | `/model-providers/{id}` | `/model-providers` |
| Model | `/model-providers/{provider-id}/models/{model-id}` | `/model-providers/{provider-id}/models` |
| Conversation | `/conversations/{id}` | `/conversations` |
| Message | `/conversations/{conv-id}/messages/{msg-id}` | `/conversations/{conv-id}/messages` |

### 2.4 特殊操作 URI

对于非 CRUD 操作，使用 `actions` 子路径：

```
POST /api/v1/agents/{id}/actions/publish      ← 发布 Agent
POST /api/v1/agents/{id}/actions/unpublish     ← 下线 Agent
POST /api/v1/agents/{id}/actions/duplicate     ← 复制 Agent
POST /api/v1/workflows/{id}/actions/execute    ← 执行 Workflow
POST /api/v1/workflows/{id}/actions/cancel     ← 取消执行
POST /api/v1/documents/{id}/actions/reindex    ← 重建文档索引
POST /api/v1/models/{id}/actions/test          ← 测试模型连接
```

---

## 3. Response 规范

### 3.1 成功响应结构

**单个资源：**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": "agent_a1b2c3d4",
    "name": "客服助手",
    "status": "published",
    "created_at": "2026-05-19T10:30:00Z",
    "updated_at": "2026-05-19T14:20:00Z"
  }
}
```

**列表资源：**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "items": [...],
    "pagination": {
      "page": 1,
      "page_size": 20,
      "total": 156,
      "total_pages": 8
    }
  }
}
```

**无返回体（204）：**

```
HTTP/1.1 204 No Content
```

### 3.2 字段命名规范

| 规则 | 正确 | 错误 |
|------|------|------|
| snake_case | `created_at` | `createdAt` |
| 复数列表 | `items` | `list` / `data` |
| ID 字段 | `agent_id` | `agentId` / `agentID` |
| 布尔字段 | `is_published` | `published` / `isPublished` |
| 时间字段 | ISO 8601 `2026-05-19T10:30:00Z` | 时间戳 / 其他格式 |

### 3.3 ID 规范

所有资源 ID 采用 `前缀_随机串` 格式：

| 资源 | 前缀 | 示例 |
|------|------|------|
| Agent | `agent_` | `agent_a1b2c3d4e5f6` |
| Workflow | `wf_` | `wf_x7y8z9a0b1c2` |
| Tool | `tool_` | `tool_d3e4f5a6b7c8` |
| MCP Server | `mcp_` | `mcp_e9f0a1b2c3d4` |
| Knowledge Base | `kb_` | `kb_f5a6b7c8d9e0` |
| Document | `doc_` | `doc_a1b2c3d4e5f6` |
| Model Provider | `provider_` | `provider_x7y8z9a0b1c2` |
| Model | `model_` | `model_d3e4f5a6b7c8` |
| Conversation | `conv_` | `conv_e9f0a1b2c3d4` |
| Message | `msg_` | `msg_f5a6b7c8d9e0` |

### 3.4 空值处理

| 场景 | 处理方式 |
|------|---------|
| 字段有值 | 返回实际值 |
| 字段为 null | 返回 `null` |
| 字段不存在 | 不出现在响应中 |
| 空字符串 | 返回 `""` |
| 空数组 | 返回 `[]` |
| 空对象 | 返回 `{}` |

---

## 4. ErrorCode 规范

### 4.1 错误响应结构

```json
{
  "code": 40001,
  "message": "Agent name is required",
  "errors": [
    {
      "field": "name",
      "message": "must not be blank",
      "rejected_value": null
    }
  ],
  "request_id": "req_a1b2c3d4e5f6",
  "documentation_url": "https://docs.lightbot.dev/errors/40001"
}
```

### 4.2 错误码分段

| 范围 | 分类 | HTTP Status |
|------|------|-------------|
| `10000-19999` | 通用错误 | 400/401/403/404/500 |
| `20000-29999` | Agent 相关 | 400/404/409/422 |
| `30000-39999` | Workflow 相关 | 400/404/409/422 |
| `40000-49999` | Tool / MCP 相关 | 400/404/409/422 |
| `50000-59999` | Knowledge 相关 | 400/404/409/422 |
| `60000-69999` | Model 相关 | 400/404/409/422 |

### 4.3 通用错误码

| Code | HTTP Status | 说明 |
|------|-------------|------|
| `10000` | 400 | 请求参数校验失败 |
| `10001` | 401 | 未认证（缺少或无效 API Key） |
| `10002` | 403 | 无权限访问该资源 |
| `10003` | 404 | 资源不存在 |
| `10004` | 405 | HTTP 方法不允许 |
| `10005` | 409` | 资源冲突（如名称重复） |
| `10006` | 429 | 请求频率超限 |
| `10007` | 500 | 服务器内部错误 |
| `10008` | 503 | 服务暂时不可用 |

### 4.4 业务错误码

**Agent (20000-29999)：**

| Code | HTTP Status | 说明 |
|------|-------------|------|
| `20001` | 404 | Agent 不存在 |
| `20002` | 409 | Agent 名称已存在 |
| `20003` | 422 | Agent 配置无效 |
| `20004` | 400 | 关联的模型不存在 |
| `20005` | 400 | 关联的 Tool 不存在 |
| `20006` | 409 | Agent 已发布，不允许修改 |
| `20007` | 400 | System Prompt 为空 |
| `20008` | 422 | 变量模板语法错误 |

**Workflow (30000-39999)：**

| Code | HTTP Status | 说明 |
|------|-------------|------|
| `30001` | 404 | Workflow 不存在 |
| `30002` | 409 | Workflow 名称已存在 |
| `30003` | 422 | DAG 存在环 |
| `30004` | 422 | 节点配置无效 |
| `30005` | 400 | 存在未连接的节点 |
| `30006` | 409 | Workflow 正在执行中 |
| `30007` | 404 | 执行记录不存在 |
| `30008` | 400 | Workflow 未发布，不可执行 |

**Tool / MCP (40000-49999)：**

| Code | HTTP Status | 说明 |
|------|-------------|------|
| `40001` | 404 | Tool 不存在 |
| `40002` | 409 | Tool 名称已存在 |
| `40003` | 422 | Tool 参数 Schema 无效 |
| `40004` | 408 | Tool 执行超时 |
| `40005` | 500 | Tool 执行异常 |
| `40006` | 404 | MCP Server 不存在 |
| `40007` | 502 | MCP Server 连接失败 |
| `40008` | 504 | MCP Server 响应超时 |

**Knowledge (50000-59999)：**

| Code | HTTP Status | 说明 |
|------|-------------|------|
| `50001` | 404 | 知识库不存在 |
| `50002` | 409 | 知识库名称已存在 |
| `50003` | 400 | 文档格式不支持 |
| `50004` | 413 | 文档大小超限 |
| `50005` | 404 | 文档不存在 |
| `50006` | 409 | 文档正在处理中 |
| `50007` | 500 | 文档解析失败 |
| `50008` | 500 | 向量化失败 |

**Model (60000-69999)：**

| Code | HTTP Status | 说明 |
|------|-------------|------|
| `60001` | 404 | 模型供应商不存在 |
| `60002` | 404 | 模型不存在 |
| `60003` | 400 | API Key 无效 |
| `60004` | 502 | 模型服务不可达 |
| `60005` | 429 | 模型调用频率超限 |
| `60006` | 500 | 模型返回格式异常 |
| `60007` | 400 | 模型参数无效 |

---

## 5. 分页规范

### 5.1 请求参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `page` | int | `1` | 页码，从 1 开始 |
| `page_size` | int | `20` | 每页条数，最大 100 |

**示例：**

```
GET /api/v1/agents?page=2&page_size=10
```

### 5.2 响应结构

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "items": [
      {"id": "agent_a1b2c3", "name": "客服助手"},
      {"id": "agent_d4e5f6", "name": "代码助手"}
    ],
    "pagination": {
      "page": 2,
      "page_size": 10,
      "total": 35,
      "total_pages": 4
    }
  }
}
```

### 5.3 排序参数

| 参数 | 说明 | 示例 |
|------|------|------|
| `sort_by` | 排序字段 | `sort_by=created_at` |
| `sort_order` | 排序方向 `asc` / `desc` | `sort_order=desc` |

支持的排序字段因资源而异，在各 API 中定义。

### 5.4 过滤参数

使用字段名直接作为查询参数：

```
GET /api/v1/agents?status=published&name=客服
GET /api/v1/documents?file_type=pdf&status=completed
```

模糊匹配字段使用 `_like` 后缀：

```
GET /api/v1/agents?name_like=客服
```

范围查询使用 `_gte` / `_lte` 后缀：

```
GET /api/v1/conversations?created_at_gte=2026-05-01T00:00:00Z
```

---

## 6. Streaming 规范

### 6.1 流式接口标识

流式接口通过 `stream=true` 查询参数启用：

```
POST /api/v1/agents/{id}/chat?stream=true
POST /api/v1/workflows/{id}/execute?stream=true
```

或通过请求体字段：

```json
{
  "message": "你好",
  "stream": true
}
```

### 6.2 流式响应格式

流式响应采用 **Server-Sent Events (SSE)** 格式：

```
HTTP/1.1 200 OK
Content-Type: text/event-stream
Cache-Control: no-cache
Connection: keep-alive
X-Request-Id: req_a1b2c3d4e5f6
```

### 6.3 流式数据帧

```
event: {event_type}
data: {json_payload}\n\n
```

每帧以 `\n\n` 分隔，`data` 为 JSON 字符串。

---

## 7. SSE 规范

### 7.1 SSE 事件类型总览

| 事件 | 说明 | 触发时机 |
|------|------|---------|
| `message_start` | 消息开始 | 流式输出开始 |
| `content_delta` | 内容增量 | 每个文本 Token |
| `tool_call_start` | 工具调用开始 | LLM 决定调用工具 |
| `tool_call_delta` | 工具参数增量 | 工具参数流式生成 |
| `tool_call_end` | 工具调用结束 | 工具执行完成 |
| `message_end` | 消息结束 | 流式输出完成 |
| `error` | 错误事件 | 执行异常 |

### 7.2 事件定义

#### message_start

```json
event: message_start
data: {
  "message_id": "msg_a1b2c3d4",
  "conversation_id": "conv_e9f0a1b2",
  "model": "gpt-4o",
  "created_at": "2026-05-19T10:30:00Z"
}
```

#### content_delta

```json
event: content_delta
data: {
  "delta": "你好",
  "index": 0
}
```

#### tool_call_start

```json
event: tool_call_start
data: {
  "tool_call_id": "tc_a1b2c3d4",
  "tool_name": "web_search",
  "arguments": ""
}
```

#### tool_call_delta

```json
event: tool_call_delta
data: {
  "tool_call_id": "tc_a1b2c3d4",
  "arguments_delta": "{\"query\":"
}
```

#### tool_call_end

```json
event: tool_call_end
data: {
  "tool_call_id": "tc_a1b2c3d4",
  "tool_name": "web_search",
  "arguments": {"query": "LightBot 文档"},
  "result": {
    "status": "success",
    "output": "搜索结果..."
  },
  "duration_ms": 1234
}
```

#### message_end

```json
event: message_end
data: {
  "message_id": "msg_a1b2c3d4",
  "finish_reason": "stop",
  "usage": {
    "prompt_tokens": 520,
    "completion_tokens": 180,
    "total_tokens": 700
  }
}
```

`finish_reason` 取值：

| 值 | 说明 |
|------|------|
| `stop` | 正常结束 |
| `length` | 达到最大 Token 限制 |
| `tool_calls` | 结束于工具调用（等待执行后继续） |
| `cancelled` | 用户取消 |
| `error` | 异常终止 |

#### error

```json
event: error
data: {
  "code": 60005,
  "message": "Model rate limit exceeded",
  "retry_after": 30
}
```

### 7.3 完整 SSE 流示例

```
event: message_start
data: {"message_id":"msg_abc123","conversation_id":"conv_xyz789","model":"gpt-4o","created_at":"2026-05-19T10:30:00Z"}

event: content_delta
data: {"delta":"你好","index":0}

event: content_delta
data: {"delta":"！有什么","index":0}

event: content_delta
data: {"delta":"可以帮你的？","index":0}

event: tool_call_start
data: {"tool_call_id":"tc_001","tool_name":"web_search","arguments":""}

event: tool_call_delta
data: {"tool_call_id":"tc_001","arguments_delta":"{\"query\":"}

event: tool_call_delta
data: {"tool_call_id":"tc_001","arguments_delta":"\"LightBot\"}"}

event: tool_call_end
data: {"tool_call_id":"tc_001","tool_name":"web_search","arguments":{"query":"LightBot"},"result":{"status":"success","output":"Found 3 results"},"duration_ms":856}

event: content_delta
data: {"delta":"根据搜索结果","index":0}

event: content_delta
data: {"delta":"...","index":0}

event: message_end
data: {"message_id":"msg_abc123","finish_reason":"stop","usage":{"prompt_tokens":520,"completion_tokens":180,"total_tokens":700}}
```

---

## 8. WebSocket 规范

### 8.1 连接建立

```
ws://localhost:8080/ws/chat?token={api_key}
wss://api.lightbot.dev/ws/chat?token={api_key}
```

连接成功后服务端推送：

```json
{
  "type": "connection.established",
  "data": {
    "session_id": "ws_a1b2c3d4",
    "server_time": "2026-05-19T10:30:00Z"
  }
}
```

### 8.2 消息协议

**客户端 → 服务端：**

```json
{
  "type": "chat.send",
  "request_id": "req_a1b2c3d4",
  "data": {
    "conversation_id": "conv_xyz789",
    "message": "你好",
    "stream": true
  }
}
```

**服务端 → 客户端：**

```json
{
  "type": "chat.delta",
  "request_id": "req_a1b2c3d4",
  "data": {
    "delta": "你好",
    "index": 0
  }
}
```

### 8.3 消息类型

| 方向 | type | 说明 |
|------|------|------|
| C→S | `chat.send` | 发送消息 |
| C→S | `chat.cancel` | 取消当前生成 |
| S→C | `connection.established` | 连接建立 |
| S→C | `chat.start` | 消息开始 |
| S→C | `chat.delta` | 内容增量 |
| S→C | `chat.tool_call` | 工具调用事件 |
| S→C | `chat.end` | 消息结束 |
| S→C | `chat.error` | 错误事件 |
| S→C | `ping` | 心跳 |
| C→S | `pong` | 心跳回复 |

### 8.4 心跳机制

```
服务端 → 客户端: {"type": "ping", "timestamp": 1716106200000}
客户端 → 服务端: {"type": "pong", "timestamp": 1716106200000}
```

- 心跳间隔：30 秒
- 超时断开：60 秒无响应

---

## 9. Agent API

### 9.1 Agent CRUD

#### 创建 Agent

```
POST /api/v1/agents
```

**Request：**

```json
{
  "name": "客服助手",
  "description": "智能客服 Agent",
  "system_prompt": "你是一个专业的客服助手，请用友好的语气回答用户问题。",
  "model_id": "model_gpt4o",
  "tool_ids": ["tool_search", "tool_knowledge"],
  "knowledge_ids": ["kb_product_docs"],
  "variables": {
    "company_name": "LightBot",
    "language": "zh-CN"
  },
  "config": {
    "temperature": 0.7,
    "max_tokens": 2048,
    "top_p": 0.9,
    "memory": {
      "type": "sliding_window",
      "max_messages": 20
    }
  }
}
```

**Response (201)：**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": "agent_a1b2c3d4",
    "name": "客服助手",
    "description": "智能客服 Agent",
    "status": "draft",
    "system_prompt": "你是一个专业的客服助手...",
    "model_id": "model_gpt4o",
    "tool_ids": ["tool_search", "tool_knowledge"],
    "knowledge_ids": ["kb_product_docs"],
    "variables": {"company_name": "LightBot", "language": "zh-CN"},
    "config": {"temperature": 0.7, "max_tokens": 2048, "top_p": 0.9},
    "created_at": "2026-05-19T10:30:00Z",
    "updated_at": "2026-05-19T10:30:00Z"
  }
}
```

#### 获取 Agent 列表

```
GET /api/v1/agents?page=1&page_size=20&status=published&name_like=客服&sort_by=created_at&sort_order=desc
```

**Response (200)：**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "items": [
      {
        "id": "agent_a1b2c3d4",
        "name": "客服助手",
        "description": "智能客服 Agent",
        "status": "published",
        "model_id": "model_gpt4o",
        "created_at": "2026-05-19T10:30:00Z",
        "updated_at": "2026-05-19T14:20:00Z"
      }
    ],
    "pagination": {
      "page": 1,
      "page_size": 20,
      "total": 1,
      "total_pages": 1
    }
  }
}
```

#### 获取单个 Agent

```
GET /api/v1/agents/{agent_id}
```

**Response (200)：** 同创建响应结构。

#### 更新 Agent

```
PATCH /api/v1/agents/{agent_id}
```

**Request（部分更新）：**

```json
{
  "name": "客服助手 v2",
  "system_prompt": "你是 LightBot 的专业客服助手。"
}
```

**Response (200)：** 返回更新后的完整 Agent 对象。

#### 删除 Agent

```
DELETE /api/v1/agents/{agent_id}
```

**Response (204)：** 无返回体。

### 9.2 Agent 操作

#### 发布 Agent

```
POST /api/v1/agents/{agent_id}/actions/publish
```

**Response (200)：**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": "agent_a1b2c3d4",
    "status": "published",
    "published_at": "2026-05-19T14:20:00Z"
  }
}
```

#### 下线 Agent

```
POST /api/v1/agents/{agent_id}/actions/unpublish
```

#### 复制 Agent

```
POST /api/v1/agents/{agent_id}/actions/duplicate
```

**Request：**

```json
{
  "name": "客服助手（副本）"
}
```

**Response (201)：** 返回新 Agent 完整对象。

### 9.3 Agent Chat

#### 发送消息（同步）

```
POST /api/v1/agents/{agent_id}/chat
```

**Request：**

```json
{
  "conversation_id": "conv_xyz789",
  "message": "你好，我想咨询一下产品价格",
  "stream": false
}
```

**Response (200)：**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "message_id": "msg_abc123",
    "conversation_id": "conv_xyz789",
    "role": "assistant",
    "content": "你好！请问您想了解哪款产品的价格？",
    "tool_calls": [],
    "usage": {
      "prompt_tokens": 520,
      "completion_tokens": 45,
      "total_tokens": 565
    },
    "created_at": "2026-05-19T10:30:05Z"
  }
}
```

#### 发送消息（流式）

```
POST /api/v1/agents/{agent_id}/chat?stream=true
```

**Request：** 同上，`stream` 设为 `true` 或通过查询参数指定。

**Response (200)：** SSE 事件流（见 [SSE 规范](#7-sse-规范)）。

### 9.4 会话管理

#### 获取会话列表

```
GET /api/v1/conversations?agent_id={agent_id}&page=1&page_size=20
```

#### 获取会话消息

```
GET /api/v1/conversations/{conversation_id}/messages?page=1&page_size=50
```

**Response (200)：**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "items": [
      {
        "id": "msg_001",
        "role": "user",
        "content": "你好",
        "created_at": "2026-05-19T10:30:00Z"
      },
      {
        "id": "msg_002",
        "role": "assistant",
        "content": "你好！有什么可以帮你的？",
        "tool_calls": [],
        "usage": {"prompt_tokens": 100, "completion_tokens": 20, "total_tokens": 120},
        "created_at": "2026-05-19T10:30:02Z"
      }
    ],
    "pagination": {"page": 1, "page_size": 50, "total": 2, "total_pages": 1}
  }
}
```

#### 删除会话

```
DELETE /api/v1/conversations/{conversation_id}
```

---

## 10. Workflow API

### 10.1 Workflow CRUD

#### 创建 Workflow

```
POST /api/v1/workflows
```

**Request：**

```json
{
  "name": "客户咨询处理流程",
  "description": "处理客户咨询，包括意图识别、知识检索、回复生成",
  "graph": {
    "nodes": [
      {
        "id": "node_start",
        "type": "start",
        "position": {"x": 0, "y": 200},
        "config": {}
      },
      {
        "id": "node_intent",
        "type": "llm",
        "position": {"x": 200, "y": 200},
        "config": {
          "model_id": "model_gpt4o",
          "prompt": "分析用户意图：{{input.message}}",
          "output_variable": "intent"
        }
      },
      {
        "id": "node_condition",
        "type": "condition",
        "position": {"x": 400, "y": 200},
        "config": {
          "conditions": [
            {"expression": "intent == 'price_query'", "output": "branch_price"},
            {"expression": "intent == 'complaint'", "output": "branch_complaint"},
            {"expression": "true", "output": "branch_default"}
          ]
        }
      },
      {
        "id": "node_search",
        "type": "tool",
        "position": {"x": 600, "y": 100},
        "config": {
          "tool_id": "tool_knowledge",
          "inputs": {"query": "${intent.detail}"}
        }
      },
      {
        "id": "node_end",
        "type": "end",
        "position": {"x": 800, "y": 200},
        "config": {
          "output": "${node_search.result}"
        }
      }
    ],
    "edges": [
      {"source": "node_start", "target": "node_intent"},
      {"source": "node_intent", "target": "node_condition"},
      {"source": "node_condition", "target": "node_search", "source_handle": "branch_price"},
      {"source": "node_search", "target": "node_end"}
    ],
    "variables": [
      {"name": "input", "type": "object", "description": "用户输入"},
      {"name": "intent", "type": "object", "description": "识别的意图"}
    ]
  }
}
```

**Response (201)：**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": "wf_a1b2c3d4",
    "name": "客户咨询处理流程",
    "status": "draft",
    "version": 1,
    "graph": {...},
    "created_at": "2026-05-19T10:30:00Z",
    "updated_at": "2026-05-19T10:30:00Z"
  }
}
```

#### 获取 Workflow 列表

```
GET /api/v1/workflows?page=1&page_size=20&status=published
```

#### 获取单个 Workflow

```
GET /api/v1/workflows/{workflow_id}
```

#### 更新 Workflow

```
PATCH /api/v1/workflows/{workflow_id}
```

#### 删除 Workflow

```
DELETE /api/v1/workflows/{workflow_id}
```

### 10.2 Workflow 操作

#### 发布 Workflow

```
POST /api/v1/workflows/{workflow_id}/actions/publish
```

#### 执行 Workflow

```
POST /api/v1/workflows/{workflow_id}/actions/execute
```

**Request：**

```json
{
  "inputs": {
    "message": "你们的产品多少钱？"
  },
  "stream": false
}
```

**Response (200)：**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "execution_id": "exec_a1b2c3d4",
    "workflow_id": "wf_a1b2c3d4",
    "status": "completed",
    "inputs": {"message": "你们的产品多少钱？"},
    "outputs": {
      "result": "根据我们的产品目录..."
    },
    "node_executions": [
      {
        "node_id": "node_intent",
        "status": "completed",
        "inputs": {"message": "你们的产品多少钱？"},
        "outputs": {"intent": "price_query"},
        "duration_ms": 1200
      },
      {
        "node_id": "node_search",
        "status": "completed",
        "inputs": {"query": "产品价格"},
        "outputs": {"result": "根据我们的产品目录..."},
        "duration_ms": 856
      }
    ],
    "started_at": "2026-05-19T10:30:00Z",
    "completed_at": "2026-05-19T10:30:03Z",
    "duration_ms": 3000
  }
}
```

#### 执行 Workflow（流式）

```
POST /api/v1/workflows/{workflow_id}/actions/execute?stream=true
```

**SSE 事件类型：**

| 事件 | 说明 |
|------|------|
| `execution_start` | 执行开始 |
| `node_start` | 节点开始执行 |
| `node_progress` | 节点执行进度（含 LLM 流式输出） |
| `node_end` | 节点执行完成 |
| `execution_end` | 执行完成 |
| `execution_error` | 执行异常 |

```
event: execution_start
data: {"execution_id":"exec_abc","workflow_id":"wf_xyz","started_at":"2026-05-19T10:30:00Z"}

event: node_start
data: {"node_id":"node_intent","node_type":"llm"}

event: node_progress
data: {"node_id":"node_intent","delta":"意图分析中..."}

event: node_end
data: {"node_id":"node_intent","status":"completed","outputs":{"intent":"price_query"},"duration_ms":1200}

event: execution_end
data: {"execution_id":"exec_abc","status":"completed","duration_ms":3000}
```

#### 取消执行

```
POST /api/v1/workflows/{workflow_id}/actions/cancel
```

**Request：**

```json
{
  "execution_id": "exec_a1b2c3d4"
}
```

#### 获取执行记录

```
GET /api/v1/workflows/{workflow_id}/executions?page=1&page_size=20
```

#### 获取单次执行详情

```
GET /api/v1/workflows/{workflow_id}/executions/{execution_id}
```

---

## 11. Tool API

### 11.1 Tool CRUD

#### 创建 Tool

```
POST /api/v1/tools
```

**Request：**

```json
{
  "name": "web_search",
  "display_name": "网页搜索",
  "description": "搜索互联网获取最新信息",
  "type": "builtin",
  "parameters": {
    "type": "object",
    "properties": {
      "query": {
        "type": "string",
        "description": "搜索关键词"
      },
      "max_results": {
        "type": "integer",
        "description": "最大返回条数",
        "default": 5
      }
    },
    "required": ["query"]
  },
  "config": {
    "timeout_ms": 10000,
    "retry_count": 2
  }
}
```

**Response (201)：**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": "tool_a1b2c3d4",
    "name": "web_search",
    "display_name": "网页搜索",
    "description": "搜索互联网获取最新信息",
    "type": "builtin",
    "parameters": {...},
    "config": {"timeout_ms": 10000, "retry_count": 2},
    "status": "active",
    "created_at": "2026-05-19T10:30:00Z",
    "updated_at": "2026-05-19T10:30:00Z"
  }
}
```

#### 获取 Tool 列表

```
GET /api/v1/tools?type=builtin&status=active
```

#### 获取单个 Tool

```
GET /api/v1/tools/{tool_id}
```

#### 更新 Tool

```
PATCH /api/v1/tools/{tool_id}
```

#### 删除 Tool

```
DELETE /api/v1/tools/{tool_id}
```

### 11.2 Tool 操作

#### 测试 Tool

```
POST /api/v1/tools/{tool_id}/actions/test
```

**Request：**

```json
{
  "arguments": {
    "query": "LightBot AI 平台",
    "max_results": 3
  }
}
```

**Response (200)：**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "status": "success",
    "output": {
      "results": [
        {"title": "LightBot - 轻量级 AI Agent 平台", "url": "https://lightbot.dev"}
      ]
    },
    "duration_ms": 856
  }
}
```

### 11.3 Tool 类型

| type | 说明 | 实现方式 |
|------|------|---------|
| `builtin` | 内置工具 | Java 实现 |
| `http` | HTTP 请求工具 | 配置化 HTTP 调用 |
| `code` | 代码工具 | 沙盒执行脚本 |
| `mcp` | MCP 工具 | MCP Bridge 适配 |

### 11.4 HTTP Tool 配置

```json
{
  "name": "send_email",
  "type": "http",
  "config": {
    "method": "POST",
    "url": "https://api.email.com/send",
    "headers": {
      "Authorization": "Bearer {{api_key}}"
    },
    "timeout_ms": 5000
  }
}
```

---

## 12. MCP API

### 12.1 MCP Server CRUD

#### 注册 MCP Server

```
POST /api/v1/mcp-servers
```

**Request：**

```json
{
  "name": "filesystem-server",
  "description": "文件系统操作 MCP Server",
  "transport": "stdio",
  "config": {
    "command": "npx",
    "args": ["-y", "@modelcontextprotocol/server-filesystem", "/data"],
    "env": {}
  },
  "auto_connect": true
}
```

**Transport 类型：**

| type | 说明 | config 字段 |
|------|------|------------|
| `stdio` | 标准输入输出 | `command`, `args`, `env` |
| `sse` | SSE 传输 | `url`, `headers` |
| `http` | Streamable HTTP | `url`, `headers` |

**Response (201)：**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": "mcp_a1b2c3d4",
    "name": "filesystem-server",
    "transport": "stdio",
    "status": "connected",
    "tools_count": 5,
    "connected_at": "2026-05-19T10:30:00Z",
    "created_at": "2026-05-19T10:30:00Z"
  }
}
```

#### 获取 MCP Server 列表

```
GET /api/v1/mcp-servers?status=connected
```

#### 获取单个 MCP Server

```
GET /api/v1/mcp-servers/{mcp_server_id}
```

#### 更新 MCP Server

```
PATCH /api/v1/mcp-servers/{mcp_server_id}
```

#### 删除 MCP Server

```
DELETE /api/v1/mcp-servers/{mcp_server_id}
```

### 12.2 MCP Server 操作

#### 连接 Server

```
POST /api/v1/mcp-servers/{mcp_server_id}/actions/connect
```

#### 断开连接

```
POST /api/v1/mcp-servers/{mcp_server_id}/actions/disconnect
```

#### 刷新 Tool 列表

```
POST /api/v1/mcp-servers/{mcp_server_id}/actions/refresh-tools
```

**Response (200)：**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "tools": [
      {
        "name": "read_file",
        "description": "读取文件内容",
        "parameters": {
          "type": "object",
          "properties": {
            "path": {"type": "string", "description": "文件路径"}
          },
          "required": ["path"]
        }
      },
      {
        "name": "write_file",
        "description": "写入文件内容",
        "parameters": {...}
      }
    ],
    "total": 5
  }
}
```

#### 调用 MCP Tool

```
POST /api/v1/mcp-servers/{mcp_server_id}/tools/{tool_name}/invoke
```

**Request：**

```json
{
  "arguments": {
    "path": "/data/test.txt"
  }
}
```

**Response (200)：**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "content": [
      {
        "type": "text",
        "text": "文件内容..."
      }
    ],
    "is_error": false,
    "duration_ms": 120
  }
}
```

---

## 13. Knowledge API

### 13.1 Knowledge Base CRUD

#### 创建知识库

```
POST /api/v1/knowledge-bases
```

**Request：**

```json
{
  "name": "产品文档",
  "description": "LightBot 产品使用文档",
  "embedding_model": "text-embedding-3-small",
  "chunk_config": {
    "strategy": "recursive",
    "chunk_size": 512,
    "chunk_overlap": 50
  }
}
```

**Response (201)：**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": "kb_a1b2c3d4",
    "name": "产品文档",
    "description": "LightBot 产品使用文档",
    "embedding_model": "text-embedding-3-small",
    "chunk_config": {"strategy": "recursive", "chunk_size": 512, "chunk_overlap": 50},
    "document_count": 0,
    "chunk_count": 0,
    "status": "active",
    "created_at": "2026-05-19T10:30:00Z",
    "updated_at": "2026-05-19T10:30:00Z"
  }
}
```

#### 获取知识库列表

```
GET /api/v1/knowledge-bases?page=1&page_size=20
```

#### 获取单个知识库

```
GET /api/v1/knowledge-bases/{kb_id}
```

#### 更新知识库

```
PATCH /api/v1/knowledge-bases/{kb_id}
```

#### 删除知识库

```
DELETE /api/v1/knowledge-bases/{kb_id}
```

### 13.2 文档管理

#### 上传文档

```
POST /api/v1/knowledge-bases/{kb_id}/documents
Content-Type: multipart/form-data
```

**Form Fields：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `file` | File | 文档文件 |
| `metadata` | JSON String | 可选元数据 |

支持格式：`.pdf`, `.docx`, `.md`, `.txt`, `.html`, `.csv`

大小限制：单文件 50MB

**Response (202)：**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": "doc_a1b2c3d4",
    "filename": "user-guide.pdf",
    "file_type": "pdf",
    "file_size": 2048576,
    "status": "processing",
    "chunk_count": 0,
    "created_at": "2026-05-19T10:30:00Z"
  }
}
```

#### 获取文档列表

```
GET /api/v1/knowledge-bases/{kb_id}/documents?page=1&page_size=20&status=completed
```

#### 获取单个文档

```
GET /api/v1/knowledge-bases/{kb_id}/documents/{doc_id}
```

#### 删除文档

```
DELETE /api/v1/knowledge-bases/{kb_id}/documents/{doc_id}
```

#### 重建文档索引

```
POST /api/v1/knowledge-bases/{kb_id}/documents/{doc_id}/actions/reindex
```

### 13.3 检索测试

#### 知识检索

```
POST /api/v1/knowledge-bases/{kb_id}/retrieve
```

**Request：**

```json
{
  "query": "如何创建 Agent？",
  "top_k": 5,
  "score_threshold": 0.7,
  "search_type": "hybrid",
  "filters": {
    "file_type": ["pdf", "md"]
  }
}
```

| search_type | 说明 |
|-------------|------|
| `vector` | 纯向量检索 |
| `keyword` | 关键词检索 |
| `hybrid` | 混合检索 |

**Response (200)：**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "results": [
      {
        "chunk_id": "chunk_001",
        "document_id": "doc_a1b2c3d4",
        "document_name": "user-guide.pdf",
        "content": "创建 Agent 的步骤如下：1. 进入 Agent 管理页面...",
        "score": 0.92,
        "metadata": {
          "page": 15,
          "section": "Agent 管理"
        }
      }
    ],
    "total": 3,
    "query_embedding_ms": 45,
    "search_ms": 120
  }
}
```

---

## 14. Model API

### 14.1 Model Provider CRUD

#### 注册模型供应商

```
POST /api/v1/model-providers
```

**Request：**

```json
{
  "name": "openai",
  "display_name": "OpenAI",
  "type": "openai",
  "config": {
    "api_key": "sk-xxx",
    "base_url": "https://api.openai.com/v1"
  }
}
```

**供应商类型：**

| type | 说明 |
|------|------|
| `openai` | OpenAI 兼容接口（含 Azure、各类转发） |
| `qwen` | 通义千问 |
| `ollama` | 本地 Ollama |
| `custom` | 自定义 HTTP 接口 |

**Response (201)：**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": "provider_a1b2c3d4",
    "name": "openai",
    "display_name": "OpenAI",
    "type": "openai",
    "status": "active",
    "model_count": 5,
    "created_at": "2026-05-19T10:30:00Z"
  }
}
```

#### 获取供应商列表

```
GET /api/v1/model-providers
```

#### 获取单个供应商

```
GET /api/v1/model-providers/{provider_id}
```

#### 更新供应商

```
PATCH /api/v1/model-providers/{provider_id}
```

#### 删除供应商

```
DELETE /api/v1/model-providers/{provider_id}
```

### 14.2 模型管理

#### 获取供应商下的模型列表

```
GET /api/v1/model-providers/{provider_id}/models
```

#### 添加模型

```
POST /api/v1/model-providers/{provider_id}/models
```

**Request：**

```json
{
  "model_id": "gpt-4o",
  "display_name": "GPT-4o",
  "type": "chat",
  "context_window": 128000,
  "max_output_tokens": 16384,
  "config": {
    "supports_stream": true,
    "supports_tool_call": true,
    "supports_vision": true
  }
}
```

**模型类型：**

| type | 说明 |
|------|------|
| `chat` | 对话模型 |
| `embedding` | 向量化模型 |
| `rerank` | 重排序模型 |
| `image` | 图像生成模型 |

#### 更新模型配置

```
PATCH /api/v1/model-providers/{provider_id}/models/{model_id}
```

#### 删除模型

```
DELETE /api/v1/model-providers/{provider_id}/models/{model_id}
```

### 14.3 模型操作

#### 测试模型连接

```
POST /api/v1/model-providers/{provider_id}/models/{model_id}/actions/test
```

**Response (200)：**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "status": "success",
    "latency_ms": 234,
    "model_version": "gpt-4o-2024-08-06",
    "test_response": "Hello! How can I help you?"
  }
}
```

#### 获取可用模型列表

```
GET /api/v1/models?type=chat&status=active
```

返回所有供应商下可用的模型列表，供 Agent/Workflow 选择。

**Response (200)：**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "items": [
      {
        "id": "model_a1b2c3d4",
        "provider_id": "provider_x7y8z9",
        "provider_name": "openai",
        "model_id": "gpt-4o",
        "display_name": "GPT-4o",
        "type": "chat",
        "context_window": 128000,
        "supports_stream": true,
        "supports_tool_call": true
      }
    ]
  }
}
```

---

## 附录

### A. HTTP Status Code 使用规范

| Status | 语义 | 使用场景 |
|--------|------|---------|
| `200` | OK | 查询成功、更新成功、操作执行成功 |
| `201` | Created | 资源创建成功 |
| `202` | Accepted | 异步任务已接受（文档处理等） |
| `204` | No Content | 删除成功 |
| `400` | Bad Request | 参数校验失败 |
| `401` | Unauthorized | 未认证 |
| `403` | Forbidden | 无权限 |
| `404` | Not Found | 资源不存在 |
| `405` | Method Not Allowed | HTTP 方法不支持 |
| `409` | Conflict | 资源冲突 |
| `413` | Payload Too Large | 请求体超限 |
| `422` | Unprocessable Entity | 业务校验失败 |
| `429` | Too Many Requests | 频率超限 |
| `500` | Internal Server Error | 服务器异常 |
| `502` | Bad Gateway | 外部服务异常 |
| `503` | Service Unavailable | 服务不可用 |
| `504` | Gateway Timeout | 外部服务超时 |

### B. 请求头规范

| Header | 必填 | 说明 |
|--------|------|------|
| `Authorization` | Yes | `Bearer {api_key}` |
| `Content-Type` | Yes* | `application/json`（POST/PUT/PATCH） |
| `X-Request-Id` | No | 请求追踪 ID，不传则服务端生成 |
| `Accept` | No | `application/json` 或 `text/event-stream` |
| `Accept-Language` | No | 响应语言偏好 |

### C. 速率限制

响应头返回限流信息：

```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 95
X-RateLimit-Reset: 1716106260
Retry-After: 30
```

| 限流维度 | 默认值 | 说明 |
|----------|--------|------|
| API 总调用 | 100 次/分钟 | 按 API Key |
| Chat 调用 | 20 次/分钟 | 按 Agent |
| Workflow 执行 | 10 次/分钟 | 按 Workflow |
| 文档上传 | 5 次/分钟 | 按知识库 |

### D. 认证方式

**API Key 认证：**

```
Authorization: Bearer lb_sk_a1b2c3d4e5f6g7h8i9j0
```

API Key 格式：`lb_sk_{32位随机串}`

**获取方式：** Dashboard → 设置 → API Key 管理

---

*Last updated: 2026-05-19*
