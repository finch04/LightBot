-- 业务办理页注册表 + API Key 页面白名单

-- 1. 业务页模板（开发者在能力中心注册；不再由服务启动同步内置种子）
CREATE TABLE IF NOT EXISTS business_page (
    id              BIGINT       NOT NULL,
    page_type       VARCHAR(64)  NOT NULL,
    display_name    VARCHAR(128) NOT NULL,
    description     VARCHAR(512),
    default_title   VARCHAR(128) NOT NULL,
    allowed_modes   JSONB        NOT NULL DEFAULT '["inline"]',
    allowed_actions JSONB        NOT NULL DEFAULT '["submit","cancel"]',
    allowed_prop_keys JSONB      NOT NULL DEFAULT '[]',
    allowed_option_keys JSONB    NOT NULL DEFAULT '[]',
    default_props   JSONB        NOT NULL DEFAULT '{}',
    form_schema     JSONB,
    builtin         SMALLINT     NOT NULL DEFAULT 0,
    enabled         SMALLINT     NOT NULL DEFAULT 1,
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_business_page_type
    ON business_page (page_type) WHERE deleted = 0;

COMMENT ON TABLE business_page IS '业务办理页模板注册表（固化样式 + 可配置元数据）';
COMMENT ON COLUMN business_page.page_type IS '稳定页面类型码，如 phone_recharge';
COMMENT ON COLUMN business_page.form_schema IS '通用表单 schema（无专用 Vue 模板时使用）';
COMMENT ON COLUMN business_page.builtin IS '是否内置模板（1=内置，禁止删 page_type）';

-- 2. API Key 级业务页白名单
ALTER TABLE api_key
    ADD COLUMN IF NOT EXISTS business_page_config JSONB;

COMMENT ON COLUMN api_key.business_page_config IS
    '业务办理页白名单；null/inherit=true 表示全部已启用页；allowedPageTypes 为字符串数组';
