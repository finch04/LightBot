-- 修复 embedding 表：chunk_id 允许为 NULL（QA pair 向量不需要 chunk_id）
-- 并将唯一索引改为部分索引，仅对非 NULL 值唯一

-- 1. 移除 chunk_id 的 NOT NULL 约束
ALTER TABLE embedding ALTER COLUMN chunk_id DROP NOT NULL;

-- 2. 删除旧的唯一索引
DROP INDEX IF EXISTS uk_embedding_chunk_id;

-- 3. 创建部分唯一索引（仅对非 NULL chunk_id 唯一）
CREATE UNIQUE INDEX uk_embedding_chunk_id ON embedding (chunk_id) WHERE chunk_id IS NOT NULL;

-- 4. 创建部分唯一索引（仅对非 NULL qa_pair_id 唯一）
CREATE UNIQUE INDEX uk_embedding_qa_pair_id ON embedding (qa_pair_id) WHERE qa_pair_id IS NOT NULL;
