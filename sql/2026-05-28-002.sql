-- 2026-05-28-002: 为prompt_version表添加tool_config字段，支持函数/工具配置持久化

-- 1. 新增tool_config字段（JSONB类型，存储工具配置）
ALTER TABLE prompt_version ADD COLUMN tool_config JSONB DEFAULT '{}';

COMMENT ON COLUMN prompt_version.tool_config IS '工具配置（JSON格式，存储Prompt关联的工具列表）';

-- 2. 为prompt_build_template表添加tool_config字段（模板也可以预设工具配置）
ALTER TABLE prompt_build_template ADD COLUMN tool_config JSONB DEFAULT '{}';

COMMENT ON COLUMN prompt_build_template.tool_config IS '工具配置（JSON格式）';