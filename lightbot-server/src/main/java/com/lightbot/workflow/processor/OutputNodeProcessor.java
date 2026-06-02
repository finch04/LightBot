package com.lightbot.workflow.processor;

import com.lightbot.enums.NodeType;
import com.lightbot.workflow.NodeExecutionContext;
import com.lightbot.workflow.NodeExecutionResult;
import com.lightbot.workflow.NodeProcessor;
import com.lightbot.workflow.WorkflowPromptUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 流程输出节点：渲染 output 模板作为工作流输出
 */
@Slf4j
@Component
public class OutputNodeProcessor extends AbstractFlowNodeProcessor implements NodeProcessor {

    @Override
    public NodeType getType() {
        return NodeType.OUTPUT;
    }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext context) {
        Map<String, Object> nodeData = context.getCurrentNodeData() != null
                ? context.getCurrentNodeData() : Map.of();
        String template = String.valueOf(nodeData.getOrDefault("output", "{{input}}"));
        String output = WorkflowPromptUtils.render(template, context.getVariables());

        Map<String, Object> outputs = new HashMap<>();
        outputs.put("output", output);

        log.info("[OutputNodeProcessor] 输出节点完成: nodeId={}, length={}",
                context.getCurrentNodeId(), output.length());

        return NodeExecutionResult.builder()
                .nextNodeId(resolveNextNodeId(context))
                .outputs(outputs)
                .streamContent(output)
                .finished(false)
                .build();
    }
}
