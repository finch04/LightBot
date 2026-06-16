-- 文档在线编辑：新增 version 和 last_edit_time 字段
ALTER TABLE document ADD COLUMN version INTEGER NOT NULL DEFAULT 1;
ALTER TABLE document ADD COLUMN last_edit_time TIMESTAMP;

COMMENT ON COLUMN document.version IS '文档内容版本号，每次编辑递增';
COMMENT ON COLUMN document.last_edit_time IS '最后一次在线编辑时间';
