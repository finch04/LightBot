package com.lightbot.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lightbot.dto.ToolRequest;
import com.lightbot.entity.Tool;

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

    void deleteById(Long id);
}
