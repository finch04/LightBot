-- Agent表新增欢迎语和推荐问题字段
ALTER TABLE agent ADD COLUMN welcome_message TEXT;
ALTER TABLE agent ADD COLUMN recommended_questions JSONB;
COMMENT ON COLUMN agent.welcome_message IS '欢迎语';
COMMENT ON COLUMN agent.recommended_questions IS '推荐问题列表';
