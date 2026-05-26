package com.lightbot.workflow.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.enums.NodeType;
import com.lightbot.service.ToolService;
import com.lightbot.workflow.NodeExecutionContext;
import com.lightbot.workflow.NodeExecutionResult;
import com.lightbot.workflow.NodeProcessor;
import com.lightbot.workflow.WorkflowNodeDataUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具调用节点：执行已注册 Tool
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

        Map<String, Object> args = buildToolArgs(nodeData, context.getVariables());
        String argsJson;
        try {
            argsJson = objectMapper.writeValueAsString(args);
        } catch (Exception e) {
            throw new IllegalArgumentException("工具参数序列化失败: " + e.getMessage(), e);
        }

        log.info("[ToolNodeProcessor] 执行工具: toolId={}, args={}", toolId, argsJson);
        String result = toolService.testTool(toolId, argsJson);

        Map<String, Object> outputs = new HashMap<>();
        outputs.put("output", result);
        outputs.put("toolResult", result);

        return NodeExecutionResult.builder()
                .nextNodeId(resolveNextNodeId(context))
                .outputs(outputs)
                .streamContent(result)
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildToolArgs(Map<String, Object> nodeData, Map<String, Object> variables) {
        Map<String, Object> args = new HashMap<>();
        Object inputParams = nodeData.get("inputParams");
        if (inputParams == null) {
            inputParams = nodeData.get("input_params");
        }
        if (inputParams instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> row)) {
                    continue;
                }
                String key = row.get("key") != null ? row.get("key").toString() : null;
                if (key == null || key.isBlank()) {
                    continue;
                }
                Object value = row.get("value");
                args.put(key, value);
            }
        }
        if (args.isEmpty()) {
            Long toolId = WorkflowNodeDataUtils.parseLongId(nodeData.get("toolId"));
            if (toolId != null) {
                Map<String, Object> example = toolService.getExampleParams(toolId);
                if (example != null) {
                    args.putAll(example);
                }
            }
            if (variables != null) {
                args.putIfAbsent("query", variables.get("query"));
                args.putIfAbsent("input", variables.getOrDefault("input", variables.get("query")));
            }
        }
        return args;
    }
}
