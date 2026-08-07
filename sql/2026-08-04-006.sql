-- 业务页主路径改为直接登记 H5 HTML（iframe srcdoc），外链 page_url 仅作可选兼容

ALTER TABLE business_page
    ADD COLUMN IF NOT EXISTS page_html TEXT;

COMMENT ON COLUMN business_page.page_html IS '开发者直接登记的 H5 页面 HTML，对话内 iframe srcdoc 嵌入';
COMMENT ON COLUMN business_page.page_url IS '可选：外链 H5（无 page_html 时使用）';
