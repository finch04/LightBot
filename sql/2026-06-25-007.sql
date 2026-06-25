-- MCP Server 新增最后同步时间字段
ALTER TABLE mcp_server ADD COLUMN last_sync_time TIMESTAMP;
COMMENT ON COLUMN mcp_server.last_sync_time IS '最后一次工具列表同步时间';
