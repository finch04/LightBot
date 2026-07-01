-- chat_session 新增 attachments JSONB：会话级附件索引（用户上传 / AI 生图 / 沙盒 / 交付）
ALTER TABLE chat_session ADD COLUMN IF NOT EXISTS attachments JSONB;
COMMENT ON COLUMN chat_session.attachments IS '会话附件索引 JSON 数组（source: user_upload|ai_image|ai_sandbox|ai_deliver）';

-- 从 context.attachments 迁移历史数据
UPDATE chat_session
SET attachments = context::jsonb -> 'attachments'
WHERE attachments IS NULL
  AND context IS NOT NULL
  AND context::jsonb ? 'attachments'
  AND jsonb_typeof(context::jsonb -> 'attachments') = 'array';
