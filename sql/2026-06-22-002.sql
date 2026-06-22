-- SubAgent: tools (工具名称) → tool_ids (工具ID)，与 Skill 保持一致
ALTER TABLE subagent RENAME COLUMN tools TO tool_ids;
