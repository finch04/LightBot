-- 模型表
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
