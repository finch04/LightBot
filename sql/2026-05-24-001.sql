-- Tool表新增is_system字段，区分系统工具
-- 系统工具：核心内置工具，自动注入所有Agent，用户无法修改/删除
-- 普通工具：用户可选绑定的工具

ALTER TABLE tool ADD COLUMN is_system BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN tool.is_system IS '是否系统工具：true=系统工具（自动注入，不可编辑删除），false=普通工具';

-- 标记现有内置工具中的系统工具
-- query_knowledge 为知识库检索核心工具，标记为系统工具
UPDATE tool SET is_system = TRUE WHERE name = 'query_knowledge';

-- 创建索引（可选，用于快速过滤系统工具）
CREATE INDEX idx_tool_is_system ON tool (is_system);