-- 知识库类型 + 检索配置字段
-- 1. knowledge 表加 type 列（知识库类型：pg / milvus）
ALTER TABLE knowledge ADD COLUMN IF NOT EXISTS type VARCHAR(32) NOT NULL DEFAULT 'pg';
CREATE INDEX IF NOT EXISTS idx_knowledge_type ON knowledge(type);

-- 2. knowledge 表加 query_params 列（检索配置，独立 JSONB 字段）
ALTER TABLE knowledge ADD COLUMN IF NOT EXISTS query_params JSONB DEFAULT '{}';
