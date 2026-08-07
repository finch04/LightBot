-- 企业长期记忆默认策略 + 企业 API Key 分策略覆盖

-- 1. 企业默认（system_config）
INSERT INTO system_config (config_key, config_value, description) VALUES (
  'long_memory_config',
  '{"enabled":true,"autoExtract":true,"injectLimit":6,"scope":"user"}',
  '企业长期记忆默认策略：启用、自动抽取、注入条数、作用域(user=跨Agent/agent=按Agent)'
) ON CONFLICT (config_key) DO NOTHING;

-- 2. API Key 级策略覆盖（null / inherit=true 表示跟随企业默认）
ALTER TABLE api_key
    ADD COLUMN IF NOT EXISTS memory_config JSONB;

COMMENT ON COLUMN api_key.memory_config IS '长期记忆策略覆盖；null 或 inherit=true 跟随企业默认';
