package com.lightbot.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lightbot.entity.AgentKnowledge;

import java.util.List;

/**
 * Agent-知识库关联 Service
 *
 * @author finch
 * @since 2026-05-19
 */
public interface AgentKnowledgeService extends IService<AgentKnowledge> {

    /**
     * 绑定知识库到 Agent
     *
     * @param agentId     Agent ID
     * @param knowledgeId 知识库 ID
     */
    void bindKnowledge(Long agentId, Long knowledgeId);

    /**
     * 解绑知识库
     *
     * @param agentId     Agent ID
     * @param knowledgeId 知识库 ID
     */
    void unbindKnowledge(Long agentId, Long knowledgeId);

    /**
     * 获取 Agent 绑定的知识库 ID 列表
     *
     * @param agentId Agent ID
     * @return 知识库 ID 列表
     */
    List<Long> getKnowledgeIds(Long agentId);

    /**
     * 批量更新 Agent 的知识库绑定
     *
     * @param agentId       Agent ID
     * @param knowledgeIds  知识库 ID 列表
     */
    void updateKnowledgeBindings(Long agentId, List<Long> knowledgeIds);
}
