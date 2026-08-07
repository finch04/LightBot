-- 业务页不再使用 formSchema：清空历史兜底数据，主路径仅为 pageHtml / pageUrl
UPDATE business_page SET form_schema = NULL WHERE form_schema IS NOT NULL;
COMMENT ON COLUMN business_page.form_schema IS '已废弃：业务页仅使用 page_html / page_url';
