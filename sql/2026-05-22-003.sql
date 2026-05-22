-- 任务表新增逻辑删除字段
ALTER TABLE task ADD COLUMN deleted SMALLINT NOT NULL DEFAULT 0;
COMMENT ON COLUMN task.deleted IS '逻辑删除标记（0=正常 1=已删除）';
