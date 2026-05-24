-- SubAgent 表：子智能体配置
CREATE TABLE IF NOT EXISTS subagent (
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
COMMENT ON COLUMN subagent.id IS '主键ID';
COMMENT ON COLUMN subagent.name IS '唯一标识（英文）';
COMMENT ON COLUMN subagent.display_name IS '显示名称（中文）';
COMMENT ON COLUMN subagent.description IS '子智能体描述';
COMMENT ON COLUMN subagent.system_prompt IS '系统提示词';
COMMENT ON COLUMN subagent.tools IS '工具名称列表（JSON数组）';
COMMENT ON COLUMN subagent.model_id IS '可选的模型覆盖';
COMMENT ON COLUMN subagent.enabled IS '是否启用';
COMMENT ON COLUMN subagent.is_builtin IS '是否内置';
COMMENT ON COLUMN subagent.user_id IS '创建者ID';
