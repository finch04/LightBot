-- LightBot Database Initialization
-- PostgreSQL 15 + pgvector

CREATE DATABASE lightbot ENCODING 'UTF8';
\c lightbot;

CREATE EXTENSION IF NOT EXISTS vector;

-- ========================================
-- 用户表
-- ========================================
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
    last_login_at   TIMESTAMPTZ,
    config          JSONB           DEFAULT '{}',
    create_time     TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_user_username ON t_user (username) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_user_email ON t_user (email) WHERE deleted = 0;
CREATE INDEX idx_user_status ON t_user (status);
CREATE INDEX idx_user_create_time ON t_user (create_time);
COMMENT ON TABLE t_user IS '用户表';

-- ========================================
-- 模型提供商表
-- ========================================
CREATE TABLE t_model_provider (
    id              BIGINT          NOT NULL,
    name            VARCHAR(64)     NOT NULL,
    type            VARCHAR(32)     NOT NULL,
    api_key         VARCHAR(512),
    base_url        VARCHAR(256),
    config          JSONB           DEFAULT '{}',
    status          VARCHAR(20)     NOT NULL DEFAULT 'active',
    create_time     TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_model_provider_type ON t_model_provider (type);
CREATE INDEX idx_model_provider_status ON t_model_provider (status);
COMMENT ON TABLE t_model_provider IS '模型提供商表';

-- ========================================
-- Agent 表
-- ========================================
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
    publish_time    TIMESTAMPTZ,
    version         INT             NOT NULL DEFAULT 1,
    create_time     TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_agent_user_id ON t_agent (user_id);
CREATE INDEX idx_agent_status ON t_agent (status);
CREATE INDEX idx_agent_create_time ON t_agent (create_time);
COMMENT ON TABLE t_agent IS 'Agent表';

-- ========================================
-- 对话会话表
-- ========================================
CREATE TABLE t_chat_session (
    id              BIGINT          NOT NULL,
    agent_id        BIGINT          NOT NULL,
    user_id         BIGINT          NOT NULL,
    title           VARCHAR(256),
    status          VARCHAR(20)     NOT NULL DEFAULT 'active',
    context         JSONB           DEFAULT '{}',
    message_count   INT             NOT NULL DEFAULT 0,
    total_tokens    BIGINT          NOT NULL DEFAULT 0,
    last_message_at TIMESTAMPTZ,
    create_time     TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_chat_session_agent_id ON t_chat_session (agent_id);
CREATE INDEX idx_chat_session_user_id ON t_chat_session (user_id);
CREATE INDEX idx_chat_session_status ON t_chat_session (status);
CREATE INDEX idx_chat_session_last_message ON t_chat_session (last_message_at DESC);
COMMENT ON TABLE t_chat_session IS '对话会话表';

-- ========================================
-- 消息表
-- ========================================
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
    create_time     TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX idx_message_session_id ON t_message (session_id);
CREATE INDEX idx_message_create_time ON t_message (session_id, create_time);
CREATE INDEX idx_message_role ON t_message (session_id, role);
COMMENT ON TABLE t_message IS '消息表';

-- ========================================
-- 知识库表
-- ========================================
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
    create_time     TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_knowledge_user_id ON t_knowledge (user_id);
CREATE INDEX idx_knowledge_status ON t_knowledge (status);
COMMENT ON TABLE t_knowledge IS '知识库表';

-- ========================================
-- 文档表
-- ========================================
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
    create_time     TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_document_knowledge_id ON t_document (knowledge_id);
CREATE INDEX idx_document_user_id ON t_document (user_id);
CREATE INDEX idx_document_status ON t_document (status);
CREATE INDEX idx_document_file_hash ON t_document (file_hash);
COMMENT ON TABLE t_document IS '文档表';

-- ========================================
-- 文档分块表
-- ========================================
CREATE TABLE t_chunk (
    id              BIGINT          NOT NULL,
    document_id     BIGINT          NOT NULL,
    knowledge_id    BIGINT          NOT NULL,
    content         TEXT            NOT NULL,
    chunk_index     INT             NOT NULL,
    token_count     INT             NOT NULL DEFAULT 0,
    metadata        JSONB           DEFAULT '{}',
    create_time     TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX idx_chunk_document_id ON t_chunk (document_id);
CREATE INDEX idx_chunk_knowledge_id ON t_chunk (knowledge_id);
COMMENT ON TABLE t_chunk IS '文档分块表';

-- ========================================
-- 向量表
-- ========================================
CREATE TABLE t_embedding (
    id              BIGINT          NOT NULL,
    chunk_id        BIGINT          NOT NULL,
    model_name      VARCHAR(64)     NOT NULL,
    dimension       INT             NOT NULL DEFAULT 1536,
    vector          vector(1536)    NOT NULL,
    create_time     TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_embedding_chunk_id ON t_embedding (chunk_id);
CREATE INDEX idx_embedding_vector ON t_embedding
    USING hnsw (vector vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
COMMENT ON TABLE t_embedding IS '向量表';

-- ========================================
-- Tool 表
-- ========================================
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
    create_time     TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_tool_name ON t_tool (name) WHERE deleted = 0;
CREATE INDEX idx_tool_type ON t_tool (tool_type);
CREATE INDEX idx_tool_status ON t_tool (status);
COMMENT ON TABLE t_tool IS 'Tool表';

-- ========================================
-- Skill 表
-- ========================================
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
    create_time     TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_skill_agent_id ON t_skill (agent_id);
CREATE INDEX idx_skill_tool_id ON t_skill (tool_id);
COMMENT ON TABLE t_skill IS 'Skill表';

-- ========================================
-- Workflow 表
-- ========================================
CREATE TABLE t_workflow (
    id              BIGINT          NOT NULL,
    agent_id        BIGINT,
    user_id         BIGINT          NOT NULL,
    name            VARCHAR(128)    NOT NULL,
    description     TEXT,
    graph_data      JSONB           NOT NULL DEFAULT '{}',
    config          JSONB           DEFAULT '{}',
    status          VARCHAR(20)     NOT NULL DEFAULT 'draft',
    version         INT             NOT NULL DEFAULT 1,
    create_time     TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_workflow_user_id ON t_workflow (user_id);
CREATE INDEX idx_workflow_agent_id ON t_workflow (agent_id);
CREATE INDEX idx_workflow_status ON t_workflow (status);
COMMENT ON TABLE t_workflow IS 'Workflow表';

-- ========================================
-- Workflow 节点表
-- ========================================
CREATE TABLE t_workflow_node (
    id              BIGINT          NOT NULL,
    workflow_id     BIGINT          NOT NULL,
    node_key        VARCHAR(64)     NOT NULL,
    node_type       VARCHAR(32)     NOT NULL,
    name            VARCHAR(128)    NOT NULL,
    description     TEXT,
    config          JSONB           DEFAULT '{}',
    inputs          JSONB           DEFAULT '[]',
    outputs         JSONB           DEFAULT '[]',
    position_x      FLOAT           NOT NULL DEFAULT 0,
    position_y      FLOAT           NOT NULL DEFAULT 0,
    create_time     TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_workflow_node_key ON t_workflow_node (workflow_id, node_key);
CREATE INDEX idx_workflow_node_type ON t_workflow_node (workflow_id, node_type);
COMMENT ON TABLE t_workflow_node IS 'Workflow节点表';
