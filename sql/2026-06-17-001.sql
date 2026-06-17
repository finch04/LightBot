-- 文档版本控制表
CREATE TABLE document_version (
    id              BIGINT          NOT NULL,
    document_id     BIGINT          NOT NULL,
    version         INTEGER         NOT NULL,
    content_hash    VARCHAR(64),
    storage_path    VARCHAR(512)    NOT NULL,
    created_by      BIGINT,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX idx_doc_version_doc ON document_version (document_id, version DESC);
COMMENT ON TABLE document_version IS '文档版本历史';
