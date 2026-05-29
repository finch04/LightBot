# LightBot 数据库设计

> PostgreSQL 15 + pgvector

---

## ER 关系图

```text
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                                    LightBot ER Diagram                                  │
└─────────────────────────────────────────────────────────────────────────────────────────┘

┌──────────────┐       ┌──────────────┐       ┌──────────────────┐
│     user     │       │model_provider│       │       tool       │
│──────────────│       │──────────────│       │──────────────────│
│ id (PK)      │       │ id (PK)      │       │ id (PK)          │
│ username     │       │ name         │       │ name             │
│ email        │       │ type         │       │ description      │
│ password     │       │ api_key      │       │ input_schema     │
└──────┬───────┘       │ base_url     │       │ tool_type        │
       │               │ config       │       │ config           │
       │               └──────┬───────┘       └────────┬─────────┘
       │                      │                        │
       │ 1:N                  │ 1:N                    │ 1:N
       │                      │                        │
       ▼                      ▼                        ▼
┌──────────────┐       ┌──────────────┐       ┌──────────────────┐
│    agent     │───────│  agent_model │       │    skill         │
│──────────────│       │──────────────│       │──────────────────│
│ id (PK)      │  M:N  │ agent_id(FK) │       │ id (PK)          │
│ user_id (FK) │       │ model_id(FK) │       │ tool_id (FK)     │
│ name         │       │ is_default   │       │ agent_id (FK)    │
│ description  │       └──────────────┘       │ name             │
│ system_prompt│                               │ prompt_template  │
│ avatar       │                               │ config           │
│ config       │                               └──────────────────┘
│ status       │
└──────┬───────┘
       │ 1:N
       ▼
┌──────────────────┐
│   chat_session   │
│──────────────────│
│ id (PK)          │
│ agent_id (FK)    │
│ user_id (FK)     │
│ title            │
│ status           │
│ context          │
│ message_count    │
│ last_message_at  │
└──────┬───────────┘
       │ 1:N
       ▼
┌──────────────────┐
│     message      │
│──────────────────│
│ id (PK)          │
│ session_id (FK)  │
│ role             │
│ content          │
│ content_type     │
│ tool_calls       │
│ token_count      │
│ metadata         │
└──────────────────┘

┌──────────────────┐       ┌──────────────────┐       ┌──────────────────┐
│    knowledge     │       │    document      │       │      chunk       │
│──────────────────│       │──────────────────│       │──────────────────│
│ id (PK)          │ 1:N   │ id (PK)          │ 1:N   │ id (PK)          │
│ user_id (FK)     │──────▶│ knowledge_id(FK) │──────▶│ document_id (FK) │
│ name             │       │ user_id (FK)     │       │ content          │
│ description      │       │ name             │       │ metadata         │
│ embedding_model  │       │ file_path        │       │ token_count      │
│ config           │       │ file_type        │       │ chunk_index      │
│ document_count   │       │ file_size        │       │ embedding_id(FK) │
│ chunk_count      │       │ chunk_count      │       └──────────────────┘
└──────────────────┘       │ status           │               │
                           │ metadata         │               │ 1:1
                           └──────────────────┘               ▼
                                                      ┌──────────────────┐
                                                      │    embedding     │
                                                      │──────────────────│
                                                      │ id (PK)          │
                                                      │ chunk_id (FK)    │
                                                      │ model_name       │
                                                      │ vector (1536)    │
                                                      │ dimension        │
                                                      └──────────────────┘
```

---

## 表结构定义

### 1. user - 用户表

