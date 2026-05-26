package com.lightbot.workflow.processor;

import com.lightbot.enums.NodeType;
import com.lightbot.workflow.NodeExecutionContext;
import com.lightbot.workflow.NodeExecutionResult;
import com.lightbot.workflow.NodeProcessor;
import com.lightbot.workflow.WorkflowNodeDataUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 应用组件节点：嵌套工作流/Agent 执行（当前版本暂未实现，返回明确错误）
 */
@Slf4j
@Component
public class AppComponentNodeProcessor extends AbstractFlowNodeProcessor implements NodeProcessor {

    @Override
    public NodeType getType() {
        return NodeType.APP_COMPONENT;
    }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext context) {
        Map<String, Object> nodeData = context.getCurrentNodeData() != null
                ? context.getCurrentNodeData() : Map.of();
        String componentCode = WorkflowNodeDataUtils.parseString(nodeData.get("componentCode"));
        String componentType = WorkflowNodeDataUtils.parseString(nodeData.get("componentType"));
        if (componentType == null) {
            componentType = "workflow";
        }

        String message = "应用组件节点暂未实现嵌套执行，componentType="
                + componentType + ", componentCode=" + componentCode;
        log.warn("[AppComponentNodeProcessor] {}", message);

        Map<String, Object> outputs = new HashMap<>();
        outputs.put("output", message);
        outputs.put("error", message);

        return NodeExecutionResult.builder()
                .nextNodeId(resolveNextNodeId(context))
                .outputs(outputs)
                .streamContent(message)
                .build();
    }
}
