-- 消息反馈：记录所属 Agent 及版本快照（提交反馈时写入）
ALTER TABLE message_feedback ADD COLUMN IF NOT EXISTS agent_id BIGINT;
ALTER TABLE message_feedback ADD COLUMN IF NOT EXISTS agent_version INT;
CREATE INDEX IF NOT EXISTS idx_message_feedback_agent_id ON message_feedback (agent_id);
COMMENT ON COLUMN message_feedback.agent_id IS '所属Agent ID（反馈提交时快照）';
COMMENT ON COLUMN message_feedback.agent_version IS 'Agent版本号（反馈提交时快照，0=草稿）';
