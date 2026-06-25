package com.lightbot.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.constant.ToolResultPrefixes;
import com.lightbot.entity.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.Map;

/**
 * API 工具回调：将数据库中定义的 API 工具包装为 Spring AI ToolCallback
 * <p>Agent 对话时 LLM 可直接调用此工具发起 HTTP 请求</p>
 *
 * @author finch
 * @since 2026-06-25
 */
@Slf4j
public class ApiToolCallback implements ToolCallback {

    private final Tool tool;
    private final ToolDefinition toolDefinition;
    private final ApiToolExecutionService executionService;
    private final ObjectMapper objectMapper;

    public ApiToolCallback(Tool tool, ApiToolExecutionService executionService, ObjectMapper objectMapper) {
        this.tool = tool;
        this.executionService = executionService;
        this.objectMapper = objectMapper;
        this.toolDefinition = buildToolDefinition(tool);
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return toolDefinition;
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        log.info("[ApiToolCallback] 执行API工具: name={}, input={}", tool.getName(), toolInput);
        try {
            Map<String, Object> inputs = parseInputs(toolInput);
            String result = executionService.execute(tool, inputs);
            log.info("[ApiToolCallback] API工具执行完成: name={}, resultLength={}", tool.getName(), result.length());
            return result;
        } catch (Exception e) {
            log.error("[ApiToolCallback] API工具执行异常: name={}, error={}", tool.getName(), e.getMessage(), e);
            return ToolResultPrefixes.failureJson(e.getMessage());
        }
    }

    /**
     * 从工具实体构建 ToolDefinition
     */
    private ToolDefinition buildToolDefinition(Tool tool) {
        return DefaultToolDefinition.builder()
                .name(tool.getName())
                .description(tool.getDescription() != null ? tool.getDescription() : "")
                .inputSchema(tool.getInputSchema() != null ? tool.getInputSchema() : "{}")
                .build();
    }

    /**
     * 解析 LLM 传入的 JSON 参数
     */
    private Map<String, Object> parseInputs(String toolInput) {
        if (toolInput == null || toolInput.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(toolInput, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("[ApiToolCallback] 解析输入参数失败: input={}, error={}", toolInput, e.getMessage());
            return Map.of();
        }
    }
}
