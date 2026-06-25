-- RAG 检索反馈表：用户对 RAG 引用的有用/无用反馈
CREATE TABLE rag_feedback (
    id              BIGINT          NOT NULL,
    message_id      BIGINT          NOT NULL,
    user_id         BIGINT          NOT NULL,
    chunk_id        BIGINT,
    qa_pair_id      BIGINT,
    source_type     VARCHAR(20)     NOT NULL,
    feedback_type   VARCHAR(20)     NOT NULL,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX idx_rag_feedback_message ON rag_feedback (message_id);
CREATE INDEX idx_rag_feedback_user ON rag_feedback (user_id);
COMMENT ON TABLE rag_feedback IS 'RAG检索反馈表';
