-- 分块表新增向量化状态字段
ALTER TABLE chunk ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'chunked';
CREATE INDEX idx_chunk_status ON chunk (status);
COMMENT ON COLUMN chunk.status IS '向量化状态: chunked/vectorizing/vectorized/failed';

-- 文档表新增分块策略字段
ALTER TABLE document ADD COLUMN chunk_strategy VARCHAR(32) DEFAULT 'general';
COMMENT ON COLUMN document.chunk_strategy IS '分块策略: general/book/separator';

-- 已有 embedding 记录的 chunk 标记为已向量化
UPDATE chunk SET status = 'vectorized' WHERE id IN (SELECT chunk_id FROM embedding);
