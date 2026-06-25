-- 子代理运行记录表（运行追踪 + 幂等性）
CREATE TABLE subagent_run (
    id              BIGINT          NOT NULL,
    thread_id       VARCHAR(100)    NOT NULL,
    parent_thread_id VARCHAR(100)   NOT NULL,
    subagent_name   VARCHAR(100)    NOT NULL,
    task            TEXT            NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'pending',
    request_id      VARCHAR(64)     NOT NULL,
    reply           TEXT,
    tool_call_count INTEGER         DEFAULT 0,
    start_time      TIMESTAMP,
    end_time        TIMESTAMP,
    error_message   TEXT,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX idx_subagent_run_request_id ON subagent_run(request_id);
CREATE INDEX idx_subagent_run_thread_id ON subagent_run(thread_id);
