-- 文档入库流程重构：document 表新增 embedding_json，移除 chunk_strategy
-- knowledge 表移除分块参数字段（下沉到文档级）

-- 1. document 表新增 embedding_json 字段
ALTER TABLE document ADD COLUMN embedding_json JSONB;
COMMENT ON COLUMN document.embedding_json IS '入库配置（chunkStrategy/chunkSize/chunkOverlap/chunkDelimiter）';

-- 2. 迁移已有数据：将 chunk_strategy 写入 embedding_json
UPDATE document SET embedding_json = jsonb_build_object('chunkStrategy', chunk_strategy) WHERE chunk_strategy IS NOT NULL;

-- 3. 迁移已有数据：从 knowledge 表读取分块参数写入 embedding_json
UPDATE document d
SET embedding_json = COALESCE(d.embedding_json, '{}'::jsonb)
    || jsonb_build_object(
        'chunkSize', COALESCE(k.chunk_size, 512),
        'chunkOverlap', COALESCE(k.chunk_overlap, 10),
        'chunkDelimiter', k.chunk_delimiter
    )
FROM knowledge k
WHERE d.knowledge_id = k.id
  AND d.embedding_json IS NOT NULL;

-- 4. document 表移除 chunk_strategy 字段
ALTER TABLE document DROP COLUMN IF EXISTS chunk_strategy;

-- 5. 状态迁移：PENDING 改为 UPLOADED（新流程中 PENDING 表示分块中）
UPDATE document SET status = 'uploaded' WHERE status = 'pending';

-- 6. knowledge 表移除分块参数字段
ALTER TABLE knowledge DROP COLUMN IF EXISTS chunk_size;
ALTER TABLE knowledge DROP COLUMN IF EXISTS chunk_overlap;
ALTER TABLE knowledge DROP COLUMN IF EXISTS chunk_delimiter;
