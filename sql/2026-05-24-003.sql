-- 系统配置表（存储全局AI配置等）
CREATE TABLE system_config (
    config_key   VARCHAR(64)    NOT NULL,
    config_value TEXT,
    description  VARCHAR(255),
    create_time  TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (config_key)
);

COMMENT ON TABLE system_config IS '系统配置表';
COMMENT ON COLUMN system_config.config_key IS '配置键，如 default_ai_provider';
COMMENT ON COLUMN system_config.config_value IS '配置值，JSON格式';
COMMENT ON COLUMN system_config.description IS '配置描述';

-- 初始化默认AI配置（首次使用需用户在前端设置）
INSERT INTO system_config (config_key, config_value, description)
VALUES ('default_ai_provider', '{"providerId": null, "modelId": null}', '默认AI模型配置（生成提示词、推荐问题等功能使用）');