-- 文档表新增内容重复率字段
ALTER TABLE document ADD COLUMN duplicate_rate DOUBLE PRECISION;
COMMENT ON COLUMN document.duplicate_rate IS '内容重复率（与知识库已有文档的最高相似度）';
