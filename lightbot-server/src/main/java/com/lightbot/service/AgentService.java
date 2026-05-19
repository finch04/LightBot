package com.lightbot.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lightbot.entity.Agent;

/**
 * Agent服务接口
 *
 * @author finch
 * @since 2026-05-19
 */
public interface AgentService extends IService<Agent> {

    /**
     * 创建Agent
     *
     * @param agent Agent信息
     * @return Agent
     */
    Agent create(Agent agent);

    /**
     * 更新Agent
     *
     * @param agent Agent信息
     * @return Agent
     */
    Agent update(Agent agent);

    /**
     * 分页查询当前用户的Agent列表
     *
     * @param pageNum  页码
     * @param pageSize 每页数量
     * @return 分页结果
     */
    Page<Agent> listMyAgents(int pageNum, int pageSize);

    /**
     * 删除Agent（逻辑删除）
     *
     * @param id 主键ID
     */
    void deleteById(Long id);
}