```sql
CREATE TABLE t_user (
    id              BIGINT          NOT NULL,
    username        VARCHAR(64)     NOT NULL,
    email           VARCHAR(128)    NOT NULL,
    password        VARCHAR(256)    NOT NULL,
    nickname        VARCHAR(64),
    avatar          VARCHAR(512),
    phone           VARCHAR(20),
    role            VARCHAR(20)     NOT NULL DEFAULT 'user',
    status          VARCHAR(20)     NOT NULL DEFAULT 'active',
    last_login_at   TIMESTAMP,
    config          JSONB           DEFAULT '{}',
    create_time     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

-- 唯一索引
CREATE UNIQUE INDEX uk_user_username ON t_user (username) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_user_email ON t_user (email) WHERE deleted = 0;

-- 普通索引
CREATE INDEX idx_user_status ON t_user (status);
CREATE INDEX idx_user_create_time ON t_user (create_time);

COMMENT ON TABLE t_user IS '用户表';
COMMENT ON COLUMN t_user.id IS '主键ID';
COMMENT ON COLUMN t_user.username IS '用户名';
COMMENT ON COLUMN t_user.email IS '邮箱';
COMMENT ON COLUMN t_user.password IS '密码(BCrypt加密)';
COMMENT ON COLUMN t_user.nickname IS '昵称';
COMMENT ON COLUMN t_user.avatar IS '头像URL';
COMMENT ON COLUMN t_user.phone IS '手机号';
COMMENT ON COLUMN t_user.role IS '角色: admin/user';
COMMENT ON COLUMN t_user.status IS '状态: active/disabled/deleted';
COMMENT ON COLUMN t_user.last_login_at IS '最后登录时间';
COMMENT ON COLUMN t_user.config IS '扩展配置(JSON)';
COMMENT ON COLUMN t_user.create_time IS '创建时间';
COMMENT ON COLUMN t_user.update_time IS '更新时间';
COMMENT ON COLUMN t_user.deleted IS '逻辑删除: 0-未删除 1-已删除';
```

**字段说明**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | BIGINT | 是 | 雪花算法生成 |
| username | VARCHAR(64) | 是 | 唯一，用于登录 |
| email | VARCHAR(128) | 是 | 唯一，用于通知 |
| password | VARCHAR(256) | 是 | BCrypt 加密存储 |
| role | VARCHAR(20) | 是 | admin-管理员, user-普通用户 |
| status | VARCHAR(20) | 是 | active-正常, disabled-禁用 |
| config | JSONB | 否 | 扩展配置，如偏好设置 |

---

### 2. model_provider - 模型提供商表

```sql
CREATE TABLE t_model_provider (
    id              BIGINT          NOT NULL,
    name            VARCHAR(64)     NOT NULL,
    type            VARCHAR(32)     NOT NULL,
    api_key         VARCHAR(512),
    base_url        VARCHAR(256),
    config          JSONB           DEFAULT '{}',
    status          VARCHAR(20)     NOT NULL DEFAULT 'active',
    create_time     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE INDEX idx_model_provider_type ON t_model_provider (type);
CREATE INDEX idx_model_provider_status ON t_model_provider (status);

COMMENT ON TABLE t_model_provider IS '模型提供商表';
COMMENT ON COLUMN t_model_provider.id IS '主键ID';
COMMENT ON COLUMN t_model_provider.name IS '提供商名称';
COMMENT ON COLUMN t_model_provider.type IS '类型: openai/dashscope/deepseek/ollama';
COMMENT ON COLUMN t_model_provider.api_key IS 'API密钥(加密存储)';
COMMENT ON COLUMN t_model_provider.base_url IS 'API基础地址';
COMMENT ON COLUMN t_model_provider.config IS '扩展配置';
COMMENT ON COLUMN t_model_provider.status IS '状态: active/disabled';
```

**字段说明**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | BIGINT | 是 | 雪花算法生成 |
| name | VARCHAR(64) | 是 | 如 "OpenAI"、"通义千问" |
| type | VARCHAR(32) | 是 | 枚举：openai/dashscope/deepseek/ollama |
| api_key | VARCHAR(512) | 否 | AES 加密存储 |
| base_url | VARCHAR(256) | 否 | 自定义 API 地址 |
| config | JSONB | 否 | 模型参数、超时配置等 |

**config 示例**：

```json
{
    "timeout": 30000,
    "max_retries": 3,
    "default_model": "gpt-4",
    "available_models": ["gpt-4", "gpt-3.5-turbo"]
}
```

---

### 3. agent - Agent 表

