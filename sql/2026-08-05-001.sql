-- 会话绑定调用方身份上下文；问数数据集租户维度映射
-- caller_context: { externalUserId, regionId, enterpriseId, profile }
-- tenant_dimensions: { "regionId": "region_id", "enterpriseId": "enterprise_id" }

ALTER TABLE chat_session
    ADD COLUMN IF NOT EXISTS caller_context JSONB;

COMMENT ON COLUMN chat_session.caller_context IS 'API 会话绑定的调用方身份上下文（externalUserId/regionId/enterpriseId/profile）';

ALTER TABLE ask_dataset
    ADD COLUMN IF NOT EXISTS tenant_dimensions JSONB;

COMMENT ON COLUMN ask_dataset.tenant_dimensions IS '租户维度映射：callerContext 键 → 数据表字段（如 regionId→region_id），问数强制注入';
