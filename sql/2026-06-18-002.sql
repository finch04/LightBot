-- Skill 表扩展：文件化存储 + 依赖声明 + 来源类型

ALTER TABLE skill ADD COLUMN object_prefix VARCHAR(256);
ALTER TABLE skill ADD COLUMN version VARCHAR(64) DEFAULT '1.0.0';
ALTER TABLE skill ADD COLUMN skill_dependencies JSONB DEFAULT '[]';
ALTER TABLE skill ADD COLUMN source_type VARCHAR(20) DEFAULT 'builtin';

COMMENT ON COLUMN skill.object_prefix IS 'MinIO 路径前缀，如 skills/{slug}/';
COMMENT ON COLUMN skill.version IS '语义版本号';
COMMENT ON COLUMN skill.skill_dependencies IS '依赖其他 Skill 的 slug 列表';
COMMENT ON COLUMN skill.source_type IS '来源类型: builtin/upload/remote';

CREATE INDEX idx_skill_source_type ON skill(source_type);