```sql
CREATE TABLE t_agent (
    id              BIGINT          NOT NULL,
    user_id         BIGINT          NOT NULL,
    name            VARCHAR(128)    NOT NULL,
    description     TEXT,
    system_prompt   TEXT,
    avatar          VARCHAR(512),
    agent_type      VARCHAR(32)     NOT NULL DEFAULT 'chat',
    config          JSONB           DEFAULT '{}',
    status          VARCHAR(20)     NOT NULL DEFAULT 'draft',
    publish_time    TIMESTAMP,
    version         INT             NOT NULL DEFAULT 1,
    create_time     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE INDEX idx_agent_user_id ON t_agent (user_id);
CREATE INDEX idx_agent_status ON t_agent (status);
CREATE INDEX idx_agent_create_time ON t_agent (create_time);

COMMENT ON TABLE t_agent IS 'Agent表';
COMMENT ON COLUMN t_agent.id IS '主键ID';
COMMENT ON COLUMN t_agent.user_id IS '创建者ID';
COMMENT ON COLUMN t_agent.name IS 'Agent名称';
COMMENT ON COLUMN t_agent.description IS 'Agent描述';
COMMENT ON COLUMN t_agent.system_prompt IS '系统提示词';
COMMENT ON COLUMN t_agent.avatar IS '头像URL';
COMMENT ON COLUMN t_agent.agent_type IS '类型: chat/workflow/assistant';
COMMENT ON COLUMN t_agent.config IS '扩展配置';
COMMENT ON COLUMN t_agent.status IS '状态: draft/published/archived';
COMMENT ON COLUMN t_agent.publish_time IS '发布时间';
COMMENT ON COLUMN t_agent.version IS '版本号';
```

**字段说明**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | BIGINT | 是 | 雪花算法生成 |
| user_id | BIGINT | 是 | 创建者，关联 t_user.id |
| name | VARCHAR(128) | 是 | Agent 名称 |
| system_prompt | TEXT | 否 | 系统提示词 |
| agent_type | VARCHAR(32) | 是 | chat-对话型, workflow-工作流型 |
| status | VARCHAR(20) | 是 | draft-草稿, published-已发布, archived-已归档 |
| version | INT | 是 | 版本号，每次发布递增 |

**config 示例**：

```json
{
    "model_id": 1,
    "temperature": 0.7,
    "max_tokens": 4096,
    "top_p": 0.9,
    "tools": [1, 2, 3],
    "knowledge_ids": [1, 2],
    "welcome_message": "你好，我是AI助手",
    "suggested_questions": ["你能做什么？"]
}
```

---

### 4. tool - Tool 表

```sql
CREATE TABLE t_tool (
    id              BIGINT          NOT NULL,
    user_id         BIGINT,
    name            VARCHAR(64)     NOT NULL,
    display_name    VARCHAR(128),
    description     TEXT,
    tool_type       VARCHAR(32)     NOT NULL DEFAULT 'builtin',
    input_schema    JSONB           NOT NULL DEFAULT '{}',
    output_schema   JSONB           DEFAULT '{}',
    config          JSONB           DEFAULT '{}',
    endpoint_url    VARCHAR(512),
    auth_type       VARCHAR(32),
    auth_config     JSONB           DEFAULT '{}',
    status          VARCHAR(20)     NOT NULL DEFAULT 'active',
    create_time     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_tool_name ON t_tool (name) WHERE deleted = 0;
CREATE INDEX idx_tool_type ON t_tool (tool_type);
CREATE INDEX idx_tool_status ON t_tool (status);

COMMENT ON TABLE t_tool IS 'Tool表';
COMMENT ON COLUMN t_tool.id IS '主键ID';
COMMENT ON COLUMN t_tool.user_id IS '创建者ID(系统内置为NULL)';
COMMENT ON COLUMN t_tool.name IS 'Tool唯一标识';
COMMENT ON COLUMN t_tool.display_name IS '显示名称';
COMMENT ON COLUMN t_tool.description IS 'Tool描述(供Agent理解)';
COMMENT ON COLUMN t_tool.tool_type IS '类型: builtin/custom/api/mcp';
COMMENT ON COLUMN t_tool.input_schema IS '输入参数Schema';
COMMENT ON COLUMN t_tool.output_schema IS '输出参数Schema';
COMMENT ON COLUMN t_tool.config IS '扩展配置';
COMMENT ON COLUMN t_tool.endpoint_url IS 'API端点地址';
COMMENT ON COLUMN t_tool.auth_type IS '认证类型: none/api_key/oauth/bearer';
COMMENT ON COLUMN t_tool.auth_config IS '认证配置';
COMMENT ON COLUMN t_tool.status IS '状态: active/disabled';
```

