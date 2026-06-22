-- 工具表新增 output_example 列，存储输出示例 JSON
ALTER TABLE tool ADD COLUMN output_example JSONB DEFAULT '{}';
