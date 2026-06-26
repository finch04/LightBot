-- 消息反馈表：用户对 AI 回复消息的点赞/踩反馈
CREATE TABLE message_feedback (
    id              BIGINT          NOT NULL,
    message_id      BIGINT          NOT NULL,
    user_id         BIGINT          NOT NULL,
    rating          VARCHAR(10)     NOT NULL,
    reason          TEXT,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

-- 唯一索引：每人每条消息只能有一条反馈
CREATE UNIQUE INDEX uk_message_feedback_user_message ON message_feedback (user_id, message_id);
CREATE INDEX idx_message_feedback_message_id ON message_feedback (message_id);

COMMENT ON TABLE message_feedback IS '消息反馈表';
COMMENT ON COLUMN message_feedback.id IS '主键ID';
COMMENT ON COLUMN message_feedback.message_id IS '消息ID';
COMMENT ON COLUMN message_feedback.user_id IS '用户ID';
COMMENT ON COLUMN message_feedback.rating IS '评分：like/dislike';
COMMENT ON COLUMN message_feedback.reason IS '反馈原因（dislike时可选填写）';
COMMENT ON COLUMN message_feedback.create_time IS '创建时间';
