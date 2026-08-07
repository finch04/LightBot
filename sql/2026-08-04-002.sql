-- 业务页改为开发者元数据注册：取消平台内置种子标记
-- 平台不再启动同步内置 pageType；已有数据保留，仅将 builtin 降为普通开发者页

UPDATE business_page
SET builtin = 0,
    update_time = CURRENT_TIMESTAMP
WHERE builtin = 1
  AND deleted = 0;

COMMENT ON COLUMN business_page.builtin IS '兼容字段；平台不再使用内置种子（新建恒为 0）';
COMMENT ON COLUMN business_page.form_schema IS '开发者注册的通用表单 schema（控制台默认渲染）';
