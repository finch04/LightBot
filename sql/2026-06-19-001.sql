-- 评估器表新增 tags 字段
ALTER TABLE eval_evaluator ADD COLUMN tags VARCHAR(200);
COMMENT ON COLUMN eval_evaluator.tags IS '标签，逗号分隔';

-- 实验结果表新增 evaluator_name 字段
ALTER TABLE eval_experiment_result ADD COLUMN evaluator_name VARCHAR(128);
COMMENT ON COLUMN eval_experiment_result.evaluator_name IS '评估器名称';
