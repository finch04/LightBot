-- 知识库新增分块分隔符字段
ALTER TABLE knowledge ADD COLUMN chunk_delimiter VARCHAR(64);
COMMENT ON COLUMN knowledge.chunk_delimiter IS '分块分隔符（为空时使用默认换行符）';
