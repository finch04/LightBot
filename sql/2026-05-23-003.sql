-- MCP Server 表扩展：支持 MCP 协议连接
ALTER TABLE mcp_server ADD COLUMN transport VARCHAR(20) NOT NULL DEFAULT 'sse';
ALTER TABLE mcp_server ADD COLUMN headers JSONB;
ALTER TABLE mcp_server ADD COLUMN disabled_tools JSONB;
COMMENT ON COLUMN mcp_server.transport IS '传输类型: sse, stdio, streamable_http';
COMMENT ON COLUMN mcp_server.headers IS 'HTTP请求头(JSONB)，用于SSE/Streamable HTTP认证';
COMMENT ON COLUMN mcp_server.disabled_tools IS '禁用的工具名列表(JSONB数组)';
