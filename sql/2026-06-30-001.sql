-- SubAgent 增加 llm_model 字段：与 model_id（Provider ID）配合，支持 provider:model 独立配置
ALTER TABLE subagent ADD COLUMN IF NOT EXISTS llm_model VARCHAR(128);
COMMENT ON COLUMN subagent.model_id IS '可选的 Provider ID 覆盖，null 表示继承主 Agent';
COMMENT ON COLUMN subagent.llm_model IS '可选的模型名称覆盖（如 gpt-4o），与 model_id 配合使用';
