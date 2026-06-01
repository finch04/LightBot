package com.lightbot.workflow.processor;

import com.lightbot.enums.NodeType;
import com.lightbot.workflow.NodeExecutionContext;
import com.lightbot.workflow.NodeExecutionResult;
import com.lightbot.workflow.NodeProcessor;
import com.lightbot.workflow.WorkflowNodeDataUtils;
import com.lightbot.workflow.WorkflowPromptUtils;
import com.lightbot.workflow.WorkflowVariableUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 变量赋值节点：将右侧值写入指定变量
 */
@Slf4j
@Component
public class VariableNodeProcessor extends AbstractFlowNodeProcessor implements NodeProcessor {

    @Override
    public NodeType getType() {
        return NodeType.VARIABLE;
    }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext context) {
        Map<String, Object> nodeData = context.getCurrentNodeData() != null
                ? context.getCurrentNodeData() : Map.of();
        Map<String, Object> variables = context.getVariables();

        String variableName = WorkflowNodeDataUtils.parseString(nodeData.get("variableName"));
        if (variableName == null || variableName.isBlank()) {
            throw new IllegalArgumentException("变量赋值节点未配置变量名");
        }

        Object rawValue = nodeData.get("variableValue");
        Object resolvedValue;
        if (rawValue == null) {
            resolvedValue = null;
        } else {
            String valueExpr = rawValue.toString();
            Object fromRef = WorkflowVariableUtils.resolveValue(valueExpr, variables);
            resolvedValue = fromRef != null ? fromRef : WorkflowPromptUtils.render(valueExpr, variables);
        }

        variables.put(variableName, resolvedValue);
        log.info("[VariableNodeProcessor] 变量赋值: {}={}, rawValue={}, 当前变量keys={}",
                variableName, resolvedValue, rawValue, variables.keySet());

        Map<String, Object> outputs = new HashMap<>();
        outputs.put(variableName, resolvedValue);

        return NodeExecutionResult.builder()
                .nextNodeId(resolveNextNodeId(context))
                .outputs(outputs)
                .build();
    }
}
