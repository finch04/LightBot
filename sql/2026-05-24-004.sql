-- 新增 agent_version 表：工作流/对话智能体版本配置独立存储，避免堆入 agent.config
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
