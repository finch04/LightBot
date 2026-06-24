-- Message 表重构：移除废弃字段，新增 message_type 字段
-- 1. 删除 tool_calls 和 tool_call_id 列（从未使用）
-- 2. 新增 message_type 列（text / multimodal_image）

ALTER TABLE message DROP COLUMN IF EXISTS tool_calls;
ALTER TABLE message DROP COLUMN IF EXISTS tool_call_id;

ALTER TABLE message ADD COLUMN IF NOT EXISTS message_type VARCHAR(32) NOT NULL DEFAULT 'text';

COMMENT ON COLUMN message.message_type IS '消息类型：text-文本, multimodal_image-多模态图片';
