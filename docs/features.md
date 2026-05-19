# LightBot 项目功能文档

> 轻量级 Java AI Agent 平台
>
> Tech Stack: SpringBoot 3.3.6 + SpringAI 1.0.0 + Vue3 + PostgreSQL 15 + pgvector

---

## 一、用户管理

### 1.1 用户注册

| 项目 | 说明 |
|------|------|
| 接口 | `POST /api/auth/register` |
| 请求体 | `{ username, password, nickname?, email? }` |
| 业务规则 | 用户名3-32字符，唯一；密码6-64字符，BCrypt加密存储；默认角色为普通用户 |
| 响应 | 用户信息（不含密码） |

### 1.2 用户登录

| 项目 | 说明 |
|------|------|
| 接口 | `POST /api/auth/login` |
| 请求体 | `{ username, password }` |
| 业务规则 | 校验用户名密码；校验账号状态（禁用账号不可登录）；更新最后登录时间；Sa-Token创建会话 |
| 响应 | `{ token, user }` |

### 1.3 用户登出

| 项目 | 说明 |
|------|------|
| 接口 | `POST /api/auth/logout` |
| 业务规则 | 清除 Sa-Token 会话 |

### 1.4 获取当前用户

| 项目 | 说明 |
|------|------|
| 接口 | `GET /api/auth/me` |
| 业务规则 | 通过 Sa-Token 获取当前登录用户信息 |

### 1.5 用户角色

| 角色 | code | 说明 |
|------|------|------|
| 管理员 | admin | 系统管理权限 |
| 普通用户 | user | 基础使用权限 |

### 1.6 用户状态

| 状态 | code | 说明 |
|------|------|------|
| 正常 | active | 可正常登录使用 |
| 禁用 | disabled | 无法登录 |

---

## 二、模型提供商管理

### 2.1 功能概述

管理 AI 模型提供商的 API 配置，支持多家模型服务商。

### 2.2 支持的提供商类型

| 类型 | code | 说明 |
|------|------|------|
| 通义千问 | dashscope | 阿里云 DashScope |
| OpenAI | openai | OpenAI API |
| DeepSeek | deepseek | DeepSeek API |
| Ollama | ollama | 本地 Ollama 服务 |

### 2.3 接口列表

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/model-providers` | POST | 新增提供商 |
| `/api/model-providers` | PUT | 更新提供商 |
| `/api/model-providers/{id}` | DELETE | 删除提供商 |
| `/api/model-providers` | GET | 分页查询 |
| `/api/model-providers/{id}` | GET | 获取单个详情 |

### 2.4 业务规则

- API Key 加密存储
- 提供商状态：active（启用）/ disabled（禁用）
- 软删除

---

## 三、AI 对话

### 3.1 功能概述

基于 SpringAI 的 AI 对话能力，支持同步和流式（SSE）两种模式。

### 3.2 接口列表

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/chat` | POST | 同步对话 |
| `/api/chat/stream` | POST | 流式对话（SSE） |

### 3.3 业务规则

- 消息自动持久化到 `message` 表
- 支持会话管理（创建、查询、归档）
- 自动加载最近20条历史消息保持对话连贯
- 流式模式下，AI回复完整后才持久化
- 每次对话自动更新会话统计（消息数、token数、最后消息时间）

### 3.4 消息角色

| 角色 | code | 说明 |
|------|------|------|
| 用户 | user | 用户发送的消息 |
| 助手 | assistant | AI 回复 |
| 系统 | system | 系统提示词 |
| 工具 | tool | 工具调用结果 |

### 3.5 内容类型

| 类型 | code | 说明 |
|------|------|------|
| 文本 | text | 纯文本消息 |
| 图片 | image | 图片消息 |
| 文件 | file | 文件消息 |

---

## 四、对话会话管理

### 4.1 功能概述

管理用户的对话会话，支持会话的创建、查询、归档、消息历史查看。

