package com.lightbot.tool.registrar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.entity.Tool;
import com.lightbot.enums.CommonStatus;
import com.lightbot.enums.ToolType;
import com.lightbot.service.ToolService;
import com.lightbot.tool.annotation.SystemTool;
import com.lightbot.tool.annotation.ToolParamMeta;
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
 * 统一工具注册器
 * <p>扫描 {@code com.lightbot.tool.builtin} 和 {@code com.lightbot.tool.systemtool} 包下
 * 带 {@code @Tool} 注解的方法，根据 {@link SystemTool#type()} 自动注册到 tool 表。</p>
 *
 * <p>type 优先级：方法级别 > 类级别（默认 "builtin"）</p>
 *
 * @author finch
 * @since 2026-06-17
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolRegistrar {

    private static final List<String> SCAN_PACKAGES = List.of(
            "com.lightbot.tool.builtin",
            "com.lightbot.tool.systemtool"
    );

    private final ToolService toolService;
    private final ApplicationContext applicationContext;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void registerAllTools() {
        log.info("[ToolRegistrar] 开始扫描工具（包: {}）...", SCAN_PACKAGES);

        List<Object> toolBeans = findToolBeans();
        int imported = 0;
        int updated = 0;

        for (Object bean : toolBeans) {
            Class<?> clazz = getTargetClass(bean);
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.isSynthetic() || method.isBridge()) {
                    continue;
                }
                org.springframework.ai.tool.annotation.Tool toolAnnotation =
                        method.getAnnotation(org.springframework.ai.tool.annotation.Tool.class);
                if (toolAnnotation == null) {
                    continue;
                }

                String name = toolAnnotation.name();
                String description = toolAnnotation.description();
                String inputSchema = generateInputSchema(method);
                String displayName = resolveDisplayName(clazz, method, name);
                ToolType toolType = resolveToolType(clazz, method);
                String tagsJson = resolveTagsJson(clazz, method);
                String config = "{\"exampleParams\": " + generateExampleParams(method) + "}";

                Tool existing = toolService.getOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Tool>()
                                .eq(Tool::getName, name)
                                .last("LIMIT 1"));

                if (existing == null) {
                    Tool tool = new Tool();
                    tool.setName(name);
                    tool.setDisplayName(displayName);
                    tool.setDescription(description);
                    tool.setToolType(toolType);
                    tool.setInputSchema(inputSchema);
                    tool.setOutputSchema("{}");
                    tool.setConfig(config);
                    tool.setTags(tagsJson);
                    tool.setStatus(CommonStatus.ACTIVE);
                    toolService.save(tool);
                    imported++;
                    log.info("[ToolRegistrar] 注册工具: name={}, type={}, class={}",
                            name, toolType, clazz.getSimpleName());
                } else {
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
                    if (existing.getToolType() != toolType) {
                        existing.setToolType(toolType);
                        changed = true;
                    }
                    if (changed) {
                        toolService.updateById(existing);
                        updated++;
                        log.info("[ToolRegistrar] 更新工具: name={}, type={}", name, toolType);
                    }
                }
            }
        }

        log.info("[ToolRegistrar] 工具注册完成: beans={}, imported={}, updated={}",
                toolBeans.size(), imported, updated);
    }

    /**
     * 解析工具类型：方法级别 > 类级别，默认 BUILTIN
     */
    private ToolType resolveToolType(Class<?> clazz, Method method) {
        SystemTool methodTool = method.getAnnotation(SystemTool.class);
        if (methodTool != null && !methodTool.type().isEmpty()) {
            return ToolType.fromValue(methodTool.type());
        }
        SystemTool classTool = clazz.getAnnotation(SystemTool.class);
        if (classTool != null && !classTool.type().isEmpty()) {
            return ToolType.fromValue(classTool.type());
        }
        return ToolType.BUILTIN;
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
     * 从 @SystemTool 注解提取 tags，方法级别与类级别合并去重
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
            return objectMapper.writeValueAsString(tags);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<Object> findToolBeans() {
        Map<String, Object> allBeans = applicationContext.getBeansWithAnnotation(
                org.springframework.stereotype.Component.class);
        return allBeans.values().stream()
                .filter(bean -> {
                    Class<?> clazz = getTargetClass(bean);
                    return SCAN_PACKAGES.stream().anyMatch(pkg -> clazz.getPackageName().startsWith(pkg));
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

                ToolParamMeta meta = param.getAnnotation(ToolParamMeta.class);
                if (meta != null && meta.required()) {
                    required.add(param.getName());
                }
            }

            schema.put("properties", properties);
            schema.put("required", required);
            return objectMapper.writeValueAsString(schema);
        } catch (Exception e) {
            log.warn("[ToolRegistrar] 生成 inputSchema 失败: {}", e.getMessage());
            return "{}";
        }
    }

    private String generateExampleParams(Method method) {
        try {
            var example = objectMapper.createObjectNode();
            for (Parameter param : method.getParameters()) {
                if (param.getType().equals(org.springframework.ai.chat.model.ToolContext.class)) {
                    continue;
                }
                String paramName = param.getName();
                ToolParam toolParam = param.getAnnotation(ToolParam.class);
                ToolParamMeta meta = param.getAnnotation(ToolParamMeta.class);

                String exampleValue = null;
                if (meta != null && !meta.example().isEmpty()) {
                    exampleValue = meta.example();
                } else if (toolParam != null) {
                    exampleValue = toolParam.description();
                }

                Class<?> paramType = param.getType();
                if (paramType == String.class) {
                    example.put(paramName, exampleValue != null ? exampleValue : "示例值");
                } else if (paramType == int.class || paramType == Integer.class
                        || paramType == long.class || paramType == Long.class) {
                    example.put(paramName, 0);
                } else if (paramType == double.class || paramType == Double.class
                        || paramType == float.class || paramType == Float.class) {
                    example.put(paramName, 0.0);
                } else if (paramType == boolean.class || paramType == Boolean.class) {
                    example.put(paramName, true);
                } else {
                    example.put(paramName, exampleValue);
                }
            }
            return objectMapper.writeValueAsString(example);
        } catch (Exception e) {
            log.warn("[ToolRegistrar] 生成示例参数失败: method={}, error={}", method.getName(), e.getMessage());
            return "{}";
        }
    }

    private String resolveJsonType(Class<?> type) {
        if (type == String.class) return "string";
        if (type == int.class || type == Integer.class || type == long.class || type == Long.class) return "integer";
        if (type == double.class || type == Double.class || type == float.class || type == Float.class) return "number";
        if (type == boolean.class || type == Boolean.class) return "boolean";
        if (List.class.isAssignableFrom(type)) return "array";
        return "object";
    }
}
