-- 若已执行含 path 的旧版 2026-08-05-002，删除 path 列（上下级改由 code/parent_code 递归）

DROP INDEX IF EXISTS idx_region_path;

ALTER TABLE region DROP COLUMN IF EXISTS path;
