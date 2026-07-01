-- 删除 tool_calls.tool_call_id（从未写入/查询，与 message.tool_call_id 同期废弃）
DROP INDEX IF EXISTS ix_tool_calls_tool_call_id;
ALTER TABLE tool_calls DROP COLUMN IF EXISTS tool_call_id;
