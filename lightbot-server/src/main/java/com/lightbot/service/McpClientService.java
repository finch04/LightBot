package com.lightbot.service;

import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

/**
 * MCP 客户端服务：连接管理、工具发现、工具转回调
 *
 * @author finch
 * @since 2026-05-23
 */
public interface McpClientService {

    /**
     * 测试连接并发现工具
     *
     * @param mcpServerId MCP Server ID
     * @return 发现的工具列表
     */
    List<McpSchema.Tool> testConnection(Long mcpServerId);

    /**
     * 获取 MCP Server 的工具回调（用于注入 Agent 工具列表）
     * <p>内部处理连接、发现、转换，结果按 server 缓存</p>
     *
     * @param mcpServerId MCP Server ID
     * @return ToolCallback 列表（已过滤 disabled_tools）
     */
    List<ToolCallback> getToolCallbacks(Long mcpServerId);

    /**
     * 获取 MCP Server 的所有工具回调（不过滤 disabled_tools）
     *
     * @param mcpServerId MCP Server ID
     * @return ToolCallback 列表（全量）
     */
    List<ToolCallback> getAllToolCallbacks(Long mcpServerId);

    /**
     * 清除指定 MCP Server 的缓存
     *
     * @param mcpServerId MCP Server ID
     */
    void clearCache(Long mcpServerId);

    /**
     * 获取工具列表（优先从Redis缓存读取）
     * <p>首次获取会缓存到Redis，刷新按钮点击后才清除缓存</p>
     *
     * @param mcpServerId MCP Server ID
     * @return 工具列表（从缓存或运行时获取）
     */
    List<McpSchema.Tool> getToolsWithCache(Long mcpServerId);

    /**
     * 刷新指定 MCP Server 的工具缓存（清除后重新加载）
     *
     * @param mcpServerId MCP Server ID
     * @return 刷新后的工具数量，失败返回 -1
     */
    int refreshServer(Long mcpServerId);
}
