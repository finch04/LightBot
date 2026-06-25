-- 合并 CUSTOM 类型到 API
UPDATE tool SET tool_type = 'API' WHERE tool_type = 'CUSTOM';

-- 删除 MCP 类型的工具（MCP 管理已独立为 McpServer 模块）
DELETE FROM tool WHERE tool_type = 'MCP';
