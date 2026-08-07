-- 行政区划地区库：支持问数租户隔离「本级及下级」展开
-- 上下级通过 code / parent_code 递归匹配（不含 path）

CREATE TABLE IF NOT EXISTS region (
    id          BIGINT       NOT NULL,
    code        VARCHAR(32)  NOT NULL,
    name        VARCHAR(64)  NOT NULL,
    parent_code VARCHAR(32),
    level       SMALLINT     NOT NULL,
    create_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     SMALLINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_region_code ON region (code) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_region_parent_code ON region (parent_code) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_region_name ON region (name) WHERE deleted = 0;

COMMENT ON TABLE region IS '行政区划地区库';
COMMENT ON COLUMN region.code IS '区划编码，国标6位（省xx0000/市xxxx00/区xxxxxx；与 callerContext.regionId 对齐）';
COMMENT ON COLUMN region.parent_code IS '上级区划编码（6位），省级为空';
COMMENT ON COLUMN region.level IS '层级：1省 2市 3区县';
