-- ============================================================
-- Skill 表演进：从「按 Agent 一行一条」改为「全局可复用 Skill + Agent 绑定」
-- ------------------------------------------------------------
-- 1. agent_id 改为可空（保留兼容旧数据；新建 Skill 不再依赖 agent_id）
-- 2. 新增 slug / display_name / tool_ids / mcp_server_ids / scope / is_builtin / model_id
-- 3. 新增唯一索引 uk_skill_slug 防止 slug 重复
-- ============================================================

ALTER TABLE skill ALTER COLUMN agent_id DROP NOT NULL;

ALTER TABLE skill ADD COLUMN IF NOT EXISTS slug           VARCHAR(128);
ALTER TABLE skill ADD COLUMN IF NOT EXISTS display_name   VARCHAR(128);
ALTER TABLE skill ADD COLUMN IF NOT EXISTS tool_ids       JSONB NOT NULL DEFAULT '[]';
ALTER TABLE skill ADD COLUMN IF NOT EXISTS mcp_server_ids JSONB NOT NULL DEFAULT '[]';
ALTER TABLE skill ADD COLUMN IF NOT EXISTS model_id       BIGINT;
ALTER TABLE skill ADD COLUMN IF NOT EXISTS scope          VARCHAR(20) NOT NULL DEFAULT 'global';
ALTER TABLE skill ADD COLUMN IF NOT EXISTS is_builtin     SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE skill ADD COLUMN IF NOT EXISTS content_hash   VARCHAR(128);
ALTER TABLE skill ADD COLUMN IF NOT EXISTS user_id        BIGINT;

CREATE UNIQUE INDEX IF NOT EXISTS uk_skill_slug ON skill (slug) WHERE slug IS NOT NULL AND deleted = 0;
CREATE INDEX IF NOT EXISTS idx_skill_is_builtin ON skill (is_builtin);
CREATE INDEX IF NOT EXISTS idx_skill_scope ON skill (scope);

COMMENT ON COLUMN skill.slug IS '全局唯一标识（英文-小写-短横线），全局 Skill 必填';
COMMENT ON COLUMN skill.display_name IS '显示名称（中文）';
COMMENT ON COLUMN skill.tool_ids IS '依赖的 Tool ID 列表（JSON 数组，字符串形式）';
COMMENT ON COLUMN skill.mcp_server_ids IS '依赖的 MCP Server ID 列表（JSON 数组，字符串形式）';
COMMENT ON COLUMN skill.model_id IS '可选的模型覆盖（保留字段，当前未启用）';
COMMENT ON COLUMN skill.scope IS '作用域：global=全局可复用；agent=旧的按 Agent 私有（兼容）';
COMMENT ON COLUMN skill.is_builtin IS '是否内置：1=是（不可编辑/删除），0=否';
COMMENT ON COLUMN skill.content_hash IS '内置 Skill 内容 hash，用于检测代码版本变化';
COMMENT ON COLUMN skill.user_id IS '创建者ID（global skill 可为空）';
