package com.lightbot.tool.registrar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.entity.Tool;
import com.lightbot.enums.CommonStatus;
import com.lightbot.enums.ToolType;
import com.lightbot.service.ToolService;
import com.lightbot.tool.annotation.SystemTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.aop.framework.Advised;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

/**
 * 内置工具注册器
 * <p>仅扫描 {@code com.lightbot.tool.builtin} 包下的 {@code @Tool} 方法，
 * 注册为 isSystem=false 的可选内置工具（与平台系统工具区分）。</p>
 *
 * @author finch
 * @since 2026-05-22
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BuiltinToolRegistrar {

    private static final String BUILTIN_TOOL_PACKAGE = "com.lightbot.tool.builtin";

    private final ToolService toolService;
    private final ApplicationContext applicationContext;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @PostConstruct
    public void importBuiltinTools() {
        log.info("[BuiltinToolRegistrar] 开始扫描内置工具（包: {}）...", BUILTIN_TOOL_PACKAGE);

        List<Object> toolBeans = findBuiltinToolBeans();
        int imported = 0;
        int updated = 0;

        for (Object bean : toolBeans) {
            Class<?> clazz = getTargetClass(bean);
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.isSynthetic() || method.isBridge()) {
                    continue;
                }
                org.springframework.ai.tool.annotation.Tool annotation =
                        method.getAnnotation(org.springframework.ai.tool.annotation.Tool.class);
                if (annotation == null) {
                    continue;
                }

                // 平台系统工具由 PlatformSystemToolRegistrar 处理
                if (isPlatformSystemTool(clazz, method)) {
                    log.debug("[BuiltinToolRegistrar] 跳过平台系统工具方法: {}.{}",
                            clazz.getSimpleName(), method.getName());
                    continue;
                }

                String name = annotation.name();
                String description = annotation.description();
                String inputSchema = generateInputSchema(method);

                Tool existing = toolService.getOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Tool>()
                                .eq(Tool::getName, name)
                                .last("LIMIT 1"));

                String tagsJson = resolveTagsJson(clazz, method);

                if (existing == null) {
                    Tool tool = new Tool();
                    tool.setName(name);
                    tool.setDisplayName(resolveDisplayName(clazz, method, name));
                    tool.setDescription(description);
                    tool.setToolType(ToolType.BUILTIN);
                    tool.setInputSchema(inputSchema);
                    tool.setTags(tagsJson);
                    tool.setIsSystem(false);
                    tool.setStatus(CommonStatus.ACTIVE);
                    toolService.save(tool);
                    imported++;
                    log.info("[BuiltinToolRegistrar] 注册内置工具: name={}, class={}",
                            name, clazz.getSimpleName());
                } else if (existing.getToolType() == ToolType.BUILTIN && !Boolean.TRUE.equals(existing.getIsSystem())) {
                    boolean changed = false;
                    if (!description.equals(existing.getDescription())) {
                        existing.setDescription(description);
                        changed = true;
                    }
                    if (!inputSchema.equals(existing.getInputSchema())) {
                        existing.setInputSchema(inputSchema);
                        changed = true;
                    }
                    if (!tagsJson.equals(existing.getTags() != null ? existing.getTags() : "[]")) {
                        existing.setTags(tagsJson);
                        changed = true;
                    }
                    if (changed) {
                        toolService.updateById(existing);
                        updated++;
                        log.info("[BuiltinToolRegistrar] 更新内置工具: name={}", name);
                    }
                }
            }
        }

        log.info("[BuiltinToolRegistrar] 内置工具扫描完成: beans={}, imported={}, updated={}",
                toolBeans.size(), imported, updated);
    }

    private boolean isPlatformSystemTool(Class<?> clazz, Method method) {
        if (!clazz.getPackageName().startsWith("com.lightbot.tool.systemtool")) {
            return false;
        }
        SystemTool classTool = clazz.getAnnotation(SystemTool.class);
        if (classTool != null && classTool.autoInject()) {
            return true;
        }
        SystemTool methodTool = method.getAnnotation(SystemTool.class);
        return methodTool != null && methodTool.autoInject();
    }

    private String resolveDisplayName(Class<?> clazz, Method method, String fallback) {
        SystemTool methodTool = method.getAnnotation(SystemTool.class);
        if (methodTool != null && !methodTool.displayName().isEmpty()) {
            return methodTool.displayName();
        }
        SystemTool classTool = clazz.getAnnotation(SystemTool.class);
        if (classTool != null && !classTool.displayName().isEmpty()) {
            return classTool.displayName();
        }
        return fallback;
    }

    /**
     * 从 @SystemTool 注解提取 tags，方法级别优先，其次类级别，合并去重
     */
    private String resolveTagsJson(Class<?> clazz, Method method) {
        try {
            java.util.LinkedHashSet<String> tags = new java.util.LinkedHashSet<>();
            SystemTool classTool = clazz.getAnnotation(SystemTool.class);
            if (classTool != null) {
                java.util.Collections.addAll(tags, classTool.tags());
            }
            SystemTool methodTool = method.getAnnotation(SystemTool.class);
            if (methodTool != null) {
                java.util.Collections.addAll(tags, methodTool.tags());
            }
            return OBJECT_MAPPER.writeValueAsString(tags);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<Object> findBuiltinToolBeans() {
        Map<String, Object> allBeans = applicationContext.getBeansWithAnnotation(
                org.springframework.stereotype.Component.class);
        return allBeans.values().stream()
                .filter(bean -> {
                    Class<?> clazz = getTargetClass(bean);
                    return clazz.getPackageName().startsWith(BUILTIN_TOOL_PACKAGE);
                })
                .filter(bean -> {
                    Class<?> clazz = getTargetClass(bean);
                    for (Method method : clazz.getDeclaredMethods()) {
                        if (method.isSynthetic() || method.isBridge()) {
                            continue;
                        }
                        if (method.isAnnotationPresent(org.springframework.ai.tool.annotation.Tool.class)) {
                            return true;
                        }
                    }
                    return false;
                })
                .toList();
    }

    private Class<?> getTargetClass(Object bean) {
        if (bean instanceof Advised advised) {
            try {
                return advised.getTargetSource().getTarget().getClass();
            } catch (Exception ignored) {
                // fall through
            }
        }
        return bean.getClass();
    }

    private String generateInputSchema(Method method) {
        try {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");

            Map<String, Object> properties = new LinkedHashMap<>();
            List<String> required = new ArrayList<>();

            for (Parameter param : method.getParameters()) {
                if (param.getType().equals(org.springframework.ai.chat.model.ToolContext.class)) {
                    continue;
                }
                ToolParam toolParam = param.getAnnotation(ToolParam.class);
                if (toolParam == null) {
                    continue;
                }

                Map<String, Object> prop = new LinkedHashMap<>();
                prop.put("type", resolveJsonType(param.getType()));
                prop.put("description", toolParam.description());
                properties.put(param.getName(), prop);

                if (toolParam.required()) {
                    required.add(param.getName());
                }
            }

            schema.put("properties", properties);
            schema.put("required", required);
            return OBJECT_MAPPER.writeValueAsString(schema);
        } catch (Exception e) {
            log.warn("[BuiltinToolRegistrar] 生成 inputSchema 失败: {}", e.getMessage());
            return "{}";
        }
    }

    private String resolveJsonType(Class<?> type) {
        if (type == String.class) {
            return "string";
        }
        if (type == int.class || type == Integer.class || type == long.class || type == Long.class) {
            return "integer";
        }
        if (type == double.class || type == Double.class || type == float.class || type == Float.class) {
            return "number";
        }
        if (type == boolean.class || type == Boolean.class) {
            return "boolean";
        }
        if (List.class.isAssignableFrom(type)) {
            return "array";
        }
        return "object";
    }
}
