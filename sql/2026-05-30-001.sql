-- ========================================
-- 知识图谱独立表：knowledge_graph + graph_document
-- 替代 graph_extraction_task 表
-- ========================================

-- 1. 知识图谱表（知识库级别，1:1）
CREATE TABLE knowledge_graph (
    id              BIGINT          NOT NULL,
    knowledge_id    BIGINT          NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'pending',
    node_count      INTEGER         NOT NULL DEFAULT 0,
    edge_count      INTEGER         NOT NULL DEFAULT 0,
    task_id         BIGINT,
    error_message   TEXT,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_knowledge_graph_knowledge_id ON knowledge_graph (knowledge_id) WHERE deleted = 0;
COMMENT ON TABLE knowledge_graph IS '知识图谱（知识库级别）';
COMMENT ON COLUMN knowledge_graph.status IS '状态：pending-待处理、running-执行中、completed-已完成、failed-失败';
COMMENT ON COLUMN knowledge_graph.task_id IS '当前正在运行的异步任务ID';

-- 2. 图谱文档关联表（记录每个文档的抽取状态）
CREATE TABLE graph_document (
    id              BIGINT          NOT NULL,
    graph_id        BIGINT          NOT NULL,
    document_id     BIGINT          NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'pending',
    entity_count    INTEGER         NOT NULL DEFAULT 0,
    relation_count  INTEGER         NOT NULL DEFAULT 0,
    error_message   TEXT,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_graph_document_graph_id ON graph_document (graph_id);
CREATE UNIQUE INDEX uk_graph_document_graph_doc ON graph_document (graph_id, document_id) WHERE deleted = 0;
COMMENT ON TABLE graph_document IS '图谱文档关联（记录每个文档的图谱抽取状态）';
COMMENT ON COLUMN graph_document.status IS '状态：pending-待处理、running-执行中、completed-已完成、failed-失败';

-- 3. 迁移旧数据（graph_extraction_task → knowledge_graph + graph_document）
-- 3.1 为每个知识库创建一条 knowledge_graph 记录（取最新的完成状态）
-- 注意：ID 使用大数+行号生成，实际应通过应用层 MyBatis-Plus 雪花算法分配
INSERT INTO knowledge_graph (id, knowledge_id, status, create_time, update_time, deleted)
SELECT
    9000000000000000000 + ROW_NUMBER() OVER (ORDER BY knowledge_id),
    knowledge_id,
    CASE
        WHEN bool_or(status = 'running') THEN 'running'
        WHEN bool_or(status = 'pending') THEN 'pending'
        WHEN bool_and(status = 'completed') THEN 'completed'
        WHEN bool_and(status = 'failed') THEN 'failed'
        ELSE 'completed'
    END,
    MIN(create_time),
    MAX(update_time),
    0
FROM graph_extraction_task
WHERE deleted = 0
GROUP BY knowledge_id;

-- 3.2 迁移有 document_id 的记录到 graph_document
INSERT INTO graph_document (id, graph_id, document_id, status, entity_count, relation_count, error_message, create_time, update_time, deleted)
SELECT
    9100000000000000000 + ROW_NUMBER() OVER (ORDER BY get.id),
    kg.id,
    get.document_id,
    get.status,
    get.entity_count,
    get.relation_count,
    get.error_message,
    get.create_time,
    get.update_time,
    0
FROM graph_extraction_task get
JOIN knowledge_graph kg ON kg.knowledge_id = get.knowledge_id AND kg.deleted = 0
WHERE get.deleted = 0 AND get.document_id IS NOT NULL;

-- 3.3 更新 knowledge_graph 的 node_count / edge_count（从知识库表同步）
UPDATE knowledge_graph kg
SET node_count = COALESCE(k.node_count, 0),
    edge_count = COALESCE(k.edge_count, 0)
FROM knowledge k
WHERE kg.knowledge_id = k.id AND kg.deleted = 0;

-- 4. 旧表保留不删除（兼容回滚），后续可手动 DROP TABLE graph_extraction_task;
