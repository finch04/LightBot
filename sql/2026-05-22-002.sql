-- 会话表新增置顶字段
ALTER TABLE chat_session ADD COLUMN pinned BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_chat_session_pinned ON chat_session (user_id, pinned DESC, last_message_at DESC);
