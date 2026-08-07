-- 自动化定时任务：任务配置 + 执行记录

CREATE TABLE IF NOT EXISTS automation_job (
    id              BIGINT       NOT NULL,
    user_id         BIGINT       NOT NULL,
    name            VARCHAR(128) NOT NULL,
    agent_id        BIGINT       NOT NULL,
    agent_name      VARCHAR(128),
    instruction     TEXT         NOT NULL,
    schedule_type   VARCHAR(20)  NOT NULL,
    schedule_config JSONB        NOT NULL DEFAULT '{}'::jsonb,
    enabled         SMALLINT     NOT NULL DEFAULT 1,
    next_run_at     TIMESTAMP,
    last_run_at     TIMESTAMP,
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_automation_job_user ON automation_job (user_id);
CREATE INDEX IF NOT EXISTS idx_automation_job_due ON automation_job (enabled, next_run_at)
    WHERE deleted = 0 AND enabled = 1;
COMMENT ON TABLE automation_job IS '自动化定时任务配置';
COMMENT ON COLUMN automation_job.schedule_type IS 'once/daily/weekly/monthly/cron';
COMMENT ON COLUMN automation_job.schedule_config IS '调度参数 JSON';
COMMENT ON COLUMN automation_job.next_run_at IS '下次触发时间；NULL 表示不再调度';

CREATE TABLE IF NOT EXISTS automation_job_run (
    id              BIGINT       NOT NULL,
    job_id          BIGINT       NOT NULL,
    user_id         BIGINT       NOT NULL,
    agent_id        BIGINT       NOT NULL,
    session_id      BIGINT,
    job_name        VARCHAR(128),
    agent_name      VARCHAR(128),
    instruction     TEXT,
    trigger_type    VARCHAR(20)  NOT NULL DEFAULT 'schedule',
    trigger_time    TIMESTAMP    NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    lease_expire_at TIMESTAMP,
    summary         TEXT,
    error           TEXT,
    duration_ms     BIGINT,
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_automation_job_run_job ON automation_job_run (job_id);
CREATE INDEX IF NOT EXISTS idx_automation_job_run_user ON automation_job_run (user_id, trigger_time DESC);
CREATE INDEX IF NOT EXISTS idx_automation_job_run_lease ON automation_job_run (status, lease_expire_at)
    WHERE status = 'running';
COMMENT ON TABLE automation_job_run IS '自动化定时任务执行记录';
COMMENT ON COLUMN automation_job_run.trigger_type IS 'schedule=定时触发 / manual=立即执行';
COMMENT ON COLUMN automation_job_run.lease_expire_at IS 'running 租约到期时间，用于僵尸回收';
