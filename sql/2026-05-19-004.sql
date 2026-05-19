-- Agent config JSONB 结构迁移
-- 旧结构: {"provider": "dashscope", "modelId": "qwen-plus", ...}
-- 新结构: {"providerId": 1234567890, "modelId": "qwen-plus", ...}
-- providerId 关联 model_provider.id，运行时通过 ModelFactory 动态创建 ChatModel

-- 删除 agent 表的独立配置列（如果存在，来自之前的迁移）
ALTER TABLE agent DROP COLUMN IF EXISTS model_id;
ALTER TABLE agent DROP COLUMN IF EXISTS temperature;
ALTER TABLE agent DROP COLUMN IF EXISTS top_p;
ALTER TABLE agent DROP COLUMN IF EXISTS max_tokens;
ALTER TABLE agent DROP COLUMN IF EXISTS repetition_penalty;
