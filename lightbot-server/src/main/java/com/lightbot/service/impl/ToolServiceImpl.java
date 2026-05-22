package com.lightbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lightbot.common.BizException;
import com.lightbot.dto.ToolRequest;
import com.lightbot.entity.Tool;
import com.lightbot.enums.CommonStatus;
import com.lightbot.enums.ErrorCode;
import com.lightbot.enums.ToolType;
import org.springframework.util.StringUtils;
import com.lightbot.mapper.ToolMapper;
import com.lightbot.service.ToolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tool 服务实现类
 *
 * @author finch
 * @since 2026-05-20
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolServiceImpl extends ServiceImpl<ToolMapper, Tool>
        implements ToolService {

    private final ApplicationContext applicationContext;

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
    public Page<Tool> listTools(int pageNum, int pageSize, String toolType) {
        return page(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Tool>()
                        .eq(StringUtils.hasText(toolType), Tool::getToolType, toolType)
                        .orderByDesc(Tool::getCreateTime));
    }

    @Override
    public void deleteById(Long id) {
        Tool tool = getById(id);
        if (tool == null) {
            throw new BizException(ErrorCode.TOOL_NOT_FOUND);
        }
        if (tool.getToolType() == ToolType.BUILTIN) {
            throw new BizException("内置工具不可删除");
        }
        removeById(id);
    }

    @Override
    public List<ToolCallback> resolveToolCallbacks(List<String> toolNames) {
        if (toolNames == null || toolNames.isEmpty()) {
            return List.of();
        }

        // 1. 从DB查询工具记录
        List<Tool> tools = list(new LambdaQueryWrapper<Tool>()
                .in(Tool::getName, toolNames)
                .eq(Tool::getStatus, CommonStatus.ACTIVE));
        if (tools.isEmpty()) {
            return List.of();
        }

        // 2. 收集所有 @Tool Bean 的 ToolCallback（通过 MethodToolCallbackProvider）
        List<ToolCallback> allCallbacks = getAllBuiltinToolCallbacks();
        Set<String> allCallbackNames = allCallbacks.stream()
                .map(cb -> cb.getToolDefinition().name())
                .collect(Collectors.toSet());

        // 3. 过滤出 Agent 绑定的工具
        List<ToolCallback> result = new ArrayList<>();
        for (Tool tool : tools) {
            if (allCallbackNames.contains(tool.getName())) {
                allCallbacks.stream()
                        .filter(cb -> cb.getToolDefinition().name().equals(tool.getName()))
                        .findFirst()
                        .ifPresent(result::add);
            } else if (tool.getToolType() == ToolType.API) {
                // API 类型工具：后续扩展 HTTP ToolCallback
                log.info("[ToolService] API工具暂不支持自动执行: name={}", tool.getName());
            }
        }
        return result;
    }

    @Override
    public List<ToolCallback> resolveToolCallbacksByIds(List<Long> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            return List.of();
        }

        // 1. 从DB查询工具记录
        List<Tool> tools = listByIds(toolIds);
        if (tools.isEmpty()) {
            return List.of();
        }

        // 2. 收集所有 @Tool Bean 的 ToolCallback
        List<ToolCallback> allCallbacks = getAllBuiltinToolCallbacks();
        Set<String> allCallbackNames = allCallbacks.stream()
                .map(cb -> cb.getToolDefinition().name())
                .collect(Collectors.toSet());

        // 3. 过滤出 Agent 绑定的工具
        List<ToolCallback> result = new ArrayList<>();
        for (Tool tool : tools) {
            if (allCallbackNames.contains(tool.getName())) {
                allCallbacks.stream()
                        .filter(cb -> cb.getToolDefinition().name().equals(tool.getName()))
                        .findFirst()
                        .ifPresent(result::add);
            } else if (tool.getToolType() == ToolType.API) {
                log.info("[ToolService] API工具暂不支持自动执行: name={}", tool.getName());
            }
        }
        return result;
    }

    /**
     * 获取所有内置 @Tool Bean 的 ToolCallback
     */
    private List<ToolCallback> getAllBuiltinToolCallbacks() {
        try {
            MethodToolCallbackProvider provider = MethodToolCallbackProvider.builder()
                    .toolObjects(getAllToolBeans())
                    .build();
            return List.of(provider.getToolCallbacks());
        } catch (Exception e) {
            log.warn("[ToolService] 获取内置ToolCallback失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 从 Spring 容器获取所有包含 @Tool 注解方法的 Bean
     */
    private List<Object> getAllToolBeans() {
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(org.springframework.stereotype.Component.class);
        return beans.values().stream()
                .filter(bean -> {
                    for (var method : bean.getClass().getMethods()) {
                        if (method.isAnnotationPresent(org.springframework.ai.tool.annotation.Tool.class)) {
                            return true;
                        }
                    }
                    return false;
                })
                .toList();
    }
}
