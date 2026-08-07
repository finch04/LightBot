-- 企业版：API 集成会话归属（source/api_key_id）
-- chat_session 增加来源与企业 API Key 关联，平台调试会话与 API 会话分离

ALTER TABLE chat_session
    ADD COLUMN IF NOT EXISTS source VARCHAR(20) NOT NULL DEFAULT 'platform';

ALTER TABLE chat_session
    ADD COLUMN IF NOT EXISTS api_key_id BIGINT NULL;

COMMENT ON COLUMN chat_session.source IS '会话来源：platform=平台调试，api=企业API Key集成，automation=自动化';
COMMENT ON COLUMN chat_session.api_key_id IS '企业 API Key ID（source=api 时有值）';

CREATE INDEX IF NOT EXISTS idx_chat_session_source ON chat_session (source);
CREATE INDEX IF NOT EXISTS idx_chat_session_api_key_id ON chat_session (api_key_id);
