package com.lightbot.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lightbot.dto.ToolRequest;
import com.lightbot.entity.Tool;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Map;

/**
 * Tool 服务接口
 *
 * @author finch
 * @since 2026-05-20
 */
public interface ToolService extends IService<Tool> {

    Tool create(ToolRequest request);

    Tool update(ToolRequest request);

    Page<Tool> listPage(int pageNum, int pageSize, String name);

    Page<Tool> listTools(int pageNum, int pageSize, String toolType);

    Page<Tool> listToolsWithFilter(int pageNum, int pageSize, String keyword, String toolType, String tag);

    void deleteById(Long id);

    /**
     * 启用/禁用 Tool
     *
     * @param id      主键ID
     * @param enabled true启用，false禁用
     */
    void setEnabled(Long id, boolean enabled);

    /**
     * 按工具名列表解析为 Spring AI ToolCallback
     *
     * @param toolNames 工具名列表
     * @return ToolCallback 列表
     */
    List<ToolCallback> resolveToolCallbacks(List<String> toolNames);

    /**
     * 按工具ID列表解析为 Spring AI ToolCallback
     *
     * @param toolIds 工具ID列表
     * @return ToolCallback 列表
     */
    List<ToolCallback> resolveToolCallbacksByIds(List<Long> toolIds);

    /**
     * 测试执行单个工具
     *
     * @param toolId 工具ID
     * @param args   JSON格式的工具参数
     * @return 执行结果
     */
    String testTool(Long toolId, String args);

    /**
     * 获取工具示例参数
     *
     * @param toolId 工具ID
     * @return 示例参数Map
     */
    Map<String, Object> getExampleParams(Long toolId);
}
