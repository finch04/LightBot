-- Agent-知识库关联表
-- 支持 Agent 绑定多个知识库（一对多关系）
CREATE TABLE agent_knowledge (
    id              BIGINT          NOT NULL,
    agent_id        BIGINT          NOT NULL,
    knowledge_id    BIGINT          NOT NULL,
    create_time     TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_agent_knowledge ON agent_knowledge (agent_id, knowledge_id);
CREATE INDEX idx_agent_knowledge_agent_id ON agent_knowledge (agent_id);
CREATE INDEX idx_agent_knowledge_knowledge_id ON agent_knowledge (knowledge_id);

COMMENT ON TABLE agent_knowledge IS 'Agent-知识库关联表';
COMMENT ON COLUMN agent_knowledge.id IS '主键ID';
COMMENT ON COLUMN agent_knowledge.agent_id IS 'Agent ID';
COMMENT ON COLUMN agent_knowledge.knowledge_id IS '知识库 ID';
COMMENT ON COLUMN agent_knowledge.create_time IS '创建时间';

-- Agent 表新增模型配置字段
ALTER TABLE agent ADD COLUMN model_id VARCHAR(128);
ALTER TABLE agent ADD COLUMN temperature NUMERIC(3,2) DEFAULT 0.7;
ALTER TABLE agent ADD COLUMN top_p NUMERIC(3,2) DEFAULT 0.9;
ALTER TABLE agent ADD COLUMN max_tokens INTEGER DEFAULT 2048;
ALTER TABLE agent ADD COLUMN presence_penalty NUMERIC(3,2) DEFAULT 0.0;
ALTER TABLE agent ADD COLUMN frequency_penalty NUMERIC(3,2) DEFAULT 0.0;

COMMENT ON COLUMN agent.model_id IS '模型ID（如 gpt-4、qwen-turbo）';
COMMENT ON COLUMN agent.temperature IS '温度参数，控制随机性（0.0-2.0）';
COMMENT ON COLUMN agent.top_p IS '核采样参数（0.0-1.0）';
COMMENT ON COLUMN agent.max_tokens IS '最大输出 Token 数';
COMMENT ON COLUMN agent.presence_penalty IS '存在惩罚（-2.0-2.0）';
COMMENT ON COLUMN agent.frequency_penalty IS '频率惩罚（-2.0-2.0）';
