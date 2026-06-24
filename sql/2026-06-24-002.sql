-- 删除 Skill 表废弃字段 tool_id（已被 tool_ids JSONB 数组取代）
ALTER TABLE skill DROP COLUMN IF EXISTS tool_id;
DROP INDEX IF EXISTS idx_skill_tool_id;
