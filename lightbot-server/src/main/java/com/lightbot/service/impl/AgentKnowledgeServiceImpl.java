package com.lightbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lightbot.entity.AgentKnowledge;
import com.lightbot.mapper.AgentKnowledgeMapper;
import com.lightbot.service.AgentKnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Agent-知识库关联 Service 实现
 *
 * @author finch
 * @since 2026-05-19
 */
@Service
@RequiredArgsConstructor
public class AgentKnowledgeServiceImpl extends ServiceImpl<AgentKnowledgeMapper, AgentKnowledge>
        implements AgentKnowledgeService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindKnowledge(Long agentId, Long knowledgeId) {
        // 1. 检查是否已绑定
        boolean exists = exists(new LambdaQueryWrapper<AgentKnowledge>()
                .eq(AgentKnowledge::getAgentId, agentId)
                .eq(AgentKnowledge::getKnowledgeId, knowledgeId));
        if (exists) {
            return;
        }

        // 2. 创建关联
        AgentKnowledge entity = new AgentKnowledge();
        entity.setAgentId(agentId);
        entity.setKnowledgeId(knowledgeId);
        save(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbindKnowledge(Long agentId, Long knowledgeId) {
        remove(new LambdaQueryWrapper<AgentKnowledge>()
                .eq(AgentKnowledge::getAgentId, agentId)
                .eq(AgentKnowledge::getKnowledgeId, knowledgeId));
    }

    @Override
    public List<Long> getKnowledgeIds(Long agentId) {
        List<AgentKnowledge> list = list(new LambdaQueryWrapper<AgentKnowledge>()
                .eq(AgentKnowledge::getAgentId, agentId)
                .select(AgentKnowledge::getKnowledgeId));
        return list.stream()
                .map(AgentKnowledge::getKnowledgeId)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateKnowledgeBindings(Long agentId, List<Long> knowledgeIds) {
        // 1. 查询现有绑定
        List<AgentKnowledge> existing = list(new LambdaQueryWrapper<AgentKnowledge>()
                .eq(AgentKnowledge::getAgentId, agentId));
        Set<Long> existingIds = existing.stream()
                .map(AgentKnowledge::getKnowledgeId)
                .collect(Collectors.toSet());

        // 2. 计算需要新增和删除的
        Set<Long> newIds = knowledgeIds != null ? Set.copyOf(knowledgeIds) : Set.of();
        Set<Long> toAdd = newIds.stream()
                .filter(id -> !existingIds.contains(id))
                .collect(Collectors.toSet());
        Set<Long> toRemove = existing.stream()
                .map(AgentKnowledge::getKnowledgeId)
                .filter(id -> !newIds.contains(id))
                .collect(Collectors.toSet());

        // 3. 批量删除
        if (!toRemove.isEmpty()) {
            remove(new LambdaQueryWrapper<AgentKnowledge>()
                    .eq(AgentKnowledge::getAgentId, agentId)
                    .in(AgentKnowledge::getKnowledgeId, toRemove));
        }

        // 4. 批量新增
        if (!toAdd.isEmpty()) {
            List<AgentKnowledge> newEntities = toAdd.stream()
                    .map(knowledgeId -> {
                        AgentKnowledge entity = new AgentKnowledge();
                        entity.setAgentId(agentId);
                        entity.setKnowledgeId(knowledgeId);
                        return entity;
                    })
                    .collect(Collectors.toList());
            saveBatch(newEntities);
        }
    }
}
