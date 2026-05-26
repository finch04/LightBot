package com.lightbot.workflow.processor;

import com.lightbot.enums.NodeType;
import com.lightbot.workflow.NodeExecutionContext;
import com.lightbot.workflow.NodeExecutionResult;
import com.lightbot.workflow.NodeProcessor;
import com.lightbot.workflow.WorkflowNodeDataUtils;
import com.lightbot.workflow.WorkflowVariableUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程输入节点：将 outputParams 定义的参数写入工作流变量
 * <p>LightBot 当前不支持参考项目的 PAUSE 异步等待，仅在变量缺失时使用默认值</p>
 */
@Slf4j
@Component
public class InputNodeProcessor extends AbstractFlowNodeProcessor implements NodeProcessor {

    @Override
    public NodeType getType() {
        return NodeType.INPUT;
    }

    @Override
    @SuppressWarnings("unchecked")
    public NodeExecutionResult execute(NodeExecutionContext context) {
        Map<String, Object> nodeData = context.getCurrentNodeData() != null
                ? context.getCurrentNodeData() : Map.of();
        Map<String, Object> variables = context.getVariables();
        Map<String, Object> outputs = new HashMap<>();

        List<Map<String, Object>> outputParams = (List<Map<String, Object>>) nodeData.get("outputParams");
        if (outputParams == null) {
            outputParams = (List<Map<String, Object>>) nodeData.get("output_params");
        }

        if (outputParams != null) {
            for (Map<String, Object> param : outputParams) {
                if (param == null) {
                    continue;
                }
                String key = WorkflowNodeDataUtils.parseString(param.get("key"));
                if (key == null || key.isBlank()) {
                    continue;
                }
                Object existing = variables.get(key);
                if (existing != null && !String.valueOf(existing).isBlank()) {
                    outputs.put(key, existing);
                    continue;
                }
                Object defaultValue = param.get("defaultValue");
                if (defaultValue == null) {
                    defaultValue = param.get("default_value");
                }
                if (defaultValue instanceof String str && str.contains("{{")) {
                    defaultValue = WorkflowVariableUtils.resolveValue(str, variables);
                }
                if (defaultValue == null || String.valueOf(defaultValue).isBlank()) {
                    if ("query".equals(key) || "input".equals(key)) {
                        defaultValue = context.getUserInput();
                    }
                }
                if (defaultValue != null) {
                    variables.put(key, defaultValue);
                    outputs.put(key, defaultValue);
                }
            }
        }

        if (outputs.isEmpty()) {
            Object input = variables.getOrDefault("input", context.getUserInput());
            outputs.put("input", input);
            if (input != null) {
                variables.putIfAbsent("input", input);
                variables.putIfAbsent("query", input);
            }
        }

        log.info("[InputNodeProcessor] 流程输入: keys={}", outputs.keySet());

        return NodeExecutionResult.builder()
                .nextNodeId(resolveNextNodeId(context))
                .outputs(outputs)
                .build();
    }
}
