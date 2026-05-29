-- ============================================================
-- LightBot Database Initialization (Complete)
-- PostgreSQL 15 + pgvector
-- 合并日期：2026-05-29
-- 包含所有表的最终状态，无需执行增量迁移
-- ============================================================

CREATE DATABASE lightbot ENCODING 'UTF8';
\c lightbot;

CREATE EXTENSION IF NOT EXISTS vector;

-- ========================================
-- 用户表（user 为PG保留字，使用 users）
-- ========================================
CREATE TABLE users (
    id              BIGINT          NOT NULL,
    username        VARCHAR(64)     NOT NULL,
    email           VARCHAR(128)    DEFAULT '',
    password        VARCHAR(256)    NOT NULL,
    nickname        VARCHAR(64),
    avatar          VARCHAR(512),
    phone           VARCHAR(20),
    role            VARCHAR(20)     NOT NULL DEFAULT 'user',
    status          VARCHAR(20)     NOT NULL DEFAULT 'active',
    last_login_at   TIMESTAMP,
    config          JSONB           DEFAULT '{}',
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_user_username ON users (username) WHERE deleted = 0;
CREATE INDEX idx_user_status ON users (status);
CREATE INDEX idx_user_create_time ON users (create_time);
COMMENT ON TABLE users IS '用户表';

-- ========================================
-- 模型提供商表
-- ========================================
CREATE TABLE model_provider (
    id              BIGINT          NOT NULL,
    name            VARCHAR(64)     NOT NULL,
    type            VARCHAR(32)     NOT NULL,
    api_key         VARCHAR(512),
    base_url        VARCHAR(256),
    config          JSONB           DEFAULT '{}',
    status          VARCHAR(20)     NOT NULL DEFAULT 'active',
    models_endpoint VARCHAR(512),
    headers_json    JSONB           DEFAULT '{}',
    extra_json      JSONB           DEFAULT '{}',
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_model_provider_type ON model_provider (type);
CREATE INDEX idx_model_provider_status ON model_provider (status);
COMMENT ON TABLE model_provider IS '模型提供商表';
COMMENT ON COLUMN model_provider.models_endpoint IS '模型列表获取地址（为空时使用默认地址）';
COMMENT ON COLUMN model_provider.headers_json IS '额外请求头（JSON格式）';
COMMENT ON COLUMN model_provider.extra_json IS '扩展配置（JSON格式）';

-- ========================================
-- 模型表
-- ========================================
CREATE TABLE model (
    id              BIGINT          NOT NULL,
    provider_id     BIGINT          NOT NULL,
    model_id        VARCHAR(128)    NOT NULL,
    name            VARCHAR(128)    NOT NULL,
    type            VARCHAR(20)     NOT NULL DEFAULT 'llm',
    status          VARCHAR(20)     NOT NULL DEFAULT 'active',
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_model_provider_id ON model (provider_id);
CREATE INDEX idx_model_type ON model (type);
COMMENT ON TABLE model IS '模型表';

-- ========================================
-- Agent 表
-- ========================================
CREATE TABLE agent (
    id              BIGINT          NOT NULL,
    user_id         BIGINT          NOT NULL,
    name            VARCHAR(128)    NOT NULL,
    description     TEXT,
    system_prompt   TEXT,
    avatar          VARCHAR(512),
    icon            VARCHAR(32),
    agent_type      VARCHAR(32)     NOT NULL DEFAULT 'chat',
    config          JSONB           DEFAULT '{}',
    status          VARCHAR(20)     NOT NULL DEFAULT 'draft',
    publish_time    TIMESTAMP,
    version         INT             NOT NULL DEFAULT 1,
    welcome_message TEXT,
    recommended_questions JSONB,
    is_default      BOOLEAN         NOT NULL DEFAULT FALSE,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_agent_user_id ON agent (user_id);
CREATE INDEX idx_agent_status ON agent (status);
CREATE INDEX idx_agent_create_time ON agent (create_time);
COMMENT ON TABLE agent IS 'Agent表';
COMMENT ON COLUMN agent.icon IS 'Agent图标（emoji或图标标识）';
COMMENT ON COLUMN agent.welcome_message IS '欢迎语';
COMMENT ON COLUMN agent.recommended_questions IS '推荐问题列表';

-- ========================================
-- Agent 版本配置表
-- ========================================
CREATE TABLE agent_version (
    id              BIGINT          NOT NULL,
    agent_id        BIGINT          NOT NULL,
    user_id         BIGINT          NOT NULL,
    version         INT             NOT NULL DEFAULT 0,
    status          VARCHAR(32)     NOT NULL DEFAULT 'draft',
    config          JSONB           NOT NULL DEFAULT '{}',
    node_count      INT             NOT NULL DEFAULT 0,
    edge_count      INT             NOT NULL DEFAULT 0,
    description     VARCHAR(512),
    publish_time    TIMESTAMP,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_agent_version_agent_id ON agent_version (agent_id);
CREATE INDEX idx_agent_version_agent_status ON agent_version (agent_id, status);
CREATE UNIQUE INDEX uk_agent_version_agent_pub ON agent_version (agent_id, version)
    WHERE status = 'published' AND deleted = 0;
COMMENT ON TABLE agent_version IS 'Agent版本配置表（草稿与发布历史）';
COMMENT ON COLUMN agent_version.version IS '发布版本号，草稿行为0';
COMMENT ON COLUMN agent_version.status IS 'draft=当前草稿 published=已发布历史版本';
COMMENT ON COLUMN agent_version.config IS '版本快照JSON（workflow图或对话配置）';

-- ========================================
-- SubAgent 表
-- ========================================
CREATE TABLE subagent (
    id              BIGINT          NOT NULL,
    name            VARCHAR(128)    NOT NULL UNIQUE,
    display_name    VARCHAR(128)    NOT NULL,
    description     TEXT            NOT NULL,
    system_prompt   TEXT            NOT NULL,
    tools           JSONB           NOT NULL DEFAULT '[]',
    model_id        BIGINT,
    enabled         SMALLINT        NOT NULL DEFAULT 1,
    is_builtin      SMALLINT        NOT NULL DEFAULT 0,
    user_id         BIGINT,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_subagent_name ON subagent (name);
CREATE INDEX idx_subagent_enabled ON subagent (enabled);
CREATE INDEX idx_subagent_is_builtin ON subagent (is_builtin);
COMMENT ON TABLE subagent IS '子智能体配置表';
COMMENT ON COLUMN subagent.name IS '唯一标识（英文）';
COMMENT ON COLUMN subagent.display_name IS '显示名称（中文）';
COMMENT ON COLUMN subagent.description IS '子智能体描述';
COMMENT ON COLUMN subagent.system_prompt IS '系统提示词';
COMMENT ON COLUMN subagent.tools IS '工具名称列表（JSON数组）';
COMMENT ON COLUMN subagent.model_id IS '可选的模型覆盖';
COMMENT ON COLUMN subagent.enabled IS '是否启用';
COMMENT ON COLUMN subagent.is_builtin IS '是否内置';
COMMENT ON COLUMN subagent.user_id IS '创建者ID';

-- ========================================
-- 对话会话表
-- ========================================
CREATE TABLE chat_session (
    id              BIGINT          NOT NULL,
    agent_id        BIGINT,
    user_id         BIGINT          NOT NULL,
    title           VARCHAR(256),
    status          VARCHAR(20)     NOT NULL DEFAULT 'active',
    context         JSONB           DEFAULT '{}',
    message_count   INT             NOT NULL DEFAULT 0,
    total_tokens    BIGINT          NOT NULL DEFAULT 0,
    last_message_at TIMESTAMP,
    pinned          BOOLEAN         NOT NULL DEFAULT FALSE,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_chat_session_agent_id ON chat_session (agent_id);
CREATE INDEX idx_chat_session_user_id ON chat_session (user_id);
CREATE INDEX idx_chat_session_status ON chat_session (status);
CREATE INDEX idx_chat_session_last_message ON chat_session (last_message_at DESC);
CREATE INDEX idx_chat_session_pinned ON chat_session (user_id, pinned DESC, last_message_at DESC);
COMMENT ON TABLE chat_session IS '对话会话表';

-- ========================================
-- 消息表
-- ========================================
CREATE TABLE message (
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
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX idx_message_session_id ON message (session_id);
CREATE INDEX idx_message_create_time ON message (session_id, create_time);
CREATE INDEX idx_message_role ON message (session_id, role);
COMMENT ON TABLE message IS '消息表';

-- ========================================
-- 知识库表
-- ========================================
CREATE TABLE knowledge (
    id              BIGINT          NOT NULL,
    user_id         BIGINT          NOT NULL,
    name            VARCHAR(128)    NOT NULL,
    description     TEXT,
    embedding_model VARCHAR(64)     NOT NULL DEFAULT 'text-embedding-3-small',
    config          JSONB           DEFAULT '{}',
    document_count  INT             NOT NULL DEFAULT 0,
    chunk_count     INT             NOT NULL DEFAULT 0,
    total_tokens    BIGINT          NOT NULL DEFAULT 0,
    status          VARCHAR(20)     NOT NULL DEFAULT 'active',
    mindmap_data    JSONB,
    example_questions JSONB         DEFAULT '[]',
    graph_enabled   BOOLEAN         NOT NULL DEFAULT FALSE,
    node_count      INTEGER         NOT NULL DEFAULT 0,
    edge_count      INTEGER         NOT NULL DEFAULT 0,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_knowledge_user_id ON knowledge (user_id);
CREATE INDEX idx_knowledge_status ON knowledge (status);
COMMENT ON TABLE knowledge IS '知识库表';
COMMENT ON COLUMN knowledge.mindmap_data IS '思维导图数据（JSON格式树状结构）';
COMMENT ON COLUMN knowledge.example_questions IS '示例问题列表（JSON数组）';
COMMENT ON COLUMN knowledge.graph_enabled IS '是否启用知识图谱';
COMMENT ON COLUMN knowledge.node_count IS '图谱节点数';
COMMENT ON COLUMN knowledge.edge_count IS '图谱边数';

-- ========================================
-- 知识库成员表（权限控制）
-- ========================================
CREATE TABLE knowledge_member (
    id              BIGINT          NOT NULL,
    knowledge_id    BIGINT          NOT NULL,
    user_id         BIGINT          NOT NULL,
    role            VARCHAR(20)     NOT NULL DEFAULT 'viewer',
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_knowledge_member ON knowledge_member (knowledge_id, user_id);
CREATE INDEX idx_knowledge_member_user_id ON knowledge_member (user_id);
COMMENT ON TABLE knowledge_member IS '知识库成员表';

-- ========================================
-- 文档表
-- ========================================
CREATE TABLE document (
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
    status          VARCHAR(20)     NOT NULL DEFAULT 'uploaded',
    error_message   TEXT,
    metadata        JSONB           DEFAULT '{}',
    markdown_path   VARCHAR(512),
    embedding_json  JSONB,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_document_knowledge_id ON document (knowledge_id);
CREATE INDEX idx_document_user_id ON document (user_id);
CREATE INDEX idx_document_status ON document (status);
CREATE INDEX idx_document_file_hash ON document (file_hash);
COMMENT ON TABLE document IS '文档表';
COMMENT ON COLUMN document.markdown_path IS 'Markdown文件存储路径';
COMMENT ON COLUMN document.embedding_json IS '入库配置（chunkStrategy/chunkSize/chunkOverlap/chunkDelimiter）';

-- ========================================
-- 文档分块表
-- ========================================
CREATE TABLE chunk (
    id              BIGINT          NOT NULL,
    document_id     BIGINT          NOT NULL,
    knowledge_id    BIGINT          NOT NULL,
    content         TEXT            NOT NULL,
    chunk_index     INT             NOT NULL,
    token_count     INT             NOT NULL DEFAULT 0,
    metadata        JSONB           DEFAULT '{}',
    status          VARCHAR(20)     NOT NULL DEFAULT 'chunked',
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX idx_chunk_document_id ON chunk (document_id);
CREATE INDEX idx_chunk_knowledge_id ON chunk (knowledge_id);
CREATE INDEX idx_chunk_status ON chunk (status);
COMMENT ON TABLE chunk IS '文档分块表';
COMMENT ON COLUMN chunk.status IS '向量化状态: chunked/vectorizing/vectorized/failed';

-- ========================================
-- 向量表
-- ========================================
CREATE TABLE embedding (
    id              BIGINT          NOT NULL,
    chunk_id        BIGINT,
    qa_pair_id      BIGINT,
    model_name      VARCHAR(64)     NOT NULL,
    dimension       INT             NOT NULL DEFAULT 1536,
    vector          vector(1536)    NOT NULL,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_embedding_chunk_id ON embedding (chunk_id) WHERE chunk_id IS NOT NULL;
CREATE UNIQUE INDEX uk_embedding_qa_pair_id ON embedding (qa_pair_id) WHERE qa_pair_id IS NOT NULL;
CREATE INDEX idx_embedding_vector ON embedding
    USING hnsw (vector vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
CREATE INDEX idx_embedding_vector_hnsw ON embedding
    USING hnsw (vector vector_l2_ops)
    WITH (m = 16, ef_construction = 200);
COMMENT ON TABLE embedding IS '向量表';
COMMENT ON COLUMN embedding.qa_pair_id IS '关联问答对ID，与chunk_id互斥';

-- ========================================
-- 问答对表
-- ========================================
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

-- ========================================
-- 图谱抽取任务表
-- ========================================
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
COMMENT ON COLUMN graph_extraction_task.status IS '状态：pending-待处理、running-执行中、completed-已完成、failed-失败';

-- ========================================
-- Tool 表
-- ========================================
CREATE TABLE tool (
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
    is_system       BOOLEAN         NOT NULL DEFAULT FALSE,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_tool_name ON tool (name) WHERE deleted = 0;
CREATE INDEX idx_tool_type ON tool (tool_type);
CREATE INDEX idx_tool_status ON tool (status);
CREATE INDEX idx_tool_is_system ON tool (is_system);
COMMENT ON TABLE tool IS 'Tool表';
COMMENT ON COLUMN tool.is_system IS '是否系统工具：true=系统工具（自动注入，不可编辑删除），false=普通工具';

-- ========================================
-- Skill 表
-- ========================================
CREATE TABLE skill (
    id              BIGINT          NOT NULL,
    agent_id        BIGINT,
    tool_id         BIGINT,
    name            VARCHAR(128)    NOT NULL,
    description     TEXT,
    prompt_template TEXT,
    config          JSONB           DEFAULT '{}',
    sort_order      INT             NOT NULL DEFAULT 0,
    status          VARCHAR(20)     NOT NULL DEFAULT 'active',
    slug            VARCHAR(128),
    display_name    VARCHAR(128),
    tool_ids        JSONB           NOT NULL DEFAULT '[]',
    mcp_server_ids  JSONB           NOT NULL DEFAULT '[]',
    model_id        BIGINT,
    scope           VARCHAR(20)     NOT NULL DEFAULT 'global',
    is_builtin      SMALLINT        NOT NULL DEFAULT 0,
    content_hash    VARCHAR(128),
    user_id         BIGINT,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_skill_agent_id ON skill (agent_id);
CREATE INDEX idx_skill_tool_id ON skill (tool_id);
CREATE UNIQUE INDEX uk_skill_slug ON skill (slug) WHERE slug IS NOT NULL AND deleted = 0;
CREATE INDEX idx_skill_is_builtin ON skill (is_builtin);
CREATE INDEX idx_skill_scope ON skill (scope);
COMMENT ON TABLE skill IS 'Skill表';
COMMENT ON COLUMN skill.slug IS '全局唯一标识（英文-小写-短横线），全局 Skill 必填';
COMMENT ON COLUMN skill.display_name IS '显示名称（中文）';
COMMENT ON COLUMN skill.tool_ids IS '依赖的 Tool ID 列表（JSON 数组，字符串形式）';
COMMENT ON COLUMN skill.mcp_server_ids IS '依赖的 MCP Server ID 列表（JSON 数组，字符串形式）';
COMMENT ON COLUMN skill.model_id IS '可选的模型覆盖（保留字段，当前未启用）';
COMMENT ON COLUMN skill.scope IS '作用域：global=全局可复用；agent=旧的按 Agent 私有（兼容）';
COMMENT ON COLUMN skill.is_builtin IS '是否内置：1=是（不可编辑/删除），0=否';
COMMENT ON COLUMN skill.content_hash IS '内置 Skill 内容 hash，用于检测代码版本变化';
COMMENT ON COLUMN skill.user_id IS '创建者ID（global skill 可为空）';

-- ========================================
-- 工具调用记录表
-- ========================================
CREATE TABLE tool_calls (
    id              BIGINT          NOT NULL,
    message_id      BIGINT,
    tool_call_id    VARCHAR(128),
    tool_name       VARCHAR(100)    NOT NULL,
    tool_input      JSONB,
    tool_output     TEXT,
    status          VARCHAR(20)     NOT NULL DEFAULT 'pending',
    error_message   TEXT,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX ix_tool_calls_message_id ON tool_calls (message_id);
CREATE INDEX ix_tool_calls_tool_call_id ON tool_calls (tool_call_id);
COMMENT ON TABLE tool_calls IS '工具调用记录表';
COMMENT ON COLUMN tool_calls.message_id IS '关联消息ID';
COMMENT ON COLUMN tool_calls.tool_call_id IS '工具调用ID（用于关联）';
COMMENT ON COLUMN tool_calls.tool_name IS '工具名称';
COMMENT ON COLUMN tool_calls.tool_input IS '工具输入参数';
COMMENT ON COLUMN tool_calls.tool_output IS '工具执行结果';
COMMENT ON COLUMN tool_calls.status IS '状态: pending/success/error';
COMMENT ON COLUMN tool_calls.error_message IS '错误信息';

-- ========================================
-- MCP Server 表
-- ========================================
CREATE TABLE mcp_server (
    id              BIGINT          NOT NULL,
    name            VARCHAR(128)    NOT NULL,
    description     VARCHAR(512),
    install_type    VARCHAR(20)     NOT NULL,
    deploy_config   JSONB,
    detail_config   JSONB,
    host            VARCHAR(256),
    status          VARCHAR(20)     NOT NULL DEFAULT 'active',
    user_id         BIGINT,
    transport       VARCHAR(20)     NOT NULL DEFAULT 'sse',
    headers         JSONB,
    disabled_tools  JSONB,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_mcp_server_user_id ON mcp_server (user_id);
COMMENT ON TABLE mcp_server IS 'MCP Server表';
COMMENT ON COLUMN mcp_server.transport IS '传输类型: sse, stdio, streamable_http';
COMMENT ON COLUMN mcp_server.headers IS 'HTTP请求头(JSONB)，用于SSE/Streamable HTTP认证';
COMMENT ON COLUMN mcp_server.disabled_tools IS '禁用的工具名列表(JSONB数组)';

-- ========================================
-- Prompt 定义表
-- ========================================
CREATE TABLE prompt (
    id              BIGINT          NOT NULL,
    prompt_key      VARCHAR(128)    NOT NULL,
    description     VARCHAR(512),
    latest_version  VARCHAR(32),
    tags            VARCHAR(512),
    user_id         BIGINT,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_prompt_key ON prompt (prompt_key) WHERE deleted = 0;
CREATE INDEX idx_prompt_user_id ON prompt (user_id);
COMMENT ON TABLE prompt IS 'Prompt定义表';

-- ========================================
-- Prompt 版本表
-- ========================================
CREATE TABLE prompt_version (
    id              BIGINT          NOT NULL,
    prompt_key      VARCHAR(128)    NOT NULL,
    version         VARCHAR(32)     NOT NULL,
    version_desc    VARCHAR(512),
    template        TEXT            NOT NULL,
    variables       JSONB           DEFAULT '{}',
    model_config    JSONB           DEFAULT '{}',
    tool_config     JSONB           DEFAULT '{}',
    status          VARCHAR(20)     NOT NULL DEFAULT 'pre',
    user_id         BIGINT,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_prompt_version ON prompt_version (prompt_key, version) WHERE deleted = 0;
CREATE INDEX idx_prompt_version_key ON prompt_version (prompt_key);
COMMENT ON TABLE prompt_version IS 'Prompt版本表';
COMMENT ON COLUMN prompt_version.tool_config IS '工具配置（JSON格式，存储Prompt关联的工具列表）';

-- ========================================
-- Prompt 构建模板表
-- ========================================
CREATE TABLE prompt_build_template (
    id                      BIGINT          NOT NULL,
    prompt_template_key     VARCHAR(128)    NOT NULL,
    tags                    VARCHAR(256),
    template_desc           VARCHAR(512),
    template                TEXT            NOT NULL,
    variables               VARCHAR(1024),
    model_config            JSONB           DEFAULT '{}',
    tool_config             JSONB           DEFAULT '{}',
    create_time             TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time             TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted                 SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_prompt_build_template_key ON prompt_build_template (prompt_template_key) WHERE deleted = 0;
COMMENT ON TABLE prompt_build_template IS 'Prompt构建模板表';
COMMENT ON COLUMN prompt_build_template.tool_config IS '工具配置（JSON格式）';

-- ========================================
-- 任务队列表
-- ========================================
CREATE TABLE task (
    id               BIGINT        NOT NULL,
    name             VARCHAR(256)  NOT NULL,
    type             VARCHAR(50)   NOT NULL,
    status           VARCHAR(20)   NOT NULL DEFAULT 'pending',
    progress         SMALLINT      NOT NULL DEFAULT 0,
    message          VARCHAR(512),
    payload          TEXT,
    result           TEXT,
    error            TEXT,
    cancel_requested SMALLINT      NOT NULL DEFAULT 0,
    user_id          BIGINT        NOT NULL,
    ref_id           BIGINT,
    create_time      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at       TIMESTAMP,
    completed_at     TIMESTAMP,
    deleted          SMALLINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_task_user_id ON task (user_id);
CREATE INDEX idx_task_status ON task (status);
CREATE INDEX idx_task_type ON task (type);
COMMENT ON TABLE task IS '任务队列表';

-- ========================================
-- LLM 调用链追踪表
-- ========================================
CREATE TABLE llm_trace (
    id              BIGINT          NOT NULL,
    request_id      VARCHAR(64)     NOT NULL,
    session_id      BIGINT,
    user_id         BIGINT,
    agent_id        BIGINT,
    agent_name      VARCHAR(128),
    model           VARCHAR(128),
    status          VARCHAR(20)     NOT NULL DEFAULT 'running',
    input_tokens    INT             NOT NULL DEFAULT 0,
    output_tokens   INT             NOT NULL DEFAULT 0,
    total_tokens    INT             NOT NULL DEFAULT 0,
    tool_call_count INT             NOT NULL DEFAULT 0,
    total_duration_ms BIGINT        NOT NULL DEFAULT 0,
    spans           JSONB           DEFAULT '[]',
    error_message   TEXT,
    reply_content   TEXT,
    trace_source    VARCHAR(32),
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX idx_llm_trace_request_id ON llm_trace (request_id);
CREATE INDEX idx_llm_trace_session_id ON llm_trace (session_id);
CREATE INDEX idx_llm_trace_user_id ON llm_trace (user_id);
CREATE INDEX idx_llm_trace_create_time ON llm_trace (create_time);
CREATE INDEX idx_llm_trace_status ON llm_trace (status);
CREATE INDEX idx_llm_trace_trace_source ON llm_trace (trace_source);
COMMENT ON TABLE llm_trace IS 'LLM调用链追踪表';
COMMENT ON COLUMN llm_trace.request_id IS '请求ID（唯一标识一次AI对话）';
COMMENT ON COLUMN llm_trace.session_id IS '会话ID';
COMMENT ON COLUMN llm_trace.user_id IS '用户ID';
COMMENT ON COLUMN llm_trace.agent_id IS 'AgentID';
COMMENT ON COLUMN llm_trace.agent_name IS 'Agent名称';
COMMENT ON COLUMN llm_trace.model IS '模型标识';
COMMENT ON COLUMN llm_trace.status IS '状态: running/completed/failed';
COMMENT ON COLUMN llm_trace.input_tokens IS '输入Token数';
COMMENT ON COLUMN llm_trace.output_tokens IS '输出Token数';
COMMENT ON COLUMN llm_trace.total_tokens IS '总Token数';
COMMENT ON COLUMN llm_trace.tool_call_count IS '工具调用次数';
COMMENT ON COLUMN llm_trace.total_duration_ms IS '总耗时（毫秒）';
COMMENT ON COLUMN llm_trace.spans IS '调用链Span列表（JSONB）';
COMMENT ON COLUMN llm_trace.error_message IS '错误信息';
COMMENT ON COLUMN llm_trace.reply_content IS 'AI完整回复内容';
COMMENT ON COLUMN llm_trace.trace_source IS '来源：chat=用户对话；辅助 LLM 调用不写入';

-- ========================================
-- 系统配置表
-- ========================================
CREATE TABLE system_config (
    config_key   VARCHAR(64)    NOT NULL,
    config_value TEXT,
    description  VARCHAR(255),
    create_time  TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (config_key)
);
COMMENT ON TABLE system_config IS '系统配置表';
COMMENT ON COLUMN system_config.config_key IS '配置键，如 default_ai_provider';
COMMENT ON COLUMN system_config.config_value IS '配置值，JSON格式';
COMMENT ON COLUMN system_config.description IS '配置描述';

-- ========================================
-- 评测集表
-- ========================================
CREATE TABLE eval_dataset (
    id              BIGINT          NOT NULL,
    name            VARCHAR(128)    NOT NULL,
    description     VARCHAR(512),
    columns_config  JSONB           DEFAULT '[]',
    user_id         BIGINT,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_eval_dataset_user_id ON eval_dataset (user_id);
COMMENT ON TABLE eval_dataset IS '评测集表';

-- ========================================
-- 评测集版本表
-- ========================================
CREATE TABLE eval_dataset_version (
    id              BIGINT          NOT NULL,
    dataset_id      BIGINT          NOT NULL,
    version         VARCHAR(32)     NOT NULL,
    data_count      INT             NOT NULL DEFAULT 0,
    status          VARCHAR(20)     NOT NULL DEFAULT 'draft',
    dataset_items   JSONB           DEFAULT '[]',
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_eval_dataset_version ON eval_dataset_version (dataset_id, version) WHERE deleted = 0;
CREATE INDEX idx_eval_dataset_version_dataset_id ON eval_dataset_version (dataset_id);
COMMENT ON TABLE eval_dataset_version IS '评测集版本表';

-- ========================================
-- 评测数据项表
-- ========================================
CREATE TABLE eval_dataset_item (
    id              BIGINT          NOT NULL,
    dataset_id      BIGINT          NOT NULL,
    data_content    JSONB           DEFAULT '{}',
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_eval_dataset_item_dataset_id ON eval_dataset_item (dataset_id);
COMMENT ON TABLE eval_dataset_item IS '评测数据项表';

-- ========================================
-- 评估器表
-- ========================================
CREATE TABLE eval_evaluator (
    id              BIGINT          NOT NULL,
    name            VARCHAR(128)    NOT NULL,
    description     VARCHAR(512),
    user_id         BIGINT,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_eval_evaluator_user_id ON eval_evaluator (user_id);
COMMENT ON TABLE eval_evaluator IS '评估器表';

-- ========================================
-- 评估器版本表
-- ========================================
CREATE TABLE eval_evaluator_version (
    id              BIGINT          NOT NULL,
    evaluator_id    BIGINT          NOT NULL,
    version         VARCHAR(32)     NOT NULL,
    model_config    JSONB           DEFAULT '{}',
    prompt          TEXT,
    variables       JSONB           DEFAULT '{}',
    status          VARCHAR(20)     NOT NULL DEFAULT 'draft',
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_eval_evaluator_version ON eval_evaluator_version (evaluator_id, version) WHERE deleted = 0;
CREATE INDEX idx_eval_evaluator_version_evaluator_id ON eval_evaluator_version (evaluator_id);
COMMENT ON TABLE eval_evaluator_version IS '评估器版本表';

-- ========================================
-- 评估器模板表
-- ========================================
CREATE TABLE eval_evaluator_template (
    id                      BIGINT          NOT NULL,
    evaluator_template_key  VARCHAR(128)    NOT NULL,
    template_desc           VARCHAR(512),
    template                TEXT            NOT NULL,
    variables               VARCHAR(1024),
    model_config            JSONB           DEFAULT '{}',
    create_time             TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time             TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted                 SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_eval_evaluator_template_key ON eval_evaluator_template (evaluator_template_key) WHERE deleted = 0;
COMMENT ON TABLE eval_evaluator_template IS '评估器模板表';

-- ========================================
-- 实验表
-- ========================================
CREATE TABLE eval_experiment (
    id                          BIGINT          NOT NULL,
    name                        VARCHAR(128)    NOT NULL,
    description                 VARCHAR(512),
    dataset_id                  BIGINT,
    dataset_version_id          BIGINT,
    dataset_version             VARCHAR(32),
    evaluation_object_config    JSONB           DEFAULT '{}',
    evaluator_config            JSONB           DEFAULT '[]',
    status                      VARCHAR(20)     NOT NULL DEFAULT 'draft',
    progress                    INT             NOT NULL DEFAULT 0,
    complete_time               TIMESTAMP,
    user_id                     BIGINT,
    task_id                     BIGINT,
    create_time                 TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time                 TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted                     SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_eval_experiment_user_id ON eval_experiment (user_id);
CREATE INDEX idx_eval_experiment_status ON eval_experiment (status);
COMMENT ON TABLE eval_experiment IS '评测实验表';

-- ========================================
-- 实验结果表
-- ========================================
CREATE TABLE eval_experiment_result (
    id                      BIGINT          NOT NULL,
    experiment_id           BIGINT          NOT NULL,
    input                   TEXT,
    actual_output           TEXT,
    reference_output        TEXT,
    score                   DECIMAL(3,2),
    reason                  TEXT,
    evaluator_version_id    BIGINT,
    evaluator_name          VARCHAR(128),
    evaluation_time         TIMESTAMP,
    create_time             TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time             TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted                 SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_eval_experiment_result_experiment_id ON eval_experiment_result (experiment_id);
CREATE INDEX idx_eval_experiment_result_evaluator ON eval_experiment_result (evaluator_version_id);
COMMENT ON TABLE eval_experiment_result IS '实验结果表';

-- ========================================
-- RAG 评估基准表
-- ========================================
CREATE TABLE eval_rag_benchmark (
    id              BIGINT        NOT NULL,
    knowledge_id    BIGINT        NOT NULL,
    name            VARCHAR(128)  NOT NULL,
    description     VARCHAR(512),
    question_count  INT           NOT NULL DEFAULT 0,
    status          VARCHAR(20)   NOT NULL DEFAULT 'ready',
    create_time     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_eval_rag_benchmark_knowledge_id ON eval_rag_benchmark (knowledge_id);
COMMENT ON TABLE eval_rag_benchmark IS 'RAG 评估基准表';
COMMENT ON COLUMN eval_rag_benchmark.status IS '状态：generating-生成中, ready-就绪';

-- ========================================
-- RAG 评估基准题目表
-- ========================================
CREATE TABLE eval_rag_benchmark_item (
    id              BIGINT        NOT NULL,
    benchmark_id    BIGINT        NOT NULL,
    query           VARCHAR(2000) NOT NULL,
    gold_chunk_ids  VARCHAR(2000),
    gold_answer     TEXT,
    sort_order      INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_eval_rag_benchmark_item_benchmark_id ON eval_rag_benchmark_item (benchmark_id);
COMMENT ON TABLE eval_rag_benchmark_item IS 'RAG 评估基准题目表';

-- ========================================
-- RAG 评估结果表
-- ========================================
CREATE TABLE eval_rag_result (
    id              BIGINT        NOT NULL,
    knowledge_id    BIGINT        NOT NULL,
    benchmark_id    BIGINT        NOT NULL,
    benchmark_name  VARCHAR(128),
    status          VARCHAR(20)   NOT NULL DEFAULT 'RUNNING',
    overall_score   DOUBLE PRECISION,
    retrieval_json  TEXT,
    answer_json     TEXT,
    config_json     TEXT,
    duration_ms     BIGINT,
    analysis        TEXT,
    error           TEXT,
    create_time     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_eval_rag_result_knowledge_id ON eval_rag_result (knowledge_id);
COMMENT ON TABLE eval_rag_result IS 'RAG 评估结果表';
COMMENT ON COLUMN eval_rag_result.analysis IS 'AI评估分析报告';

-- ========================================
-- RAG 评估结果详情表
-- ========================================
CREATE TABLE eval_rag_result_detail (
    id                  BIGINT        NOT NULL,
    result_id           BIGINT        NOT NULL,
    query               VARCHAR(2000) NOT NULL,
    gold_chunk_ids      VARCHAR(2000),
    gold_answer         TEXT,
    generated_answer    TEXT,
    retrieved_chunk_ids VARCHAR(2000),
    retrieval_scores    TEXT,
    answer_score        DOUBLE PRECISION,
    answer_reasoning    TEXT,
    sort_order          INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_eval_rag_result_detail_result_id ON eval_rag_result_detail (result_id);
COMMENT ON TABLE eval_rag_result_detail IS 'RAG 评估结果详情表';

-- ============================================================
-- 预制数据
-- ============================================================

-- 内置默认Agent（id=1）
INSERT INTO agent (id, user_id, name, description, agent_type, system_prompt, welcome_message, status, is_default, version, create_time, update_time, deleted)
VALUES (
    1, 1, 'LightBot 助手', '默认AI助手', 'chat',
    '你是 LightBot 智能助手，请用中文回答用户问题。回答应简洁准确，遇到不确定的信息请如实告知。',
    '## 你好，我是 LightBot
有什么可以帮你的？',
    'published', FALSE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
) ON CONFLICT (id) DO NOTHING;

-- 系统配置
INSERT INTO system_config (config_key, config_value, description) VALUES
('default_ai_provider', '{"providerId": null, "modelId": null}', '默认AI模型配置（生成提示词、推荐问题等功能使用）'),
('default_chat_model', '{"providerId": null, "modelId": null}', '默认对话模型配置'),
('default_embedding_model', '{"providerId": null, "modelId": null}', '默认向量模型配置（知识库文档嵌入等场景使用）'),
('default_tts_model', '{"providerId": null, "modelId": null}', '默认TTS模型配置（语音合成等场景使用）'),
('default_rerank_model', '{"providerId": null, "modelId": null}', '默认重排模型配置（知识库检索精排等场景使用）')
ON CONFLICT (config_key) DO NOTHING;

-- 评估器模板（3个）
INSERT INTO eval_evaluator_template (id, evaluator_template_key, template_desc, template, variables, model_config) VALUES
(10001, 'text_similarity', '文本相似度评估', '请评估以下两个文本的相似度，分数范围为0-1，保留两位小数。

文本1：{{reference_output}}

文本2：{{actual_output}}

相似度分数：', 'reference_output,actual_output', '{"temperature": 0.1}'),
(10002, 'code_quality', '代码质量评估', '请评估以下代码的质量，从可读性、效率和最佳实践三个方面进行分析，并给出0-1的总分，保留两位小数。

代码：
{{code}}

评估报告：', 'code', '{"temperature": 0.2}'),
(10003, 'sentiment_analysis', '情感分析评估', '请分析以下文本的情感倾向，输出-1到1之间的情感分数，其中-1表示非常负面，0表示中性，1表示非常正面，保留两位小数。

文本：{{text}}

情感分数：', 'text', '{"temperature": 0.1}');

-- Prompt 构建模板（16个）
INSERT INTO prompt_build_template (id, prompt_template_key, tags, template_desc, template, variables, model_config, tool_config) VALUES
(10001, 'general_assistant', '通用,助手', '通用AI助手模板', '你是一个专业的AI助手。请根据用户的问题提供准确、有帮助的回答。

角色：{{role}}
任务：{{task}}

用户输入：{{user_input}}', 'role,task,user_input', '{"temperature": 0.7}', '{}'),
(10002, 'code_reviewer', '代码,审查', '代码审查模板', '你是一个资深的代码审查专家。请对以下代码进行审查，指出问题并给出改进建议。

审查语言：{{language}}
审查重点：{{focus}}

代码：
{{code}}', 'language,focus,code', '{"temperature": 0.3}', '{}'),
(10003, 'translator', '翻译', '翻译专家模板', '你是一个专业的翻译专家，精通多种语言。请将以下文本翻译成目标语言。

源语言：{{source_lang}}
目标语言：{{target_lang}}

原文：{{text}}', 'source_lang,target_lang,text', '{"temperature": 0.3}', '{}'),
(10004, 'conversational_ai', 'chat,dialogue', '对话式AI模板', '你是一个{{role}}，具有以下特点：
{{personality}}

在与用户对话时，请遵循以下原则：
1. {{principle_1}}
2. {{principle_2}}
3. {{principle_3}}

用户：{{user_input}}

请回复：', 'role,personality,principle_1,principle_2,principle_3,user_input', '{"temperature": 0.7, "maxTokens": 2000}', '{}'),
(10005, 'social_media_promotion', 'social,promotion', '社交媒体推销文案生成模板', '你是一个擅长撰写社交媒体文案的 AI 助手，请根据提供的产品信息生成一条适合发布在{{platform}}平台的推广文案。

要求：
1. 使用轻松、亲切的口吻，像朋友分享好物；
2. 结尾添加相关话题标签，如 #好物推荐；

产品信息：
{{product_info}}', 'platform,product_info', '{"temperature": 0.8, "maxTokens": 500}', '{}'),
(10006, 'product_promotion', 'goods,promotion', '商品推广Prompt模板', '请为以下商品写一段推广文案：

商品名称：{{product_name}}
商品特点：{{features}}
目标人群：{{target_audience}}

要求：
1. 突出商品卖点
2. 语言简洁有力
3. 吸引目标人群购买', 'product_name,features,target_audience', '{"temperature": 0.7, "maxTokens": 300}', '{}'),
(10007, 'task_executor', 'task,execution', '任务执行模板', '你是一个专业的{{domain}}专家，请完成以下任务：

## 任务描述
{{task_description}}

## 输入信息
{{input_data}}

## 输出要求
{{output_requirements}}

## 约束条件
{{constraints}}

请按要求完成任务：', 'domain,task_description,input_data,output_requirements,constraints', '{"temperature": 0.3, "maxTokens": 3000}', '{}'),
(10008, 'analysis_report', 'analysis,report', '分析报告模板', '请对以下{{analysis_subject}}进行深入分析：

## 分析对象
{{subject_details}}

## 分析维度
{{analysis_dimensions}}

## 参考标准
{{reference_standards}}

## 报告结构
1. 摘要
2. 详细分析
3. 关键发现
4. 结论和建议

请生成完整的分析报告：', 'analysis_subject,subject_details,analysis_dimensions,reference_standards', '{"temperature": 0.4, "maxTokens": 4000}', '{}'),
(10009, 'creative_generator', 'creative,generation', '创意生成模板', '请为{{project_type}}项目生成创意方案：

## 项目背景
{{background}}

## 目标群体
{{target_audience}}

## 核心需求
{{core_requirements}}

## 创意约束
{{creative_constraints}}

## 输出要求
- 提供3-5个不同的创意方向
- 每个方向包含核心概念和执行要点
- 评估可行性和预期效果

请开始生成创意：', 'project_type,background,target_audience,core_requirements,creative_constraints', '{"temperature": 0.9, "maxTokens": 3000}', '{}'),
(10010, 'problem_solver', 'problem,solution', '问题解决模板', '作为{{expert_role}}，请帮助解决以下问题：

## 问题描述
{{problem_description}}

## 现状分析
{{current_situation}}

## 已尝试方案
{{attempted_solutions}}

## 限制条件
{{limitations}}

## 解决方案要求
1. 分析问题根因
2. 提供多个可选方案
3. 评估方案的可行性和风险
4. 推荐最优方案和实施步骤

请提供解决方案：', 'expert_role,problem_description,current_situation,attempted_solutions,limitations', '{"temperature": 0.5, "maxTokens": 3500}', '{}'),
(10011, 'teaching_assistant', 'education,teaching', '教学辅导模板', '你是一位经验丰富的{{subject}}老师，请为学生提供学习指导：

## 学生信息
- 学习水平：{{student_level}}
- 学习目标：{{learning_goal}}

## 教学内容
{{teaching_content}}

## 学生问题
{{student_question}}

## 教学要求
1. 用简单易懂的语言解释
2. 提供具体的例子
3. 给出练习建议
4. 鼓励学生思考

请开始教学：', 'subject,student_level,learning_goal,teaching_content,student_question', '{"temperature": 0.6, "maxTokens": 2500}', '{}'),
(10012, 'content_writer', '写作,内容创作', '内容创作专家模板，适用于文章、博客、营销文案等场景', '你是一位资深的内容创作专家，擅长撰写各类文体。请根据以下要求创作内容。

内容类型：{{content_type}}
目标受众：{{target_audience}}
风格要求：{{style}}
主题：{{topic}}

请直接输出内容：', 'content_type,target_audience,style,topic', '{"temperature": 0.8}', '{}'),
(10013, 'data_analyst', '数据分析', '数据分析专家模板，适用于数据解读、报表分析、趋势预测等场景', '你是一位专业的数据分析师。请根据以下数据和问题进行分析。

数据描述：{{data_description}}
分析目标：{{analysis_goal}}
数据样本：
{{data_sample}}

请提供分析结论和建议：', 'data_description,analysis_goal,data_sample', '{"temperature": 0.3}', '{}'),
(10014, 'customer_service', '客服', '智能客服模板，适用于售前咨询、售后支持、投诉处理等场景', '你是一位专业的客服代表，态度友好、耐心细致。请根据以下信息回复客户。

客服角色：{{service_role}}
客户问题：{{customer_issue}}
产品信息：{{product_info}}
回复语言：{{reply_lang}}

请给出专业回复：', 'service_role,customer_issue,product_info,reply_lang', '{"temperature": 0.5}', '{}'),
(10015, 'summarizer', '摘要,总结', '文本摘要专家模板，适用于长文摘要、会议纪要、报告精简等场景', '你是一位文本摘要专家。请对以下内容进行精准概括。

摘要类型：{{summary_type}}
摘要长度：{{summary_length}}
原文：
{{original_text}}

请输出摘要：', 'summary_type,summary_length,original_text', '{"temperature": 0.3}', '{}'),
(10016, 'email_composer', '邮件', '邮件撰写专家模板，适用于商务邮件、工作汇报、客户沟通等场景', '你是一位专业的邮件撰写助手。请根据以下信息撰写邮件。

邮件场景：{{scenario}}
收件人：{{recipient}}
邮件目的：{{purpose}}
关键要点：{{key_points}}
语气：{{tone}}

请输出完整邮件（含主题行）：', 'scenario,recipient,purpose,key_points,tone', '{"temperature": 0.6}', '{}');

-- 标记系统工具
UPDATE tool SET is_system = TRUE WHERE name = 'query_knowledge';
