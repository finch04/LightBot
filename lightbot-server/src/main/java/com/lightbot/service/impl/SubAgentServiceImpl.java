package com.lightbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.BizException;
import com.lightbot.dto.SubAgentRequest;
import com.lightbot.entity.SubAgent;
import com.lightbot.enums.ErrorCode;
import com.lightbot.mapper.SubAgentMapper;
import com.lightbot.service.SubAgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * SubAgent 服务实现
 *
 * @author finch
 * @since 2026-05-24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubAgentServiceImpl extends ServiceImpl<SubAgentMapper, SubAgent>
        implements SubAgentService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public SubAgent create(SubAgentRequest request) {
        // 1. 校验名称唯一性
        long count = count(new LambdaQueryWrapper<SubAgent>().eq(SubAgent::getName, request.getName()));
        if (count > 0) {
            throw new BizException(ErrorCode.SUBAGENT_NAME_EXISTS);
        }
        // 2. 构建实体
        SubAgent subAgent = new SubAgent();
        subAgent.setName(request.getName());
        subAgent.setDisplayName(request.getDisplayName());
        subAgent.setDescription(request.getDescription());
        subAgent.setSystemPrompt(request.getSystemPrompt());
        subAgent.setTools(toJson(request.getTools()));
        subAgent.setModelId(request.getModelId());
        subAgent.setEnabled(request.getEnabled() != null ? (request.getEnabled() ? 1 : 0) : 1);
        subAgent.setIsBuiltin(0);
        save(subAgent);
        return subAgent;
    }

    @Override
    public SubAgent update(SubAgentRequest request) {
        // 1. 校验存在性
        SubAgent subAgent = getById(request.getId());
        if (subAgent == null) {
            throw new BizException(ErrorCode.SUBAGENT_NOT_FOUND);
        }
        // 2. 内置不可编辑
        if (Integer.valueOf(1).equals(subAgent.getIsBuiltin())) {
            throw new BizException("内置 SubAgent 不可编辑");
        }
        // 3. 名称变更时校验唯一性
        if (!subAgent.getName().equals(request.getName())) {
            long count = count(new LambdaQueryWrapper<SubAgent>().eq(SubAgent::getName, request.getName()));
            if (count > 0) {
                throw new BizException(ErrorCode.SUBAGENT_NAME_EXISTS);
            }
        }
        // 4. 更新字段
        subAgent.setName(request.getName());
        subAgent.setDisplayName(request.getDisplayName());
        subAgent.setDescription(request.getDescription());
        subAgent.setSystemPrompt(request.getSystemPrompt());
        subAgent.setTools(toJson(request.getTools()));
        subAgent.setModelId(request.getModelId());
        if (request.getEnabled() != null) {
            subAgent.setEnabled(request.getEnabled() ? 1 : 0);
        }
        updateById(subAgent);
        return subAgent;
    }

    @Override
    public Page<SubAgent> listPage(int pageNum, int pageSize, String keyword, Boolean isBuiltin) {
        LambdaQueryWrapper<SubAgent> wrapper = new LambdaQueryWrapper<SubAgent>()
                .orderByDesc(SubAgent::getCreateTime);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(SubAgent::getName, keyword)
                    .or().like(SubAgent::getDisplayName, keyword));
        }
        if (isBuiltin != null) {
            wrapper.eq(SubAgent::getIsBuiltin, isBuiltin ? 1 : 0);
        }
        return baseMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public SubAgent getByName(String name) {
        return baseMapper.selectByName(name);
    }

    @Override
    public List<SubAgent> listEnabled() {
        return list(new LambdaQueryWrapper<SubAgent>()
                .eq(SubAgent::getEnabled, 1)
                .orderByDesc(SubAgent::getCreateTime));
    }

    @Override
    public void deleteById(Long id) {
        SubAgent subAgent = getById(id);
        if (subAgent == null) {
            throw new BizException(ErrorCode.SUBAGENT_NOT_FOUND);
        }
        if (Integer.valueOf(1).equals(subAgent.getIsBuiltin())) {
            throw new BizException("内置 SubAgent 不可删除");
        }
        removeById(id);
    }

    @Override
    public void setEnabled(Long id, boolean enabled) {
        SubAgent subAgent = getById(id);
        if (subAgent == null) {
            throw new BizException(ErrorCode.SUBAGENT_NOT_FOUND);
        }
        subAgent.setEnabled(enabled ? 1 : 0);
        updateById(subAgent);
    }

    private String toJson(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            return "[]";
        }
    }
}