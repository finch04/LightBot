package com.lightbot.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lightbot.dto.McpServerRequest;
import com.lightbot.entity.McpServer;

/**
 * MCP Server 服务接口
 *
 * @author finch
 * @since 2026-05-20
 */
public interface McpServerService extends IService<McpServer> {

    /**
     * 创建 MCP Server
     *
     * @param request 创建请求
     * @return MCP Server
     */
    McpServer create(McpServerRequest request);

    /**
     * 更新 MCP Server
     *
     * @param request 更新请求
     * @return MCP Server
     */
    McpServer update(McpServerRequest request);

    /**
     * 分页查询
     *
     * @param pageNum  页码
     * @param pageSize 每页数量
     * @return 分页结果
     */
    Page<McpServer> listPage(int pageNum, int pageSize, String name);

    /**
     * 删除 MCP Server
     *
     * @param id 主键ID
     */
    void deleteById(Long id);

    /**
     * 启用/禁用 MCP Server
     *
     * @param id      主键ID
     * @param enabled true启用，false禁用
     */
    void setEnabled(Long id, boolean enabled);
}
