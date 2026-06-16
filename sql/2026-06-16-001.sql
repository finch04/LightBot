-- chunk 表新增 tsvector 列（pgvector 全文检索支持）
ALTER TABLE chunk ADD COLUMN IF NOT EXISTS content_tsv tsvector;

-- 回填已有数据
UPDATE chunk SET content_tsv = to_tsvector('simple', content) WHERE content_tsv IS NULL;

-- GIN 索引
CREATE INDEX IF NOT EXISTS idx_chunk_content_tsv ON chunk USING GIN(content_tsv);

-- 触发器：自动维护 content_tsv
CREATE OR REPLACE FUNCTION update_chunk_content_tsv() RETURNS TRIGGER AS $$
BEGIN
    NEW.content_tsv := to_tsvector('simple', NEW.content);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_chunk_content_tsv
    BEFORE INSERT OR UPDATE OF content ON chunk
    FOR EACH ROW EXECUTE FUNCTION update_chunk_content_tsv();
