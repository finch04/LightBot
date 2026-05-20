-- 模型提供商表新增字段：模型列表获取地址、额外请求头、扩展配置
ALTER TABLE model_provider ADD COLUMN models_endpoint VARCHAR(512);
COMMENT ON COLUMN model_provider.models_endpoint IS '模型列表获取地址（为空时使用默认地址）';

ALTER TABLE model_provider ADD COLUMN headers_json JSONB DEFAULT '{}';
COMMENT ON COLUMN model_provider.headers_json IS '额外请求头（JSON格式）';

ALTER TABLE model_provider ADD COLUMN extra_json JSONB DEFAULT '{}';
COMMENT ON COLUMN model_provider.extra_json IS '扩展配置（JSON格式）';
