-- 评估器表新增 tags 字段
ALTER TABLE eval_evaluator ADD COLUMN tags VARCHAR(200);
COMMENT ON COLUMN eval_evaluator.tags IS '标签，逗号分隔';
