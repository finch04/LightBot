-- 数据中心：分类 + 数据模型元数据（物理数据池表按模型动态创建，前缀 sjc_data_）

CREATE TABLE IF NOT EXISTS data_model_category (
    id          BIGINT       NOT NULL,
    user_id     BIGINT       NOT NULL,
    name        VARCHAR(64)  NOT NULL,
    sort_order  INT          NOT NULL DEFAULT 0,
    create_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     SMALLINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_data_model_category_user ON data_model_category (user_id);
COMMENT ON TABLE data_model_category IS '数据模型分类';
COMMENT ON COLUMN data_model_category.user_id IS '所属用户';
COMMENT ON COLUMN data_model_category.name IS '分类名称';
COMMENT ON COLUMN data_model_category.sort_order IS '排序';

CREATE TABLE IF NOT EXISTS data_model (
    id          BIGINT       NOT NULL,
    user_id     BIGINT       NOT NULL,
    category_id BIGINT       NOT NULL,
    name        VARCHAR(128) NOT NULL,
    table_name  VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    schema_json JSONB        NOT NULL DEFAULT '{}'::jsonb,
    create_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     SMALLINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_data_model_table_name ON data_model (table_name) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_data_model_user ON data_model (user_id);
CREATE INDEX IF NOT EXISTS idx_data_model_category ON data_model (category_id);
COMMENT ON TABLE data_model IS '数据模型元数据（schema_json 存字段/搜索/唯一/索引配置）';
COMMENT ON COLUMN data_model.table_name IS '物理表名，固定前缀 sjc_data_';
COMMENT ON COLUMN data_model.schema_json IS '表单与约束配置 JSON';