### 4.2 接口列表

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/chat/sessions` | POST | 创建新会话 |
| `/api/chat/sessions` | GET | 分页查询当前用户的会话列表 |
| `/api/chat/sessions/{id}` | GET | 获取会话详情 |
| `/api/chat/sessions/{id}/messages` | GET | 获取会话的消息历史 |
| `/api/chat/sessions/{id}/title` | PUT | 更新会话标题 |
| `/api/chat/sessions/{id}/archive` | PUT | 归档会话 |

### 4.3 会话状态

| 状态 | code | 说明 |
|------|------|------|
| 活跃 | active | 正常使用中 |
| 已归档 | archived | 已归档，不再显示在列表中 |

### 4.4 会话统计

- `messageCount`：消息总数
- `totalTokens`：总 Token 消耗
- `lastMessageAt`：最后消息时间（用于排序）

---

## 五、知识库管理

### 5.1 功能概述

知识库是 RAG（检索增强生成）的核心数据载体。用户可以创建知识库、上传文档、管理成员权限，并基于知识库进行 AI 问答。

### 5.2 权限模型

知识库采用成员制权限管理，**非成员无法访问知识库内容**。

| 角色 | code | 权限说明 |
|------|------|----------|
| 创建者 | creator | 完全权限：删除知识库、管理所有成员、所有操作 |
| 管理者 | manager | 可拉人/踢人、上传文档、文档增删改查、提问 |
| 开发者 | developer | 可上传文档、文档增删改查、提问 |
| 查看者 | viewer | 只可提问、查看文档 |

**权限等级**：CREATOR > MANAGER > DEVELOPER > VIEWER

**权限校验规则**：
- 创建知识库时，创建者自动成为 CREATOR 角色成员
- 所有知识库操作都需要校验用户是否为成员
- 文档上传/删除需要 DEVELOPER 及以上权限
- 成员管理需要 MANAGER 及以上权限
- 删除知识库仅 CREATOR 可操作
- RAG 问答需要 VIEWER 及以上权限

### 5.3 知识库接口

| 接口 | 方法 | 权限要求 | 说明 |
|------|------|----------|------|
| `/api/knowledge` | POST | 登录用户 | 创建知识库 |
| `/api/knowledge` | PUT | MANAGER+ | 更新知识库 |
| `/api/knowledge` | GET | 登录用户 | 查询有权限的知识库 |
| `/api/knowledge/{id}` | GET | 成员 | 获取知识库详情 |
| `/api/knowledge/{id}` | DELETE | CREATOR | 删除知识库 |

### 5.4 成员管理接口

| 接口 | 方法 | 权限要求 | 说明 |
|------|------|----------|------|
| `/api/knowledge/{id}/members` | POST | MANAGER+ | 添加成员 |
| `/api/knowledge/{id}/members/{userId}` | PUT | MANAGER+ | 更新成员角色 |
| `/api/knowledge/{id}/members/{userId}` | DELETE | MANAGER+ | 移除成员 |
| `/api/knowledge/{id}/members` | GET | 成员 | 获取成员列表 |

### 5.5 知识库配置

| 字段 | 默认值 | 说明 |
|------|--------|------|
| embeddingModel | text-embedding-3-small | 向量化模型 |
| chunkSize | 512 | 分块大小（字符数） |
| chunkOverlap | 50 | 分块重叠（字符数） |

---

## 六、文档管理

### 6.1 功能概述

知识库下的文档管理，支持 Markdown 文件上传、异步处理（分块+向量化）、预览。

### 6.2 接口列表

| 接口 | 方法 | 权限要求 | 说明 |
|------|------|----------|------|
| `/api/knowledge/{id}/documents` | POST | DEVELOPER+ | 上传文档 |
| `/api/knowledge/{id}/documents` | GET | 成员 | 获取文档列表 |
| `/api/knowledge/documents/{docId}` | GET | 成员 | 获取文档详情 |
| `/api/knowledge/documents/{docId}` | DELETE | DEVELOPER+ | 删除文档 |
| `/api/knowledge/documents/{docId}/preview` | GET | 成员 | 预览文档内容 |
| `/api/knowledge/documents/{docId}/chunks` | GET | 成员 | 获取分块列表 |

### 6.3 文档处理流程

```
用户上传 Markdown 文件
    ↓
1. 校验文件类型（仅支持 .md）
    ↓
2. 计算文件 MD5 哈希，检查去重
    ↓
3. 上传文件到 MinIO
    ↓
4. 创建文档记录（状态：pending）
    ↓
