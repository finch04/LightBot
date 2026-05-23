-- 新增 llm_trace.reply_content 字段，存储AI完整回复内容
ALTER TABLE llm_trace ADD COLUMN reply_content TEXT;
COMMENT ON COLUMN llm_trace.reply_content IS 'AI完整回复内容';
