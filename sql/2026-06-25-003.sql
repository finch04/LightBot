-- 消息表新增 reply_to_message_id 字段，支持引用回复
ALTER TABLE message ADD COLUMN reply_to_message_id BIGINT;
