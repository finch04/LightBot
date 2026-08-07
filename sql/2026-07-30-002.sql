-- 智能问数：语义层 Dataset（分析配置）
-- dimensions / metrics / profile 存 JSONB；跨数据集关联见 ask_relation

CREATE TABLE IF NOT EXISTS ask_dataset (
    id                   BIGINT        NOT NULL,
    data_model_id        BIGINT        NOT NULL,
    code                 VARCHAR(64)   NOT NULL,
    name                 VARCHAR(128)  NOT NULL,
    description          VARCHAR(512),
    default_time_field   VARCHAR(64),
    default_filters      JSONB         NOT NULL DEFAULT '{}'::jsonb,
    sensitive_fields     JSONB         NOT NULL DEFAULT '[]'::jsonb,
    dimensions           JSONB         NOT NULL DEFAULT '[]'::jsonb,
    metrics              JSONB         NOT NULL DEFAULT '[]'::jsonb,
    profile_json         JSONB         NOT NULL DEFAULT '{}'::jsonb,
    create_time          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted              SMALLINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_ask_dataset_model
    ON ask_dataset (data_model_id) WHERE deleted = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uk_ask_dataset_code
    ON ask_dataset (code) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_ask_dataset_name ON ask_dataset (name);

COMMENT ON TABLE ask_dataset IS '智能问数分析数据集（绑定 data_model）';
COMMENT ON COLUMN ask_dataset.code IS '稳定编码，供 Intent IR / Tool 引用';
COMMENT ON COLUMN ask_dataset.default_time_field IS '默认时间维度字段 key（如 createTime）';
COMMENT ON COLUMN ask_dataset.default_filters IS '默认过滤 JSON 对象';
COMMENT ON COLUMN ask_dataset.sensitive_fields IS '敏感字段 key 列表 JSON 数组';
COMMENT ON COLUMN ask_dataset.dimensions IS '维度定义 JSON 数组';
COMMENT ON COLUMN ask_dataset.metrics IS '指标定义 JSON 数组';
COMMENT ON COLUMN ask_dataset.profile_json IS '字段画像 JSON';

CREATE TABLE IF NOT EXISTS ask_relation (
    id               BIGINT        NOT NULL,
    name             VARCHAR(128),
    from_dataset_id  BIGINT        NOT NULL,
    from_field       VARCHAR(64)   NOT NULL,
    to_dataset_id    BIGINT        NOT NULL,
    to_field         VARCHAR(64)   NOT NULL,
    create_time      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted          SMALLINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_ask_relation_from ON ask_relation (from_dataset_id) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_ask_relation_to ON ask_relation (to_dataset_id) WHERE deleted = 0;

COMMENT ON TABLE ask_relation IS '智能问数跨数据集白名单关联';
