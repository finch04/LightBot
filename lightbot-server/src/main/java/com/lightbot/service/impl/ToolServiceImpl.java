package com.lightbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lightbot.common.BizException;
import com.lightbot.dto.ToolRequest;
import com.lightbot.entity.Agent;
import com.lightbot.entity.Tool;
import com.lightbot.enums.CommonStatus;
import com.lightbot.enums.ErrorCode;
import com.lightbot.enums.ToolType;
import org.springframework.util.StringUtils;
import com.lightbot.mapper.ToolMapper;
import com.lightbot.service.AgentService;
import com.lightbot.service.ToolService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.method.MethodToolCallback;
import org.springframework.ai.tool.support.ToolDefinitions;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
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
public class ToolServiceImpl extends ServiceImpl<ToolMapper, Tool>
        implements ToolService {

    private final ApplicationContext applicationContext;

    @Autowired
    @Lazy
    private AgentService agentService;

    public ToolServiceImpl(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

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
        // 2. 系统工具不可编辑
        if (Boolean.TRUE.equals(tool.getIsSystem())) {
            throw new BizException("系统工具不可编辑");
        }
        // 3. 更新字段
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
        return baseMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Tool>()
                        .like(StringUtils.hasText(name), Tool::getName, name)
                        .orderByDesc(Tool::getCreateTime));
    }

    @Override
    public Page<Tool> listTools(int pageNum, int pageSize, String toolType) {
        return baseMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Tool>()
                        .eq(StringUtils.hasText(toolType), Tool::getToolType, toolType)
                        .orderByDesc(Tool::getCreateTime));
    }

    @Override
    public Page<Tool> listToolsWithFilter(int pageNum, int pageSize, String keyword, String toolType, Boolean isSystem) {
        LambdaQueryWrapper<Tool> wrapper = new LambdaQueryWrapper<Tool>()
                .orderByDesc(Tool::getCreateTime);

        // 关键字搜索（name 或 displayName）
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w
                    .like(Tool::getName, keyword)
                    .or()
                    .like(Tool::getDisplayName, keyword));
        }

        // 工具类型过滤
        if (StringUtils.hasText(toolType)) {
            wrapper.eq(Tool::getToolType, toolType);
        }

        // 系统工具过滤
        if (isSystem != null) {
            wrapper.eq(Tool::getIsSystem, isSystem);
        }

        return baseMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public void deleteById(Long id) {
        Tool tool = getById(id);
        if (tool == null) {
            throw new BizException(ErrorCode.TOOL_NOT_FOUND);
        }
        // 系统工具不可删除
        if (Boolean.TRUE.equals(tool.getIsSystem())) {
            throw new BizException("系统工具不可删除");
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
     * <p>绕过 MethodToolCallbackProvider，手动用反射构建 ToolCallback，
     * 避免 CGLIB 代理和 getDeclaredMethods() 导致的注解发现失败</p>
     */
    private List<ToolCallback> getAllBuiltinToolCallbacks() {
        try {
            List<ToolCallback> callbacks = new ArrayList<>();
            Map<String, Object> beans = applicationContext.getBeansWithAnnotation(org.springframework.stereotype.Component.class);

            for (Object bean : beans.values()) {
                // 解包 CGLIB 代理，获取真实类
                Class<?> clazz = getTargetClass(bean);
                // 直接遍历类声明的方法（跳过编译器生成的桥方法和synthetic方法）
                for (Method method : clazz.getDeclaredMethods()) {
                    if (method.isSynthetic() || method.isBridge()) continue;
                    org.springframework.ai.tool.annotation.Tool tool = method.getAnnotation(org.springframework.ai.tool.annotation.Tool.class);
                    if (tool != null) {
                        method.setAccessible(true);
                        callbacks.add(MethodToolCallback.builder()
                                .toolDefinition(ToolDefinitions.from(method))
                                .toolMetadata(ToolMetadata.from(method))
                                .toolMethod(method)
                                .toolObject(bean)
                                .build());
                    }
                }
            }

            log.info("[ToolService] 发现内置ToolCallback: {}", callbacks.stream()
                    .map(cb -> cb.getToolDefinition().name()).toList());
            return callbacks;
        } catch (Exception e) {
            log.warn("[ToolService] 获取内置ToolCallback失败: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public String testTool(Long toolId, String args) {
        // 1. 解析工具回调
        List<ToolCallback> callbacks = resolveToolCallbacksByIds(List.of(toolId));
        if (callbacks.isEmpty()) {
            throw new BizException(ErrorCode.TOOL_NOT_FOUND);
        }

        // 2. 执行工具
        ToolCallback callback = callbacks.get(0);
        String toolName = callback.getToolDefinition().name();
        log.info("[ToolService] 测试工具: toolId={}, name={}, args={}", toolId, toolName, args);

        try {
            // 从 args 中提取 agentId（如有），供 query_knowledge 等需要上下文的工具使用
            long agentId = 0L;
            if (args != null && !args.isBlank()) {
                try {
                    var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(args);
                    if (node.has("agentId") && !node.get("agentId").isNull()) {
                        var idNode = node.get("agentId");
                        if (idNode.isNumber()) {
                            agentId = idNode.asLong(0);
                        } else if (idNode.isTextual() && !idNode.asText().isBlank()) {
                            agentId = Long.parseLong(idNode.asText().trim());
                        }
                    }
                } catch (Exception ignored) {}
            }
            // agentId 为空时，使用当前用户的默认 Agent（便于测试 query_knowledge 等工具）
            if (agentId == 0) {
                try {
                    long userId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong();
                    Agent defaultAgent = agentService.getDefaultAgent(userId);
                    if (defaultAgent != null) {
                        agentId = defaultAgent.getId();
                        log.info("[ToolService] 测试工具自动使用默认Agent: agentId={}", agentId);
                    }
                } catch (cn.dev33.satoken.exception.NotWebContextException ignored) {
                    log.debug("[ToolService] 非Web上下文，跳过默认Agent查找");
                }
            }
            org.springframework.ai.chat.model.ToolContext testContext =
                    new org.springframework.ai.chat.model.ToolContext(Map.of(
                            "agentId", agentId,
                            "requestId", "test-" + System.nanoTime()));
            String callArgs = com.lightbot.util.ToolArgsSanitizer.forTestCall(args != null ? args : "{}");
            String result = callback.call(callArgs, testContext);
            log.info("[ToolService] 工具测试完成: name={}, resultLength={}", toolName, result.length());
            return result;
        } catch (Exception e) {
            log.error("[ToolService] 工具测试失败: name={}, error={}", toolName, e.getMessage(), e);
            return "工具执行失败: " + e.getMessage();
        }
    }

    /**
     * 获取 Bean 的真实类（处理 CGLIB 代理）
     */
    private Class<?> getTargetClass(Object bean) {
        if (bean instanceof Advised advised) {
            try {
                return advised.getTargetSource().getTarget().getClass();
            } catch (Exception e) {
                // fall through
            }
        }
        return bean.getClass();
    }

    @Override
    public Map<String, Object> getExampleParams(Long toolId) {
        Tool tool = getById(toolId);
        if (tool == null) {
            throw new BizException(ErrorCode.TOOL_NOT_FOUND);
        }
        // 从 config 字段解析 exampleParams
        if (tool.getConfig() != null && !tool.getConfig().isBlank()) {
            try {
                var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(tool.getConfig());
                if (node.has("exampleParams")) {
                    var exampleNode = node.get("exampleParams");
                    Map<String, Object> example = new java.util.HashMap<>();
                    exampleNode.fields().forEachRemaining(entry -> {
                        example.put(entry.getKey(), entry.getValue().asText());
                    });
                    return example;
                }
            } catch (Exception e) {
                log.warn("[ToolService] 解析示例参数失败: toolId={}, error={}", toolId, e.getMessage());
            }
        }
        return Map.of();
    }
}
