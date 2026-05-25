package com.lightbot.workflow.processor;

import com.lightbot.workflow.NodeExecutionContext;
import com.lightbot.workflow.NodeExecutionResult;
import com.lightbot.workflow.WorkflowEdge;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用 DAG 节点处理基类：取第一条出边作为下一节点
 */
public abstract class AbstractFlowNodeProcessor {

    protected String resolveNextNodeId(NodeExecutionContext context) {
        List<WorkflowEdge> outEdges = context.getWorkflow().getOutEdges(context.getCurrentNodeId());
        return outEdges.isEmpty() ? null : outEdges.get(0).getTarget();
    }

    protected NodeExecutionResult passThrough(NodeExecutionContext context, String outputKey, Object outputValue) {
        Map<String, Object> outputs = new HashMap<>();
        outputs.put(outputKey, outputValue);
        return NodeExecutionResult.builder()
                .nextNodeId(resolveNextNodeId(context))
                .outputs(outputs)
                .finished(false)
                .build();
    }
}
