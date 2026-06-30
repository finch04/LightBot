-- llm_trace 新增 display_content：用户对话页可见正文（剥离 thinking 后）
ALTER TABLE llm_trace ADD COLUMN IF NOT EXISTS display_content TEXT;
COMMENT ON COLUMN llm_trace.reply_content IS 'AI完整回复内容（模型原始输出，含深度思考标签，不做删改）';
COMMENT ON COLUMN llm_trace.display_content IS '最终展示内容（用户对话页可见正文，已剥离思考标签）';