**字段说明**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | BIGINT | 是 | 雪花算法生成 |
| name | VARCHAR(64) | 是 | 唯一标识，如 "http_request" |
| tool_type | VARCHAR(32) | 是 | builtin-内置, custom-自定义, api-API调用, mcp-MCP协议 |
| input_schema | JSONB | 是 | JSON Schema 格式定义输入参数 |
| auth_type | VARCHAR(32) | 否 | 认证方式 |
| auth_config | JSONB | 否 | 认证配置，如 API Key |

**input_schema 示例**：

```json
{
    "type": "object",
    "properties": {
        "url": {
            "type": "string",
            "description": "请求URL"
        },
        "method": {
            "type": "string",
            "enum": ["GET", "POST", "PUT", "DELETE"],
            "description": "请求方法"
        },
        "headers": {
            "type": "object",
            "description": "请求头"
        },
        "body": {
            "type": "object",
            "description": "请求体"
        }
    },
    "required": ["url", "method"]
}
```

---

### 5. skill - Skill 表

```sql
CREATE TABLE t_skill (
    id              BIGINT          NOT NULL,
    agent_id        BIGINT          NOT NULL,
    tool_id         BIGINT,
    name            VARCHAR(128)    NOT NULL,
    description     TEXT,
    prompt_template TEXT,
    config          JSONB           DEFAULT '{}',
    sort_order      INT             NOT NULL DEFAULT 0,
    status          VARCHAR(20)     NOT NULL DEFAULT 'active',
    create_time     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE INDEX idx_skill_agent_id ON t_skill (agent_id);
CREATE INDEX idx_skill_tool_id ON t_skill (tool_id);

COMMENT ON TABLE t_skill IS 'Skill表(Agent技能)';
COMMENT ON COLUMN t_skill.id IS '主键ID';
COMMENT ON COLUMN t_skill.agent_id IS '所属AgentID';
COMMENT ON COLUMN t_skill.tool_id IS '关联ToolID';
COMMENT ON COLUMN t_skill.name IS '技能名称';
COMMENT ON COLUMN t_skill.description IS '技能描述';
COMMENT ON COLUMN t_skill.prompt_template IS '技能提示词模板';
COMMENT ON COLUMN t_skill.config IS '扩展配置';
COMMENT ON COLUMN t_skill.sort_order IS '排序序号';
COMMENT ON COLUMN t_skill.status IS '状态: active/disabled';
```

**字段说明**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | BIGINT | 是 | 雪花算法生成 |
| agent_id | BIGINT | 是 | 所属 Agent |
| tool_id | BIGINT | 否 | 关联 Tool，可为空表示纯 Prompt 技能 |
| prompt_template | TEXT | 否 | 技能提示词模板 |
| config | JSONB | 否 | 技能配置 |

**config 示例**：

```json
{
    "trigger_keywords": ["搜索", "查找"],
    "auto_trigger": false,
    "priority": 10,
    "params": {
        "max_results": 5
    }
}
```

---

### 6. knowledge - 知识库表

