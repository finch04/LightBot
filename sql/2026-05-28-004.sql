-- RAG 评估基准表
CREATE TABLE eval_rag_benchmark (
    id              BIGINT        NOT NULL,
    knowledge_id    BIGINT        NOT NULL,
    name            VARCHAR(128)  NOT NULL,
    description     VARCHAR(512),
    question_count  INT           NOT NULL DEFAULT 0,
    create_time     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_eval_rag_benchmark_knowledge_id ON eval_rag_benchmark (knowledge_id);
COMMENT ON TABLE eval_rag_benchmark IS 'RAG 评估基准表';

-- RAG 评估基准题目表
CREATE TABLE eval_rag_benchmark_item (
    id              BIGINT        NOT NULL,
    benchmark_id    BIGINT        NOT NULL,
    query           VARCHAR(2000) NOT NULL,
    gold_chunk_ids  VARCHAR(2000),
    gold_answer     TEXT,
    sort_order      INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_eval_rag_benchmark_item_benchmark_id ON eval_rag_benchmark_item (benchmark_id);
COMMENT ON TABLE eval_rag_benchmark_item IS 'RAG 评估基准题目表';

-- RAG 评估结果表
CREATE TABLE eval_rag_result (
    id              BIGINT        NOT NULL,
    knowledge_id    BIGINT        NOT NULL,
    benchmark_id    BIGINT        NOT NULL,
    benchmark_name  VARCHAR(128),
    status          VARCHAR(20)   NOT NULL DEFAULT 'RUNNING',
    overall_score   DOUBLE PRECISION,
    retrieval_json  TEXT,
    answer_json     TEXT,
    config_json     TEXT,
    duration_ms     BIGINT,
    create_time     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_eval_rag_result_knowledge_id ON eval_rag_result (knowledge_id);
COMMENT ON TABLE eval_rag_result IS 'RAG 评估结果表';

-- RAG 评估结果详情表
CREATE TABLE eval_rag_result_detail (
    id                  BIGINT        NOT NULL,
    result_id           BIGINT        NOT NULL,
    query               VARCHAR(2000) NOT NULL,
    gold_chunk_ids      VARCHAR(2000),
    gold_answer         TEXT,
    generated_answer    TEXT,
    retrieved_chunk_ids VARCHAR(2000),
    retrieval_scores    TEXT,
    answer_score        DOUBLE PRECISION,
    answer_reasoning    TEXT,
    sort_order          INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_eval_rag_result_detail_result_id ON eval_rag_result_detail (result_id);
COMMENT ON TABLE eval_rag_result_detail IS 'RAG 评估结果详情表';
