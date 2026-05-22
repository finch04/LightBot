package com.lightbot.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.entity.Tool;
import com.lightbot.enums.CommonStatus;
import com.lightbot.enums.ToolType;
import com.lightbot.service.ToolService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

/**
 * 内置工具自动注册器
 * <p>启动时扫描所有 @Tool 注解方法，自动导入到 tool 表</p>
 *
 * @author finch
 * @since 2026-05-22
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BuiltInToolRegistrar {

    private final ToolService toolService;
    private final ApplicationContext applicationContext;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @PostConstruct
    public void importBuiltInTools() {
        // 1. 扫描所有包含 @Tool 注解方法的 Bean
        List<Object> toolBeans = findToolBeans();
        int imported = 0;

        for (Object bean : toolBeans) {
            for (Method method : bean.getClass().getMethods()) {
                org.springframework.ai.tool.annotation.Tool annotation =
                        method.getAnnotation(org.springframework.ai.tool.annotation.Tool.class);
                if (annotation == null) continue;

                String name = annotation.name();
                String description = annotation.description();

                // 2. 检查DB中是否已存在
                Tool existing = toolService.getOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Tool>()
                                .eq(Tool::getName, name)
                                .last("LIMIT 1"));

                // 3. 生成 inputSchema
                String inputSchema = generateInputSchema(method);

                if (existing == null) {
                    // 新增
                    Tool tool = new Tool();
                    tool.setName(name);
                    tool.setDisplayName(name);
                    tool.setDescription(description);
                    tool.setToolType(ToolType.BUILTIN);
                    tool.setInputSchema(inputSchema);
                    tool.setStatus(CommonStatus.ACTIVE);
                    toolService.save(tool);
                    imported++;
                    log.info("[BuiltInToolRegistrar] 导入内置工具: name={}", name);
                } else if (existing.getToolType() == ToolType.BUILTIN) {
                    // 已存在且为BUILTIN：同步 description 和 inputSchema
                    boolean changed = false;
                    if (!description.equals(existing.getDescription())) {
                        existing.setDescription(description);
                        changed = true;
                    }
                    if (!inputSchema.equals(existing.getInputSchema())) {
                        existing.setInputSchema(inputSchema);
                        changed = true;
                    }
                    if (changed) {
                        toolService.updateById(existing);
                        log.info("[BuiltInToolRegistrar] 更新内置工具: name={}", name);
                    }
                }
            }
        }
        log.info("[BuiltInToolRegistrar] 内置工具扫描完成: total={}, imported={}", toolBeans.size(), imported);
    }

    /**
     * 查找所有包含 @Tool 注解方法的 Spring Bean
     */
    private List<Object> findToolBeans() {
        Map<String, Object> allBeans = applicationContext.getBeansWithAnnotation(
                org.springframework.stereotype.Component.class);
        return allBeans.values().stream()
                .filter(bean -> {
                    for (Method method : bean.getClass().getMethods()) {
                        if (method.isAnnotationPresent(org.springframework.ai.tool.annotation.Tool.class)) {
                            return true;
                        }
                    }
                    return false;
                })
                .toList();
    }

    /**
     * 从方法参数生成 JSON Schema（OpenAI function calling 格式）
     */
    private String generateInputSchema(Method method) {
        try {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");

            Map<String, Object> properties = new LinkedHashMap<>();
            List<String> required = new ArrayList<>();

            Parameter[] params = method.getParameters();
            for (Parameter param : params) {
                // 跳过 ToolContext 参数
                if (param.getType().equals(org.springframework.ai.chat.model.ToolContext.class)) {
                    continue;
                }
                ToolParam toolParam = param.getAnnotation(ToolParam.class);
                if (toolParam == null) continue;

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
            log.warn("[BuiltInToolRegistrar] 生成inputSchema失败: {}", e.getMessage());
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