```sql
CREATE TABLE t_knowledge (
    id              BIGINT          NOT NULL,
    user_id         BIGINT          NOT NULL,
    name            VARCHAR(128)    NOT NULL,
    description     TEXT,
    embedding_model VARCHAR(64)     NOT NULL DEFAULT 'text-embedding-3-small',
    chunk_size      INT             NOT NULL DEFAULT 512,
    chunk_overlap   INT             NOT NULL DEFAULT 50,
    config          JSONB           DEFAULT '{}',
    document_count  INT             NOT NULL DEFAULT 0,
    chunk_count     INT             NOT NULL DEFAULT 0,
    total_tokens    BIGINT          NOT NULL DEFAULT 0,
    status          VARCHAR(20)     NOT NULL DEFAULT 'active',
    create_time     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE INDEX idx_knowledge_user_id ON t_knowledge (user_id);
CREATE INDEX idx_knowledge_status ON t_knowledge (status);

COMMENT ON TABLE t_knowledge IS '知识库表';
COMMENT ON COLUMN t_knowledge.id IS '主键ID';
COMMENT ON COLUMN t_knowledge.user_id IS '创建者ID';
COMMENT ON COLUMN t_knowledge.name IS '知识库名称';
COMMENT ON COLUMN t_knowledge.description IS '知识库描述';
COMMENT ON COLUMN t_knowledge.embedding_model IS '向量化模型';
COMMENT ON COLUMN t_knowledge.chunk_size IS '分块大小(Token)';
COMMENT ON COLUMN t_knowledge.chunk_overlap IS '分块重叠(Token)';
COMMENT ON COLUMN t_knowledge.config IS '扩展配置';
COMMENT ON COLUMN t_knowledge.document_count IS '文档数量';
COMMENT ON COLUMN t_knowledge.chunk_count IS '分块数量';
COMMENT ON COLUMN t_knowledge.total_tokens IS '总Token数';
COMMENT ON COLUMN t_knowledge.status IS '状态: active/disabled';
```

**字段说明**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | BIGINT | 是 | 雪花算法生成 |
| user_id | BIGINT | 是 | 创建者 |
| embedding_model | VARCHAR(64) | 是 | 向量化模型名称 |
| chunk_size | INT | 是 | 分块大小，默认 512 Token |
| chunk_overlap | INT | 是 | 分块重叠，默认 50 Token |
| document_count | INT | 是 | 文档总数，冗余字段 |
| chunk_count | INT | 是 | 分块总数，冗余字段 |

**config 示例**：

```json
{
    "retrieval_mode": "hybrid",
    "top_k": 5,
    "score_threshold": 0.7,
    "rerank_enabled": false,
    "metadata_fields": ["source", "page"]
}
```

---

### 7. document - 文档表

```sql
CREATE TABLE t_document (
    id              BIGINT          NOT NULL,
    knowledge_id    BIGINT          NOT NULL,
    user_id         BIGINT          NOT NULL,
    name            VARCHAR(256)    NOT NULL,
    file_path       VARCHAR(512),
    file_type       VARCHAR(32),
    file_size       BIGINT,
    file_hash       VARCHAR(64),
    chunk_count     INT             NOT NULL DEFAULT 0,
    token_count     BIGINT          NOT NULL DEFAULT 0,
    status          VARCHAR(20)     NOT NULL DEFAULT 'pending',
    error_message   TEXT,
    metadata        JSONB           DEFAULT '{}',
    create_time     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE INDEX idx_document_knowledge_id ON t_document (knowledge_id);
CREATE INDEX idx_document_user_id ON t_document (user_id);
CREATE INDEX idx_document_status ON t_document (status);
CREATE INDEX idx_document_file_hash ON t_document (file_hash);

COMMENT ON TABLE t_document IS '文档表';
COMMENT ON COLUMN t_document.id IS '主键ID';
COMMENT ON COLUMN t_document.knowledge_id IS '所属知识库ID';
COMMENT ON COLUMN t_document.user_id IS '上传者ID';
COMMENT ON COLUMN t_document.name IS '文档名称';
COMMENT ON COLUMN t_document.file_path IS '文件存储路径';
COMMENT ON COLUMN t_document.file_type IS '文件类型: pdf/txt/md/docx';
COMMENT ON COLUMN t_document.file_size IS '文件大小(字节)';
COMMENT ON COLUMN t_document.file_hash IS '文件哈希(去重用)';
COMMENT ON COLUMN t_document.chunk_count IS '分块数量';
COMMENT ON COLUMN t_document.token_count IS 'Token数量';
COMMENT ON COLUMN t_document.status IS '状态: pending/processing/completed/failed';
COMMENT ON COLUMN t_document.error_message IS '处理错误信息';
COMMENT ON COLUMN t_document.metadata IS '文档元数据';
```

