-- 知识库表新增思维导图数据字段
-- 用于存储AI生成的知识库思维导图（JSON格式）
ALTER TABLE knowledge ADD COLUMN mindmap_data JSONB;
COMMENT ON COLUMN knowledge.mindmap_data IS '思维导图数据（JSON格式树状结构）';
