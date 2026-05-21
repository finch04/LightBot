-- 知识库新增示例问题字段
ALTER TABLE knowledge ADD COLUMN example_questions JSONB DEFAULT '[]';
COMMENT ON COLUMN knowledge.example_questions IS '示例问题列表（JSON数组）';