**字段说明**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | BIGINT | 是 | 雪花算法生成 |
| knowledge_id | BIGINT | 是 | 所属知识库 |
| file_hash | VARCHAR(64) | 否 | 文件 MD5，用于去重 |
| status | VARCHAR(20) | 是 | pending-待处理, processing-处理中, completed-完成, failed-失败 |
| metadata | JSONB | 否 | 文档元数据，如页数、作者 |

**status 流转**：

```text
pending → processing → completed
                    → failed
```

---

### 8. chunk - 文档分块表

```sql
CREATE TABLE t_chunk (
    id              BIGINT          NOT NULL,
    document_id     BIGINT          NOT NULL,
    knowledge_id    BIGINT          NOT NULL,
    content         TEXT            NOT NULL,
    chunk_index     INT             NOT NULL,
    token_count     INT             NOT NULL DEFAULT 0,
    metadata        JSONB           DEFAULT '{}',
    create_time     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE INDEX idx_chunk_document_id ON t_chunk (document_id);
CREATE INDEX idx_chunk_knowledge_id ON t_chunk (knowledge_id);

COMMENT ON TABLE t_chunk IS '文档分块表';
COMMENT ON COLUMN t_chunk.id IS '主键ID';
COMMENT ON COLUMN t_chunk.document_id IS '所属文档ID';
COMMENT ON COLUMN t_chunk.knowledge_id IS '所属知识库ID(冗余)';
COMMENT ON COLUMN t_chunk.content IS '分块内容';
COMMENT ON COLUMN t_chunk.chunk_index IS '分块序号';
COMMENT ON COLUMN t_chunk.token_count IS 'Token数量';
COMMENT ON COLUMN t_chunk.metadata IS '分块元数据';
```

**字段说明**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | BIGINT | 是 | 雪花算法生成 |
| document_id | BIGINT | 是 | 所属文档 |
| knowledge_id | BIGINT | 是 | 所属知识库，冗余字段便于查询 |
| content | TEXT | 是 | 分块文本内容 |
| chunk_index | INT | 是 | 在文档中的序号 |
| metadata | JSONB | 否 | 如页码、章节标题 |

**metadata 示例**：

```json
{
    "page": 5,
    "section": "第三章 API设计",
    "start_char": 1024,
    "end_char": 2048
}
```

---

### 9. embedding - 向量表

```sql
-- 启用 pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE t_embedding (
    id              BIGINT          NOT NULL,
    chunk_id        BIGINT          NOT NULL,
    model_name      VARCHAR(64)     NOT NULL,
    dimension       INT             NOT NULL DEFAULT 1536,
    vector          vector(1536)    NOT NULL,
    create_time     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_embedding_chunk_id ON t_embedding (chunk_id);

-- HNSW 索引 (推荐用于高维向量)
CREATE INDEX idx_embedding_vector ON t_embedding
    USING hnsw (vector vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

COMMENT ON TABLE t_embedding IS '向量表';
COMMENT ON COLUMN t_embedding.id IS '主键ID';
COMMENT ON COLUMN t_embedding.chunk_id IS '关联分块ID';
COMMENT ON COLUMN t_embedding.model_name IS '向量化模型名称';
COMMENT ON COLUMN t_embedding.dimension IS '向量维度';
COMMENT ON COLUMN t_embedding.vector IS '向量数据';
COMMENT ON COLUMN t_embedding.create_time IS '创建时间';
```

**字段说明**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | BIGINT | 是 | 雪花算法生成 |
| chunk_id | BIGINT | 是 | 一对一关联 chunk |
| model_name | VARCHAR(64) | 是 | 生成向量的模型 |
| dimension | INT | 是 | 向量维度，如 1536 |
| vector | vector(1536) | 是 | pgvector 向量类型 |

**向量检索示例**：

```sql
-- 余弦相似度检索 Top 5
SELECT
    c.id AS chunk_id,
    c.content,
    d.name AS document_name,
    1 - (e.vector <=> $1::vector) AS score
FROM t_embedding e
JOIN t_chunk c ON e.chunk_id = c.id
JOIN t_document d ON c.document_id = d.id
WHERE c.knowledge_id = $2
ORDER BY e.vector <=> $1::vector
LIMIT 5;
```

