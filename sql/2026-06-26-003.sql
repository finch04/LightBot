-- API Key 增强：限流、作用域、配额
ALTER TABLE api_key ADD COLUMN agent_ids JSONB DEFAULT NULL;
ALTER TABLE api_key ADD COLUMN rate_limit INT NOT NULL DEFAULT 60;
ALTER TABLE api_key ADD COLUMN daily_quota INT NOT NULL DEFAULT 100000;
ALTER TABLE api_key ADD COLUMN used_tokens BIGINT NOT NULL DEFAULT 0;
ALTER TABLE api_key ADD COLUMN quota_reset_at DATE DEFAULT NULL;

COMMENT ON COLUMN api_key.agent_ids IS '绑定的Agent ID列表，null表示全部';
COMMENT ON COLUMN api_key.rate_limit IS '每分钟调用上限，默认60';
COMMENT ON COLUMN api_key.daily_quota IS '每日Token配额，默认100000';
COMMENT ON COLUMN api_key.used_tokens IS '当日已用Token数';
COMMENT ON COLUMN api_key.quota_reset_at IS '配额重置日期（每日重置时比较）';
