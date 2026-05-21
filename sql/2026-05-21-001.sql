-- 任务队列表：跟踪长耗时操作（文档入库、OCR等）
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
    PRIMARY KEY (id)
);
CREATE INDEX idx_task_user_id ON task (user_id);
CREATE INDEX idx_task_status ON task (status);
CREATE INDEX idx_task_type ON task (type);
COMMENT ON TABLE task IS '任务队列表';
