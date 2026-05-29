-- 问答对表
CREATE TABLE qa_pair (
    id              BIGINT          NOT NULL,
    knowledge_id    BIGINT          NOT NULL,
    question        TEXT            NOT NULL,
    answer          TEXT            NOT NULL,
    source          VARCHAR(20)     NOT NULL DEFAULT 'manual',
    status          VARCHAR(20)     NOT NULL DEFAULT 'pending',
    token_count     INTEGER         NOT NULL DEFAULT 0,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_qa_pair_knowledge_id ON qa_pair (knowledge_id);
CREATE INDEX idx_qa_pair_status ON qa_pair (status);
COMMENT ON TABLE qa_pair IS '知识库问答对';
COMMENT ON COLUMN qa_pair.source IS '来源：manual-手动创建、import-批量导入、ai-AI生成';
COMMENT ON COLUMN qa_pair.status IS '状态：pending-待向量化、vectorizing-向量化中、active-生效、failed-失败';

-- embedding 表新增 qa_pair_id 列
ALTER TABLE embedding ADD COLUMN qa_pair_id BIGINT;
COMMENT ON COLUMN embedding.qa_pair_id IS '关联问答对ID，与chunk_id互斥';
