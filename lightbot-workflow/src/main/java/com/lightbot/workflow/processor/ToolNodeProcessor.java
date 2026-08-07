package com.lightbot.workflow.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.constant.ToolResultPrefixes;
import com.lightbot.enums.NodeType;
import com.lightbot.service.ToolService;
import com.lightbot.tool.builtin.PresentBusinessPageTool;
import com.lightbot.workflow.NodeExecutionContext;
import com.lightbot.workflow.NodeExecutionResult;
import com.lightbot.workflow.NodeProcessor;
import com.lightbot.workflow.WorkflowHitlPayloadBuilder;
import com.lightbot.workflow.WorkflowMappingUtils;
import com.lightbot.workflow.WorkflowNodeDataUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具调用节点：执行已注册 Tool，支持 inputMappings / outputMappings
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolNodeProcessor extends AbstractFlowNodeProcessor implements NodeProcessor {

    private final ToolService toolService;
    private final ObjectMapper objectMapper;

    @Override
    public NodeType getType() {
        return NodeType.TOOL;
    }

    @Override
    @SuppressWarnings("unchecked")
    public NodeExecutionResult execute(NodeExecutionContext context) {
        Map<String, Object> nodeData = context.getCurrentNodeData() != null
                ? context.getCurrentNodeData() : Map.of();

        Long toolId = WorkflowNodeDataUtils.parseLongId(nodeData.get("toolId"));
        if (toolId == null) {
            throw new IllegalArgumentException("工具节点未配置 toolId");
        }

        Map<String, Object> args = buildToolArgs(nodeData, context, toolId);
        String argsJson;
        try {
            argsJson = objectMapper.writeValueAsString(args);
        } catch (Exception e) {
            throw new IllegalArgumentException("工具参数序列化失败: " + e.getMessage(), e);
        }

        log.info("[ToolNodeProcessor] 执行工具: toolId={}, args={}", toolId, argsJson);
        String rawResult = toolService.testTool(toolId, argsJson);
        if (ToolResultPrefixes.isError(rawResult)) {
            throw new IllegalArgumentException("工具执行失败: " + summarizeError(rawResult));
        }

        Map<String, Object> toolVars = parseToolResultVars(rawResult);
        Map<String, Object> outputs = WorkflowMappingUtils.applyOutputMappings(
                nodeData, toolVars, "output", rawResult);
        // outputMappings 仅写入映射字段，保留 tool 原始结果与 ask_user 渲染元数据
        WorkflowHitlPayloadBuilder.preserveAskUserRenderMeta(outputs, toolVars);
        outputs.putIfAbsent("toolResult", toolVars.get("toolResult"));
        outputs.putIfAbsent("output", rawResult);
        outputs.putIfAbsent("toolResultText", rawResult);
        String toolName = WorkflowNodeDataUtils.parseString(nodeData.get("toolName"));
        String displayName = WorkflowNodeDataUtils.parseString(nodeData.get("label"));
        try {
            var toolEntity = toolService.getById(toolId);
            if (toolEntity != null) {
                // 始终使用注册名（ask_user），避免节点 data 误存 displayName 导致 HITL 检测失败
                toolName = toolEntity.getName();
                if (displayName == null || displayName.isBlank()) {
                    displayName = toolEntity.getDisplayName() != null ? toolEntity.getDisplayName() : toolEntity.getName();
                }
            }
        } catch (Exception ignored) {
        }
        if (toolName != null && !toolName.isBlank()) {
            outputs.put("toolName", toolName);
        }
        outputs.put("toolId", String.valueOf(toolId));
        if (displayName != null && !displayName.isBlank()) {
            outputs.put("toolDisplayName", displayName);
        }

        // wait_for_user：ask_user / present_business_page 等与 confirm 节点共用 HITL 挂起
        if (WorkflowHitlPayloadBuilder.hasWaitForUserFlag(rawResult, objectMapper)) {
            Map<String, Object> suspendPayload;
            if (PresentBusinessPageTool.TOOL_NAME.equals(toolName)
                    || WorkflowHitlPayloadBuilder.isBusinessPageResult(rawResult, objectMapper)) {
                suspendPayload = WorkflowHitlPayloadBuilder.fromBusinessPageTool(
                        context.getCurrentNodeId(), rawResult, objectMapper);
                log.info("[ToolNodeProcessor] 业务办理页挂起工作流: nodeId={}, tool={}",
                        context.getCurrentNodeId(), toolName);
            } else {
                WorkflowHitlPayloadBuilder.enrichAskUserPhaseOutputs(outputs, toolVars);
                suspendPayload = WorkflowHitlPayloadBuilder.fromAskUserTool(
                        context.getCurrentNodeId(), rawResult, objectMapper);
                log.info("[ToolNodeProcessor] ask_user 挂起工作流等待用户回答: nodeId={}", context.getCurrentNodeId());
            }
            return NodeExecutionResult.builder()
                    .suspended(true)
                    .nextNodeId(resolveNextNodeId(context))
                    .suspendPayload(suspendPayload)
                    .outputs(outputs)
                    .streamContent(rawResult)
                    .build();
        }

        return NodeExecutionResult.builder()
                .nextNodeId(resolveNextNodeId(context))
                .outputs(outputs)
                .streamContent(rawResult)
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildToolArgs(Map<String, Object> nodeData,
                                              NodeExecutionContext context,
                                              Long toolId) {
        Map<String, Object> variables = context.getVariables() != null
                ? context.getVariables() : Map.of();
        Map<String, Object> args = WorkflowMappingUtils.buildInputArgs(nodeData, context);
        fillMissingToolArgsFromExample(args, toolId);
        if (!args.isEmpty()) {
            return args;
        }

        // 兼容旧版 inputParams 配置
        Object inputParams = nodeData.get("inputParams");
        if (inputParams == null) {
            inputParams = nodeData.get("input_params");
        }
        if (inputParams instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> row)) {
                    continue;
                }
                String key = WorkflowNodeDataUtils.parseString(row.get("key"));
                if (key == null) {
                    continue;
                }
                Object value = row.get("value");
                args.put(key, WorkflowMappingUtils.resolveTemplateValue(value, context));
            }
        }
        if (args.isEmpty()) {
            Map<String, Object> example = toolService.getExampleParams(toolId);
            if (example != null) {
                args.putAll(example);
            }
            args.putIfAbsent("query", variables.get("query"));
            args.putIfAbsent("input", variables.getOrDefault("input", variables.get("query")));
        }
        return args;
    }

    /** 映射未覆盖的必填参数，用工具注册时的 exampleParams 补全（可选参数不补，如 ask_user.options 留空=开放提问） */
    private void fillMissingToolArgsFromExample(Map<String, Object> args, Long toolId) {
        Map<String, Object> example = toolService.getExampleParams(toolId);
        if (example == null || example.isEmpty()) {
            return;
        }
        java.util.Set<String> requiredKeys = toolService.getRequiredParamKeys(toolId);
        if (requiredKeys.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : example.entrySet()) {
            if (!requiredKeys.contains(entry.getKey())) {
                continue;
            }
            Object current = args.get(entry.getKey());
            if (current == null || (current instanceof String s && s.isBlank())) {
                args.put(entry.getKey(), entry.getValue());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseToolResultVars(String rawResult) {
        Map<String, Object> vars = new LinkedHashMap<>();
        if (rawResult == null) {
            return vars;
        }
        vars.put("output", rawResult);
        vars.put("toolResultText", rawResult);
        String trimmed = rawResult.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            vars.put("toolResult", rawResult);
            return vars;
        }
        try {
            Object parsed = objectMapper.readValue(trimmed, Object.class);
            vars.put("toolResult", parsed);
            if (parsed instanceof Map<?, ?> map) {
                map.forEach((k, v) -> vars.put(String.valueOf(k), v));
            }
        } catch (Exception e) {
            log.warn("[ToolNodeProcessor] 工具结果 JSON 解析失败，按原始字符串处理: {}", e.getMessage());
            vars.put("toolResult", rawResult);
        }
        return vars;
    }

    private String summarizeError(String rawResult) {
        if (rawResult == null) {
            return "未知错误";
        }
        String trimmed = rawResult.trim();
        if (trimmed.contains("\"message\"")) {
            try {
                var node = objectMapper.readTree(trimmed);
                if (node.has("message")) {
                    return node.get("message").asText(trimmed);
                }
            } catch (Exception ignored) {
            }
        }
        return trimmed.length() > 200 ? trimmed.substring(0, 200) + "..." : trimmed;
    }
}
