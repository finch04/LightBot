-- 文档表新增：重复文档详情（top3，含文档名和相似度）
ALTER TABLE document ADD COLUMN duplicate_details JSONB;
COMMENT ON COLUMN document.duplicate_details IS '重复文档详情（top3，含文档名和相似度）';
