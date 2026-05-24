package com.lightbot.tool;

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
 * 系统工具自动注册器
 * <p>启动时扫描所有 @SystemTool 标记的类，解析 @Tool 方法并自动注册到 tool 表。</p>
 *
 * @author finch
 * @since 2026-05-24
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemToolRegistrar implements ApplicationRunner {

    private final ApplicationContext applicationContext;
    private final ToolMapper toolMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void run(ApplicationArguments args) {
        log.info("[SystemToolRegistrar] 开始扫描系统工具...");

        // 1. 扫描所有 @SystemTool 标记的 Bean
        Map<String, Object> systemToolBeans = applicationContext.getBeansWithAnnotation(SystemTool.class);
        List<Tool> systemTools = new ArrayList<>();

        for (Map.Entry<String, Object> entry : systemToolBeans.entrySet()) {
            Object bean = entry.getValue();
            SystemTool classAnnotation = bean.getClass().getAnnotation(SystemTool.class);
            String classDisplayName = classAnnotation.displayName();
            String classDescription = classAnnotation.description();

            // 2. 遍历类中的 @Tool 方法
            for (Method method : bean.getClass().getDeclaredMethods()) {
                org.springframework.ai.tool.annotation.Tool toolAnnotation =
                        method.getAnnotation(org.springframework.ai.tool.annotation.Tool.class);
                if (toolAnnotation == null) continue;

                // 3. 解析工具定义
                String toolName = toolAnnotation.name().isEmpty() ? method.getName() : toolAnnotation.name();
                String toolDescription = toolAnnotation.description();

                // 3.1 获取 displayName 和 autoInject：优先方法级 @SystemTool，否则用类级
                SystemTool methodSystemTool = method.getAnnotation(SystemTool.class);
                String displayName = (methodSystemTool != null && !methodSystemTool.displayName().isEmpty())
                        ? methodSystemTool.displayName()
                        : classDisplayName;
                boolean autoInject = (methodSystemTool != null)
                        ? methodSystemTool.autoInject()
                        : classAnnotation.autoInject();

                // 4. 生成 inputSchema
                String inputSchema = generateInputSchema(method);

                // 5. 生成示例参数
                String exampleParams = generateExampleParams(method);

                // 6. 构建 Tool 实体
                Tool tool = new Tool();
                tool.setName(toolName);
                tool.setDisplayName(displayName);
                tool.setDescription(toolDescription);
                tool.setToolType(ToolType.BUILTIN);
                tool.setInputSchema(inputSchema);
                tool.setOutputSchema("{}");
                tool.setConfig("{\"exampleParams\": " + exampleParams + "}");
                tool.setIsSystem(autoInject);

                systemTools.add(tool);
                log.info("[SystemToolRegistrar] 发现系统工具: name={}, displayName={}", toolName, tool.getDisplayName());
            }
        }

        // 7. 注册到数据库（插入或更新）
        for (Tool tool : systemTools) {
            Tool existing = toolMapper.selectByName(tool.getName());
            if (existing == null) {
                toolMapper.insert(tool);
                log.info("[SystemToolRegistrar] 注册新系统工具: name={}", tool.getName());
            } else {
                // 更新现有记录，保留 id 和 create_time
                tool.setId(existing.getId());
                tool.setCreateTime(existing.getCreateTime());
                toolMapper.updateById(tool);
                log.info("[SystemToolRegistrar] 更新系统工具: name={}", tool.getName());
            }
        }

        log.info("[SystemToolRegistrar] 系统工具注册完成: 共 {} 个", systemTools.size());
    }

    /**
     * 从方法生成 inputSchema
     */
    private String generateInputSchema(Method method) {
        try {
            // 使用 Spring AI 的 ToolDefinitions.from 生成基础 schema
            var toolDefinition = ToolDefinitions.from(method);
            String baseSchema = toolDefinition.inputSchema();

            // 解析并补充 required 字段（从 @ToolParamMeta 获取）
            if (baseSchema == null || baseSchema.isEmpty()) {
                return "{}";
            }

            // 如果有 @ToolParamMeta，补充 required 信息
            var schemaNode = objectMapper.readTree(baseSchema);
            var properties = schemaNode.path("properties");
            List<String> required = new ArrayList<>();

            for (Parameter param : method.getParameters()) {
                ToolParamMeta meta = param.getAnnotation(ToolParamMeta.class);
                if (meta != null && meta.required()) {
                    required.add(param.getName());
                }
            }

            // 如果有新的 required 参数，更新 schema
            if (!required.isEmpty() && schemaNode.has("required")) {
                var existingRequired = schemaNode.path("required");
                if (existingRequired.isArray()) {
                    for (var item : existingRequired) {
                        if (!required.contains(item.asText())) {
                            required.add(item.asText());
                        }
                    }
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
            log.warn("[SystemToolRegistrar] 生成 inputSchema 失败: method={}, error={}", method.getName(), e.getMessage());
            return "{}";
        }
    }

    /**
     * 从方法参数生成示例参数
     */
    private String generateExampleParams(Method method) {
        try {
            var example = objectMapper.createObjectNode();

            for (Parameter param : method.getParameters()) {
                String paramName = param.getName();
                ToolParam toolParam = param.getAnnotation(ToolParam.class);
                ToolParamMeta meta = param.getAnnotation(ToolParamMeta.class);

                // 获取示例值
                String exampleValue = null;
                if (meta != null && !meta.example().isEmpty()) {
                    exampleValue = meta.example();
                } else if (toolParam != null) {
                    // 如果没有 example，使用 description 作为占位符
                    exampleValue = toolParam.description();
                }

                // 根据参数类型设置值
                Class<?> paramType = param.getType();
                if (paramType == String.class) {
                    example.put(paramName, exampleValue != null ? exampleValue : "示例值");
                } else if (paramType == int.class || paramType == Integer.class ||
                           paramType == long.class || paramType == Long.class) {
                    example.put(paramName, 0);
                } else if (paramType == double.class || paramType == Double.class ||
                           paramType == float.class || paramType == Float.class) {
                    example.put(paramName, 0.0);
                } else if (paramType == boolean.class || paramType == Boolean.class) {
                    example.put(paramName, true);
                } else {
                    example.put(paramName, exampleValue != null ? exampleValue : null);
                }
            }

            return objectMapper.writeValueAsString(example);
        } catch (Exception e) {
            log.warn("[SystemToolRegistrar] 生成示例参数失败: method={}, error={}", method.getName(), e.getMessage());
            return "{}";
        }
    }
}