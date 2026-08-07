-- 业务页主路径改为 H5：注册 page_url，对话内 iframe 渲染；form_schema 仅作无 H5 时的兜底

ALTER TABLE business_page
    ADD COLUMN IF NOT EXISTS page_url VARCHAR(1024);

COMMENT ON COLUMN business_page.page_url IS '开发者托管的 H5 业务页入口 URL（对话内 iframe 加载）';
COMMENT ON COLUMN business_page.form_schema IS '可选兜底：无 page_url 时用通用表单渲染';
