-- MCP Server 表
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
    create_time     TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_mcp_server_user_id ON mcp_server (user_id);
COMMENT ON TABLE mcp_server IS 'MCP Server表';
