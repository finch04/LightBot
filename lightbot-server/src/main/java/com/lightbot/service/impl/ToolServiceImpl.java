package com.lightbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lightbot.common.BizException;
import com.lightbot.dto.ToolRequest;
import com.lightbot.entity.Tool;
import com.lightbot.enums.CommonStatus;
import com.lightbot.enums.ErrorCode;
import org.springframework.util.StringUtils;
import com.lightbot.mapper.ToolMapper;
import com.lightbot.service.ToolService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Tool 服务实现类
 *
 * @author finch
 * @since 2026-05-20
 */
@Slf4j
@Service
public class ToolServiceImpl extends ServiceImpl<ToolMapper, Tool>
        implements ToolService {

    @Override
    public Tool create(ToolRequest request) {
        // 1. 校验名称唯一性
        long count = count(new LambdaQueryWrapper<Tool>().eq(Tool::getName, request.getName()));
        if (count > 0) {
            throw new BizException(ErrorCode.TOOL_NAME_EXISTS);
        }
        // 2. 构建实体并保存
        Tool tool = new Tool();
        tool.setName(request.getName());
        tool.setDisplayName(request.getDisplayName());
        tool.setDescription(request.getDescription());
        tool.setToolType(request.getToolType());
        tool.setInputSchema(request.getInputSchema());
        tool.setOutputSchema(request.getOutputSchema());
        tool.setConfig(request.getConfig());
        tool.setEndpointUrl(request.getEndpointUrl());
        tool.setAuthType(request.getAuthType());
        tool.setAuthConfig(request.getAuthConfig());
        tool.setStatus(CommonStatus.ACTIVE);
        save(tool);
        return tool;
    }

    @Override
    public Tool update(ToolRequest request) {
        // 1. 校验存在性
        Tool tool = getById(request.getId());
        if (tool == null) {
            throw new BizException(ErrorCode.TOOL_NOT_FOUND);
        }
        // 2. 更新字段
        tool.setName(request.getName());
        tool.setDisplayName(request.getDisplayName());
        tool.setDescription(request.getDescription());
        tool.setToolType(request.getToolType());
        tool.setInputSchema(request.getInputSchema());
        tool.setOutputSchema(request.getOutputSchema());
        tool.setConfig(request.getConfig());
        tool.setEndpointUrl(request.getEndpointUrl());
        tool.setAuthType(request.getAuthType());
        tool.setAuthConfig(request.getAuthConfig());
        updateById(tool);
        return tool;
    }

    @Override
    public Page<Tool> listPage(int pageNum, int pageSize, String name) {
        return page(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Tool>()
                        .like(StringUtils.hasText(name), Tool::getName, name)
                        .orderByDesc(Tool::getCreateTime));
    }

    @Override
    public void deleteById(Long id) {
        if (!removeById(id)) {
            throw new BizException(ErrorCode.TOOL_NOT_FOUND);
        }
    }
}
