-- 知识图谱功能：knowledge 表新增字段 + 新建 graph_extraction_task 表

-- 1. knowledge 表新增图谱相关字段
ALTER TABLE knowledge ADD COLUMN IF NOT EXISTS graph_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE knowledge ADD COLUMN IF NOT EXISTS node_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE knowledge ADD COLUMN IF NOT EXISTS edge_count INTEGER NOT NULL DEFAULT 0;

COMMENT ON COLUMN knowledge.graph_enabled IS '是否启用知识图谱';
COMMENT ON COLUMN knowledge.node_count IS '图谱节点数';
COMMENT ON COLUMN knowledge.edge_count IS '图谱边数';

-- 2. 图谱抽取任务表
CREATE TABLE IF NOT EXISTS graph_extraction_task (
    id              BIGINT          NOT NULL,
    knowledge_id    BIGINT          NOT NULL,
    document_id     BIGINT,
    status          VARCHAR(20)     NOT NULL DEFAULT 'pending',
    source          VARCHAR(20)     NOT NULL DEFAULT 'auto',
    entity_count    INTEGER         NOT NULL DEFAULT 0,
    relation_count  INTEGER         NOT NULL DEFAULT 0,
    error_message   TEXT,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_graph_task_knowledge_id ON graph_extraction_task (knowledge_id);
CREATE INDEX IF NOT EXISTS idx_graph_task_status ON graph_extraction_task (status);
COMMENT ON TABLE graph_extraction_task IS '图谱抽取任务';
COMMENT ON COLUMN graph_extraction_task.source IS '来源：auto-自动抽取、import-手动导入';
COMMENT ON COLUMN graph_extraction_task.status IS '状态：pending-待处理、running-执行中、completed-已完成、failed-失败';
