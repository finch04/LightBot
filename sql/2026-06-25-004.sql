-- API Key 管理表
CREATE TABLE api_key (
    id              BIGINT          NOT NULL,
    user_id         BIGINT          NOT NULL,
    name            VARCHAR(64)     NOT NULL,
    key_prefix      VARCHAR(20)     NOT NULL,
    key_hash        VARCHAR(64)     NOT NULL,
    permissions     VARCHAR(32)     NOT NULL DEFAULT 'chat',
    is_enabled      SMALLINT        NOT NULL DEFAULT 1,
    last_used_at    TIMESTAMP       NULL,
    expires_at      TIMESTAMP       NULL,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_api_key_user_id ON api_key (user_id);
CREATE UNIQUE INDEX uk_api_key_hash ON api_key (key_hash);
COMMENT ON TABLE api_key IS 'API Key管理表';
