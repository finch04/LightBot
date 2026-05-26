-- 新增系统配置：默认向量模型配置、默认TTS模型配置
-- 用于知识库文档嵌入、语音合成等非Agent场景

-- 默认向量模型配置（知识库文档入库时使用）
INSERT INTO system_config (config_key, config_value, description)
VALUES ('default_embedding_model', '{"providerId": null, "modelId": null}', '默认向量模型配置（知识库文档嵌入等场景使用）')
ON CONFLICT (config_key) DO UPDATE SET
    config_value = EXCLUDED.config_value,
    description = EXCLUDED.description,
    update_time = CURRENT_TIMESTAMP;

-- 默认TTS模型配置（语音合成场景使用）
INSERT INTO system_config (config_key, config_value, description)
VALUES ('default_tts_model', '{"providerId": null, "modelId": null}', '默认TTS模型配置（语音合成等场景使用）')
ON CONFLICT (config_key) DO UPDATE SET
    config_value = EXCLUDED.config_value,
    description = EXCLUDED.description,
    update_time = CURRENT_TIMESTAMP;

-- 默认对话模型配置
INSERT INTO system_config (config_key, config_value, description)
VALUES ('default_chat_model', '{"providerId": null, "modelId": null}', '默认对话模型配置（生成提示词、推荐问题等功能使用）')
ON CONFLICT (config_key) DO NOTHING;

COMMENT ON TABLE system_config IS '系统配置表';