5. 异步处理（@Async）
    ├─ 5.1 更新状态为 processing
    ├─ 5.2 从 MinIO 读取文件内容
    ├─ 5.3 Markdown 分块（先按标题，再按大小）
    ├─ 5.4 保存分块到数据库
    ├─ 5.5 更新文档状态为 completed
    └─ 5.6 更新知识库统计
```

### 6.4 文档状态

| 状态 | code | 说明 |
|------|------|------|
| 待处理 | pending | 已上传，等待处理 |
| 处理中 | processing | 正在分块和向量化 |
| 已完成 | completed | 处理完成，可用于 RAG |
| 失败 | failed | 处理失败，errorMessage 记录原因 |

### 6.5 分块策略

1. **按标题拆分**：优先按 Markdown 标题（# ## ### 等）拆分段落
2. **按大小切分**：超长段落按字符数切分，支持重叠窗口
3. **Token 估算**：中文 1字≈1.5token，英文 1词≈1token

---

## 七、RAG 问答

### 7.1 功能概述

基于知识库的检索增强生成（RAG）问答能力。

### 7.2 接口

| 接口 | 方法 | 权限要求 | 说明 |
|------|------|----------|------|
| `/api/knowledge/{id}/ask` | POST | VIEWER+ | 基于知识库 RAG 问答 |

### 7.3 RAG 流程

```
用户提问
    ↓
1. 问题文本向量化（EmbeddingModel）
    ↓
2. pgvector 余弦相似度检索（Top-5，阈值 0.5）
    ↓
3. 构建参考资料上下文
    ↓
4. 调用 ChatModel 生成回答
    ↓
5. 返回回答
```

### 7.4 向量存储

- 使用 PostgreSQL pgvector 扩展
- 向量维度：1536（text-embedding-3-small）
- 索引类型：HNSW（m=16, ef_construction=64）
- 相似度算法：余弦距离（cosine）

---

## 八、文件存储（MinIO）

### 8.1 功能概述

通过 MinIO 提供对象存储能力，用于存储知识库文档。

### 8.2 存储路径规则

```
knowledge/{knowledgeId}/doc/{uuid}.{ext}
```

### 8.3 能力列表

| 方法 | 说明 |
|------|------|
| upload | 上传文件（MultipartFile / InputStream） |
| download | 下载文件（返回 InputStream） |
| delete | 删除文件 |
| getPresignedUrl | 获取预签名URL（7天有效） |
| generatePath | 生成存储路径 |

---

## 九、待实现功能

### 9.1 Agent 管理

- Agent 的 CRUD（创建、编辑、发布、归档）
- Agent 类型：chat（对话型）、workflow（工作流型）、assistant（助手型）
- Agent 配置：系统提示词、模型选择、工具绑定

### 9.2 Tool 体系

- Tool 的 CRUD
- Tool 类型：builtin（内置）、custom（自定义）、api（API调用）、mcp（MCP协议）
- Tool 认证：none、api_key、oauth、bearer

### 9.3 Skill 体系

- Agent 技能管理
- Skill 关联 Tool 和 Prompt 模板
- 技能排序和启用/禁用

### 9.4 Workflow 引擎

- Workflow 的 CRUD
- DAG 图编辑器
- 节点类型：start、end、llm、tool、condition、code
- 节点间数据传递（NodeContext）

### 9.5 Dashboard 仪表盘

- 系统概览统计
- 使用量趋势图
- 知识库和 Agent 统计

### 9.6 操作日志

- 用户操作日志记录
- 日志查询和导出

### 9.7 MCP 协议

- MCP Server 注册
- Tool 对外暴露
- MCP 请求路由

---

## 十、技术架构

### 10.1 后端架构

```
Controller → Service(Interface) → ServiceImpl → Mapper → Database
                ↓
            Util（中间件封装：MinIO/Redis）
```

### 10.2 前端架构

```
Vue3 + Vite + Element Plus + Pinia + Vue Router
    ↓
API 层（axios） → 后端接口
    ↓
页面组件（views/） → 布局组件（layouts/）
```

### 10.3 数据库

- PostgreSQL 15 + pgvector 扩展
- 14 张业务表（见 sql/init.sql）
- 雪花算法主键（BIGINT）
- 逻辑删除（SMALLINT deleted 字段）
- 自动填充 create_time / update_time

### 10.4 认证鉴权

- Sa-Token 1.39.0
- Token 模式
- 会话管理
