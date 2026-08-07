-- 业务办理页：默认 options（身份透传等页面级配置）

ALTER TABLE business_page
    ADD COLUMN IF NOT EXISTS default_options JSONB;

COMMENT ON COLUMN business_page.default_options IS '默认 options JSON（含身份透传 injectIdentityHeaders/contextHeaders 等）';