---

### 10. chat_session - 对话会话表

```sql
CREATE TABLE t_chat_session (
    id              BIGINT          NOT NULL,
    agent_id        BIGINT          NOT NULL,
    user_id         BIGINT          NOT NULL,
    title           VARCHAR(256),
    status          VARCHAR(20)     NOT NULL DEFAULT 'active',
    context         JSONB           DEFAULT '{}',
    message_count   INT             NOT NULL DEFAULT 0,
    total_tokens    BIGINT          NOT NULL DEFAULT 0,
    last_message_at TIMESTAMP,
    create_time     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE INDEX idx_chat_session_agent_id ON t_chat_session (agent_id);
CREATE INDEX idx_chat_session_user_id ON t_chat_session (user_id);
CREATE INDEX idx_chat_session_status ON t_chat_session (status);
CREATE INDEX idx_chat_session_last_message ON t_chat_session (last_message_at DESC);

COMMENT ON TABLE t_chat_session IS '对话会话表';
COMMENT ON COLUMN t_chat_session.id IS '主键ID';
COMMENT ON COLUMN t_chat_session.agent_id IS 'AgentID';
COMMENT ON COLUMN t_chat_session.user_id IS '用户ID';
COMMENT ON COLUMN t_chat_session.title IS '会话标题';
COMMENT ON COLUMN t_chat_session.status IS '状态: active/archived';
COMMENT ON COLUMN t_chat_session.context IS '会话上下文';
COMMENT ON COLUMN t_chat_session.message_count IS '消息数量';
COMMENT ON COLUMN t_chat_session.total_tokens IS '总Token消耗';
COMMENT ON COLUMN t_chat_session.last_message_at IS '最后消息时间';
```

**字段说明**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | BIGINT | 是 | 雪花算法生成 |
| agent_id | BIGINT | 是 | 关联 Agent |
| user_id | BIGINT | 是 | 所属用户 |
| context | JSONB | 否 | 会话级变量 |
| message_count | INT | 是 | 冗余字段，快速查询 |
| total_tokens | BIGINT | 是 | Token 消耗统计 |

**context 示例**：

```json
{
    "variables": {
        "user_name": "张三",
        "department": "技术部"
    },
    "memory_window": 20,
    "temperature_override": 0.8
}
```

---

### 11. message - 消息表

```sql
CREATE TABLE t_message (
    id              BIGINT          NOT NULL,
    session_id      BIGINT          NOT NULL,
    role            VARCHAR(20)     NOT NULL,
    content         TEXT,
    content_type    VARCHAR(20)     NOT NULL DEFAULT 'text',
    tool_calls      JSONB           DEFAULT '[]',
    tool_call_id    VARCHAR(128),
    token_count     INT             NOT NULL DEFAULT 0,
    metadata        JSONB           DEFAULT '{}',
    parent_id       BIGINT,
    create_time     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE INDEX idx_message_session_id ON t_message (session_id);
CREATE INDEX idx_message_create_time ON t_message (session_id, create_time);
CREATE INDEX idx_message_role ON t_message (session_id, role);

COMMENT ON TABLE t_message IS '消息表';
COMMENT ON COLUMN t_message.id IS '主键ID';
COMMENT ON COLUMN t_message.session_id IS '所属会话ID';
COMMENT ON COLUMN t_message.role IS '角色: user/assistant/system/tool';
COMMENT ON COLUMN t_message.content IS '消息内容';
COMMENT ON COLUMN t_message.content_type IS '内容类型: text/image/file';
COMMENT ON COLUMN t_message.tool_calls IS '工具调用列表';
COMMENT ON COLUMN t_message.tool_call_id IS '工具调用ID(用于tool角色)';
COMMENT ON COLUMN t_message.token_count IS 'Token数量';
COMMENT ON COLUMN t_message.metadata IS '元数据';
COMMENT ON COLUMN t_message.parent_id IS '父消息ID(用于分支对话)';
```

