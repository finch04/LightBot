package com.lightbot.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lightbot.common.BizException;
import com.lightbot.entity.Agent;
import com.lightbot.enums.AgentStatus;
import com.lightbot.enums.ErrorCode;
import com.lightbot.mapper.AgentMapper;
import com.lightbot.service.AgentKnowledgeService;
import com.lightbot.service.AgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent服务实现类
 *
 * @author finch
 * @since 2026-05-19
 */
@Service
@RequiredArgsConstructor
public class AgentServiceImpl extends ServiceImpl<AgentMapper, Agent>
        implements AgentService {

    private final AgentKnowledgeService agentKnowledgeService;

    @Override
    public Agent create(Agent agent) {
        // 1. 获取当前用户ID
        long userId = StpUtil.getLoginIdAsLong();

        // 2. 初始化Agent字段
        agent.setUserId(userId);
        agent.setStatus(AgentStatus.DRAFT);
        agent.setVersion(1);
        save(agent);
        return agent;
    }

    @Override
    public Agent update(Agent agent) {
        // 1. 校验存在性
        Agent existing = getById(agent.getId());
        if (existing == null) {
            throw new BizException(ErrorCode.AGENT_NOT_FOUND);
        }

        // 2. 更新允许修改的字段
        existing.setName(agent.getName());
        existing.setDescription(agent.getDescription());
        existing.setSystemPrompt(agent.getSystemPrompt());
        existing.setAvatar(agent.getAvatar());
        existing.setAgentType(agent.getAgentType());
        existing.setConfig(agent.getConfig());
        // 3. 更新模型配置字段
        existing.setModelId(agent.getModelId());
        existing.setTemperature(agent.getTemperature());
        existing.setTopP(agent.getTopP());
        existing.setMaxTokens(agent.getMaxTokens());
        existing.setRepetitionPenalty(agent.getRepetitionPenalty());
        updateById(existing);
        return existing;
    }

    @Override
    public Page<Agent> listMyAgents(int pageNum, int pageSize) {
        long userId = StpUtil.getLoginIdAsLong();
        return page(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Agent>()
                        .eq(Agent::getUserId, userId)
                        .orderByDesc(Agent::getCreateTime));
    }

    @Override
    public Map<String, Object> getAgentDetail(Long id) {
        // 1. 获取 Agent 信息
        Agent agent = getById(id);
        if (agent == null) {
            throw new BizException(ErrorCode.AGENT_NOT_FOUND);
        }

        // 2. 获取绑定的知识库 ID 列表
        List<Long> knowledgeIds = agentKnowledgeService.getKnowledgeIds(id);

        // 3. 组装返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("agent", agent);
        result.put("knowledgeIds", knowledgeIds);
        return result;
    }

    @Override
    public void deleteById(Long id) {
        Agent agent = getById(id);
        if (agent == null) {
            throw new BizException(ErrorCode.AGENT_NOT_FOUND);
        }
        removeById(id);
    }
}
