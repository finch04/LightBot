-- 新增系统配置：默认重排模型（RAG 召回后精排）

INSERT INTO system_config (config_key, config_value, description)
VALUES ('default_rerank_model', '{"providerId": null, "modelId": null}', '默认重排模型配置（知识库检索精排等场景使用）')
ON CONFLICT (config_key) DO UPDATE SET
    config_value = EXCLUDED.config_value,
    description = EXCLUDED.description,
    update_time = CURRENT_TIMESTAMP;
