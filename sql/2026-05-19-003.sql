-- Agent表：合并 presence_penalty + frequency_penalty 为 repetition_penalty（DashScope模型参数）
ALTER TABLE agent DROP COLUMN IF EXISTS presence_penalty;
ALTER TABLE agent DROP COLUMN IF EXISTS frequency_penalty;
ALTER TABLE agent ADD COLUMN repetition_penalty DOUBLE PRECISION;
COMMENT ON COLUMN agent.repetition_penalty IS '重复惩罚（0.0-2.0），DashScope模型参数';
