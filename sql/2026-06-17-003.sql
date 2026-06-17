-- 2026-06-17-003: tool 表新增 tags JSONB 字段，支持工具标签分类

ALTER TABLE tool ADD COLUMN tags JSONB NOT NULL DEFAULT '[]';
CREATE INDEX idx_tool_tags ON tool USING GIN (tags);
