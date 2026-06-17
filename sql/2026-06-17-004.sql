-- 2026-06-17-004: 移除 is_system 列，toolType 新增 knowledge 类型

ALTER TABLE tool DROP COLUMN IF EXISTS is_system;
