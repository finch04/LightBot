-- 工作流测试运行记录表（编排页测试专用，非 Chat 正式运行 / 非 llm_trace）
CREATE TABLE workflow_test_run (
    id              BIGINT NOT NULL,
    run_id          VARCHAR(64) NOT NULL,
    agent_id        BIGINT NOT NULL,
    user_id         BIGINT NOT NULL,
    test_mode       VARCHAR(20) NOT NULL DEFAULT 'generation',
    used_draft      SMALLINT NOT NULL DEFAULT 1,
    status          VARCHAR(20) NOT NULL DEFAULT 'running',
    user_input      TEXT,
    output          TEXT,
    node_events     JSONB NOT NULL DEFAULT '[]',
    variables       JSONB,
    workflow_graph  JSONB,
    error_info      TEXT,
    duration_ms     BIGINT,
    start_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    end_time        TIMESTAMP,
    create_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_workflow_test_run_run_id ON workflow_test_run (run_id);
CREATE INDEX idx_workflow_test_run_agent_time ON workflow_test_run (agent_id, start_time DESC);
COMMENT ON TABLE workflow_test_run IS '工作流编排页测试运行记录';
