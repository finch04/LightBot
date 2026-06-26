-- 消息收藏功能：message 表新增 starred 字段
ALTER TABLE message ADD COLUMN starred BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_message_starred ON message (starred) WHERE starred = TRUE;
COMMENT ON COLUMN message.starred IS '是否收藏';
