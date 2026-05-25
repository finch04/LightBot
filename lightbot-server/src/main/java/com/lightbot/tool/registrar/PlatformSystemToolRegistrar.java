package com.lightbot.tool.registrar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.entity.Tool;
import com.lightbot.enums.ToolType;
import com.lightbot.mapper.ToolMapper;
import com.lightbot.tool.annotation.SystemTool;
import com.lightbot.tool.annotation.ToolParamMeta;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.support.ToolDefinitions;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 平台系统工具注册器
 * <p>仅扫描 {@code com.lightbot.tool.systemtool} 包下带 {@link SystemTool} 且 autoInject=true 的工具，
 * 注册为 isSystem=true，自动注入所有 Agent。</p>
 *
 * @author finch
 * @since 2026-05-24
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlatformSystemToolRegistrar implements ApplicationRunner {

    private static final String SYSTEM_TOOL_PACKAGE = "com.lightbot.tool.systemtool";

    private final ApplicationContext applicationContext;
    private final ToolMapper toolMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void run(ApplicationArguments args) {
        log.info("[PlatformSystemToolRegistrar] 开始扫描平台系统工具（包: {}）...", SYSTEM_TOOL_PACKAGE);

        Map<String, Object> systemToolBeans = applicationContext.getBeansWithAnnotation(SystemTool.class);
        List<Tool> toolsToRegister = new ArrayList<>();

        for (Map.Entry<String, Object> entry : systemToolBeans.entrySet()) {
            Object bean = entry.getValue();
            Class<?> targetClass = resolveTargetClass(bean);
            if (!targetClass.getPackageName().startsWith(SYSTEM_TOOL_PACKAGE)) {
                continue;
            }

            SystemTool classAnnotation = targetClass.getAnnotation(SystemTool.class);
            if (classAnnotation == null || !classAnnotation.autoInject()) {
                log.warn("[PlatformSystemToolRegistrar] 跳过非自动注入的系统工具类: {}",
                        targetClass.getSimpleName());
                continue;
            }

            String classDisplayName = classAnnotation.displayName();
            collectToolsFromBean(bean, targetClass, classAnnotation, classDisplayName, toolsToRegister);
        }

        for (Tool tool : toolsToRegister) {
            Tool existing = toolMapper.selectByName(tool.getName());
            if (existing == null) {
                toolMapper.insert(tool);
                log.info("[PlatformSystemToolRegistrar] 注册平台系统工具: name={}, displayName={}",
                        tool.getName(), tool.getDisplayName());
            } else {
                tool.setId(existing.getId());
                tool.setCreateTime(existing.getCreateTime());
                toolMapper.updateById(tool);
                log.info("[PlatformSystemToolRegistrar] 更新平台系统工具: name={}", tool.getName());
            }
        }

        log.info("[PlatformSystemToolRegistrar] 平台系统工具注册完成: 共 {} 个", toolsToRegister.size());
    }

    private void collectToolsFromBean(Object bean, Class<?> targetClass, SystemTool classAnnotation,
                                      String classDisplayName, List<Tool> out) {
        for (Method method : targetClass.getDeclaredMethods()) {
            org.springframework.ai.tool.annotation.Tool toolAnnotation =
                    method.getAnnotation(org.springframework.ai.tool.annotation.Tool.class);
            if (toolAnnotation == null) {
                continue;
            }

            String toolName = toolAnnotation.name().isEmpty() ? method.getName() : toolAnnotation.name();
            SystemTool methodSystemTool = method.getAnnotation(SystemTool.class);
            String displayName = (methodSystemTool != null && !methodSystemTool.displayName().isEmpty())
                    ? methodSystemTool.displayName()
                    : classDisplayName;
            boolean autoInject = (methodSystemTool != null)
                    ? methodSystemTool.autoInject()
                    : classAnnotation.autoInject();

            Tool tool = new Tool();
            tool.setName(toolName);
            tool.setDisplayName(displayName);
            tool.setDescription(toolAnnotation.description());
            tool.setToolType(ToolType.BUILTIN);
            tool.setInputSchema(generateInputSchema(method));
            tool.setOutputSchema("{}");
            tool.setConfig("{\"exampleParams\": " + generateExampleParams(method) + "}");
            tool.setIsSystem(autoInject);

            out.add(tool);
            log.info("[PlatformSystemToolRegistrar] 发现平台系统工具: name={}, displayName={}, autoInject={}",
                    toolName, displayName, autoInject);
        }
    }

    private Class<?> resolveTargetClass(Object bean) {
        Class<?> clazz = bean.getClass();
        if (clazz.getName().contains("$$")) {
            Class<?> superClass = clazz.getSuperclass();
            if (superClass != null && !superClass.equals(Object.class)) {
                return superClass;
            }
        }
        return clazz;
    }

    private String generateInputSchema(Method method) {
        try {
            var toolDefinition = ToolDefinitions.from(method);
            String baseSchema = toolDefinition.inputSchema();
            if (baseSchema == null || baseSchema.isEmpty()) {
                return "{}";
            }

            var schemaNode = objectMapper.readTree(baseSchema);
            List<String> required = new ArrayList<>();

            for (Parameter param : method.getParameters()) {
                ToolParamMeta meta = param.getAnnotation(ToolParamMeta.class);
                if (meta != null && meta.required()) {
                    required.add(param.getName());
                }
            }

            if (!required.isEmpty()) {
                var requiredArray = objectMapper.createArrayNode();
                for (String r : required) {
                    requiredArray.add(r);
                }
                ((com.fasterxml.jackson.databind.node.ObjectNode) schemaNode).set("required", requiredArray);
            }

            return objectMapper.writeValueAsString(schemaNode);
        } catch (Exception e) {
            log.warn("[PlatformSystemToolRegistrar] 生成 inputSchema 失败: method={}, error={}",
                    method.getName(), e.getMessage());
            return "{}";
        }
    }

    private String generateExampleParams(Method method) {
        try {
            var example = objectMapper.createObjectNode();

            for (Parameter param : method.getParameters()) {
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
            log.warn("[PlatformSystemToolRegistrar] 生成示例参数失败: method={}, error={}",
                    method.getName(), e.getMessage());
            return "{}";
        }
    }
}
