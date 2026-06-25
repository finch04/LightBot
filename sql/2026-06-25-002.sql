-- chat_session 表新增 agent_version_id 列，记录会话最近使用的 Agent 版本快照 ID
-- 使用 agent_version.id（主键）而非 version 编号，避免版本删除后编号复用导致误匹配
ALTER TABLE chat_session ADD COLUMN agent_version_id BIGINT;
COMMENT ON COLUMN chat_session.agent_version_id IS '最近使用的Agent版本快照ID（agent_version.id），null=未指定';
