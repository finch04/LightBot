-- 自动化执行记录：落库对话详情快照（思考/工具/工作流事件等，与 UI 对话消息同构）

ALTER TABLE automation_job_run
    ADD COLUMN IF NOT EXISTS detail_json JSONB;

COMMENT ON COLUMN automation_job_run.detail_json IS '执行详情快照：content/metadata/toolEvents 等，供任务详情展示';