**字段说明**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | BIGINT | 是 | 雪花算法生成 |
| session_id | BIGINT | 是 | 所属会话 |
| role | VARCHAR(20) | 是 | user/assistant/system/tool |
| content | TEXT | 否 | 消息内容，tool 角色可为空 |
| tool_calls | JSONB | 否 | assistant 消息中的工具调用 |
| tool_call_id | VARCHAR(128) | 否 | tool 消息对应的调用 ID |
| parent_id | BIGINT | 否 | 支持分支对话 |

**tool_calls 示例**：

```json
[
    {
        "id": "call_abc123",
        "type": "function",
        "function": {
            "name": "http_request",
            "arguments": "{\"url\": \"https://api.example.com\", \"method\": \"GET\"}"
        }
    }
]
```

**metadata 示例**：

```json
{
    "model": "gpt-4",
    "prompt_tokens": 100,
    "completion_tokens": 50,
    "total_tokens": 150,
    "latency_ms": 1200,
    "finish_reason": "stop"
}
```

---

## 索引设计原则

### 主键索引

```text
所有表使用 BIGINT 主键，雪花算法生成
优势：分布式友好、趋势递增、无锁竞争
```

### 唯一索引

```text
uk_user_username      - 用户名唯一
uk_user_email         - 邮箱唯一
uk_tool_name          - Tool名称唯一
uk_embedding_chunk_id - Chunk与Embedding一对一
uk_workflow_node_key  - 同一Workflow内节点标识唯一
```

### 条件唯一索引

```sql
-- 支持软删除的唯一约束
CREATE UNIQUE INDEX uk_user_username ON t_user (username) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_tool_name ON t_tool (name) WHERE deleted = 0;
```

### 普通索引

```text
外键索引：所有 *_id 字段
状态索引：所有 status 字段
时间索引：create_time, last_message_at
哈希索引：file_hash (文档去重)
```

### 向量索引

```sql
-- HNSW 索引，适合高维向量近似检索
CREATE INDEX idx_embedding_vector ON t_embedding
    USING hnsw (vector vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- 参数说明：
-- m: 每个节点的最大连接数，默认16
-- ef_construction: 构建时的候选集大小，默认64
-- vector_cosine_ops: 余弦距离操作符
```

---

## 向量设计规范

### 维度选择

| 模型 | 维度 | 适用场景 |
|------|------|----------|
| text-embedding-3-small | 1536 | 默认，性价比高 |
| text-embedding-3-large | 3072 | 高精度需求 |
| text-embedding-ada-002 | 1536 | 兼容旧模型 |

### 存储策略

```text
1. 向量与文本分离存储：chunk 存文本，embedding 存向量
2. 支持多模型：不同 embedding 模型可共存
3. 维度固定：表结构定义时确定维度，变更需重建
```

### 检索策略

```sql
-- 1. 余弦相似度（默认）
SELECT * FROM t_embedding ORDER BY vector <=> $1::vector LIMIT 5;

-- 2. L2 距离
SELECT * FROM t_embedding ORDER BY vector <-> $1::vector LIMIT 5;

-- 3. 内积（需归一化）
SELECT * FROM t_embedding ORDER BY vector <#> $1::vector LIMIT 5;
```

### 性能优化

```text
1. HNSW 索引：内存充足时优先使用，查询速度快
2. IVFFlat 索引：数据量大时使用，内存友好
3. 批量插入：向量数据批量插入，减少索引重建次数
4. 分区表：按 knowledge_id 分区，适合超大规模数据
```

---

## 附录：建表脚本

完整建表脚本见 `lightbot-deploy/scripts/init-db.sql`

### Flyway 迁移文件

```
lightbot-server/src/main/resources/db/migration/
├── V0.1__init_user.sql
├── V0.2__init_model_provider.sql
├── V0.3__init_agent.sql
├── V0.4__init_workflow.sql
├── V0.5__init_tool.sql
├── V0.6__init_knowledge.sql
├── V0.7__init_chat.sql
└── V0.8__init_embedding.sql
```

### 扩展建议

| 阶段 | 优化方向 |
|------|----------|
| 初期 | 单表，基础索引 |
| 中期 | 读写分离，缓存热数据 |
| 后期 | 分区表，向量检索专用节点 |
