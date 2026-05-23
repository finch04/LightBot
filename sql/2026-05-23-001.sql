-- LLM调用链追踪表
CREATE TABLE llm_trace (
    id              BIGINT          NOT NULL,
    request_id      VARCHAR(64)     NOT NULL,
    session_id      BIGINT,
    user_id         BIGINT,
    agent_id        BIGINT,
    agent_name      VARCHAR(128),
    model           VARCHAR(128),
    status          VARCHAR(20)     NOT NULL DEFAULT 'running',
    input_tokens    INT             NOT NULL DEFAULT 0,
    output_tokens   INT             NOT NULL DEFAULT 0,
    total_tokens    INT             NOT NULL DEFAULT 0,
    tool_call_count INT             NOT NULL DEFAULT 0,
    total_duration_ms BIGINT        NOT NULL DEFAULT 0,
    spans           JSONB           DEFAULT '[]',
    error_message   TEXT,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX idx_llm_trace_request_id ON llm_trace (request_id);
CREATE INDEX idx_llm_trace_session_id ON llm_trace (session_id);
CREATE INDEX idx_llm_trace_user_id ON llm_trace (user_id);
CREATE INDEX idx_llm_trace_create_time ON llm_trace (create_time);
CREATE INDEX idx_llm_trace_status ON llm_trace (status);
COMMENT ON TABLE llm_trace IS 'LLM调用链追踪表';
COMMENT ON COLUMN llm_trace.request_id IS '请求ID（唯一标识一次AI对话）';
COMMENT ON COLUMN llm_trace.session_id IS '会话ID';
COMMENT ON COLUMN llm_trace.user_id IS '用户ID';
COMMENT ON COLUMN llm_trace.agent_id IS 'AgentID';
COMMENT ON COLUMN llm_trace.agent_name IS 'Agent名称';
COMMENT ON COLUMN llm_trace.model IS '模型标识';
COMMENT ON COLUMN llm_trace.status IS '状态: running/completed/failed';
COMMENT ON COLUMN llm_trace.input_tokens IS '输入Token数';
COMMENT ON COLUMN llm_trace.output_tokens IS '输出Token数';
COMMENT ON COLUMN llm_trace.total_tokens IS '总Token数';
COMMENT ON COLUMN llm_trace.tool_call_count IS '工具调用次数';
COMMENT ON COLUMN llm_trace.total_duration_ms IS '总耗时（毫秒）';
COMMENT ON COLUMN llm_trace.spans IS '调用链Span列表（JSONB）';
COMMENT ON COLUMN llm_trace.error_message IS '错误信息';
