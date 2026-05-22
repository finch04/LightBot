-- 1. 创建 tool_calls 表（记录工具调用详情）
CREATE TABLE tool_calls (
    id              BIGINT          NOT NULL,
    message_id      BIGINT          NOT NULL,
    tool_call_id    VARCHAR(128),
    tool_name       VARCHAR(100)    NOT NULL,
    tool_input      JSONB,
    tool_output     TEXT,
    status          VARCHAR(20)     NOT NULL DEFAULT 'pending',
    error_message   TEXT,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX ix_tool_calls_message_id ON tool_calls (message_id);
CREATE INDEX ix_tool_calls_tool_call_id ON tool_calls (tool_call_id);
COMMENT ON TABLE tool_calls IS '工具调用记录表';
COMMENT ON COLUMN tool_calls.message_id IS '关联消息ID';
COMMENT ON COLUMN tool_calls.tool_call_id IS '工具调用ID（用于关联）';
COMMENT ON COLUMN tool_calls.tool_name IS '工具名称';
COMMENT ON COLUMN tool_calls.tool_input IS '工具输入参数';
COMMENT ON COLUMN tool_calls.tool_output IS '工具执行结果';
COMMENT ON COLUMN tool_calls.status IS '状态: pending/success/error';
COMMENT ON COLUMN tool_calls.error_message IS '错误信息';

-- 2. 迁移 agent_knowledge 数据到 agent.config JSONB
-- 将每个 agent 的绑定知识库 ID 迁移到 config.knowledges 数组
UPDATE agent SET config = jsonb_set(
    COALESCE(config, '{}'::jsonb),
    '{knowledges}',
    (
        SELECT COALESCE(jsonb_agg(ak.knowledge_id::text), '[]'::jsonb)
        FROM agent_knowledge ak
        WHERE ak.agent_id = agent.id
    )
)
WHERE id IN (SELECT DISTINCT agent_id FROM agent_knowledge);

-- 3. 删除 agent_knowledge 关联表
DROP TABLE IF EXISTS agent_knowledge;
