package com.lightbot.workflow;

import com.lightbot.enums.NodeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 单节点执行器（供主流程与子图复用，避免 SubgraphExecutor 依赖 WorkflowExecutorService 产生循环依赖）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowNodeRunner {

    private final NodeProcessorRegistry registry;

    /**
     * 在已有上下文中执行指定节点并合并输出到 variables
     */
    public NodeExecutionResult executeNodeInContext(NodeExecutionContext context, String nodeId) {
        WorkflowDefinition workflow = context.getWorkflow();
        WorkflowNode node = workflow.getNode(nodeId);
        if (node == null) {
            throw new IllegalArgumentException("节点不存在: " + nodeId);
        }
        context.setCurrentNodeId(nodeId);
        context.setCurrentNodeData(node.getData());

        NodeProcessor processor = registry.getProcessor(node.getType());
        log.debug("[WorkflowNodeRunner] 执行节点: nodeId={}, type={}", nodeId, node.getType());

        NodeExecutionResult nodeResult = processor.execute(context);
        if (nodeResult.getOutputs() != null) {
            context.getNodeOutputs().put(nodeId, nodeResult.getOutputs());
            context.getVariables().putAll(nodeResult.getOutputs());
        }
        if (nodeResult.isFinished() || node.getType() == NodeType.END) {
            return NodeExecutionResult.builder()
                    .nextNodeId(null)
                    .outputs(nodeResult.getOutputs())
                    .streamContent(nodeResult.getStreamContent())
                    .finished(true)
                    .build();
        }
        String nextNodeId = nodeResult.getNextNodeId();
        if (nextNodeId == null) {
            List<WorkflowEdge> outEdges = workflow.getOutEdges(nodeId);
            nextNodeId = outEdges.isEmpty() ? null : outEdges.get(0).getTarget();
            return NodeExecutionResult.builder()
                    .nextNodeId(nextNodeId)
                    .outputs(nodeResult.getOutputs())
                    .streamContent(nodeResult.getStreamContent())
                    .finished(false)
                    .build();
        }
        return nodeResult;
    }
}
