-- 文档表新增 markdown_path 字段，存储转换后的 Markdown 文件路径
ALTER TABLE document ADD COLUMN markdown_path VARCHAR(512);
COMMENT ON COLUMN document.markdown_path IS 'Markdown文件存储路径';
