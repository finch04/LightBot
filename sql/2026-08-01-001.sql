-- 开放 API：上层业务终端用户标识 + 外部用户长期记忆命名空间
-- chat_session.external_user_id：会话绑定的业务用户（可选）
-- user_memory.api_key_id / external_user_id：企业 Key 下按外部用户隔离的记忆

ALTER TABLE chat_session
    ADD COLUMN IF NOT EXISTS external_user_id VARCHAR(128);

COMMENT ON COLUMN chat_session.external_user_id IS '上层业务系统终端用户标识（source=api 时可选，用于跨会话记忆）';

CREATE INDEX IF NOT EXISTS idx_chat_session_api_external
    ON chat_session (api_key_id, external_user_id)
    WHERE external_user_id IS NOT NULL;

ALTER TABLE user_memory
    ADD COLUMN IF NOT EXISTS api_key_id BIGINT;

ALTER TABLE user_memory
    ADD COLUMN IF NOT EXISTS external_user_id VARCHAR(128);

COMMENT ON COLUMN user_memory.api_key_id IS '企业 API Key ID（开放 API 外部用户记忆；平台调试记忆为空）';
COMMENT ON COLUMN user_memory.external_user_id IS '上层业务终端用户标识（与 api_key_id 组成记忆命名空间）';

CREATE INDEX IF NOT EXISTS idx_user_memory_api_external
    ON user_memory (api_key_id, external_user_id, status)
    WHERE api_key_id IS NOT NULL AND external_user_id IS NOT NULL;
