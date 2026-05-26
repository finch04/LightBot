package com.lightbot.workflow.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.enums.NodeType;
import com.lightbot.workflow.NodeExecutionContext;
import com.lightbot.workflow.NodeExecutionResult;
import com.lightbot.workflow.NodeProcessor;
import com.lightbot.workflow.WorkflowGroupUtils;
import com.lightbot.workflow.WorkflowNode;
import com.lightbot.workflow.WorkflowSubgraphExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;

import java.util.List;
import java.util.Map;

/**
 * 循环/批处理容器执行基类
 */
@Slf4j
abstract class AbstractGroupContainerProcessor extends AbstractFlowNodeProcessor implements NodeProcessor {

    protected final WorkflowSubgraphExecutor subgraphExecutor;
    protected final ObjectMapper objectMapper;

    protected AbstractGroupContainerProcessor(@Lazy WorkflowSubgraphExecutor subgraphExecutor,
                                              ObjectMapper objectMapper) {
        this.subgraphExecutor = subgraphExecutor;
        this.objectMapper = objectMapper;
    }

    protected NodeExecutionResult executeContainer(NodeExecutionContext context, NodeType startType,
                                                   NodeType endType, boolean parallel) {
        String groupId = resolveGroupId(context, startType);
        if (groupId == null) {
            return passThrough(context, "output", context.getVariables().get("input"));
        }

        WorkflowNode container = WorkflowGroupUtils.findGroupContainer(context.getWorkflow(), groupId);
        if (container == null || container.getData() == null) {
            throw new IllegalArgumentException("未找到循环/批处理容器配置: " + groupId);
        }
        Map<String, Object> containerData = container.getData();

        WorkflowNode startNode = WorkflowGroupUtils.findBuiltinNode(context.getWorkflow(), groupId, startType);
        WorkflowNode endNode = WorkflowGroupUtils.findBuiltinNode(context.getWorkflow(), groupId, endType);
        if (startNode == null || endNode == null) {
            throw new IllegalArgumentException("容器缺少内置起止节点: " + groupId);
        }

        Map<String, List<Object>> itemListMap = WorkflowGroupUtils.buildItemListMap(
                containerData, context.getVariables(), objectMapper);
        int count = parallel
                ? WorkflowGroupUtils.resolveBatchCount(containerData, itemListMap)
                : WorkflowGroupUtils.resolveLoopCount(containerData, itemListMap);

        log.info("[{}] 开始执行: groupId={}, count={}, parallel={}",
                getClass().getSimpleName(), groupId, count, parallel);

        boolean continueOnError = WorkflowGroupUtils.isContinueOnError(containerData);
        List<Map<String, Object>> snapshots;
        if (parallel && count > 0) {
            int concurrent = WorkflowGroupUtils.resolveConcurrentSize(containerData);
            snapshots = subgraphExecutor.runParallelIterations(
                    context, groupId, startNode.getId(), endNode.getId(),
                    count, itemListMap, concurrent, continueOnError);
        } else {
            snapshots = subgraphExecutor.runSequentialIterations(
                    context, groupId, startNode.getId(), endNode.getId(),
                    count, itemListMap, continueOnError);
        }

        Map<String, Object> outputs = WorkflowGroupUtils.aggregateOutputs(containerData, snapshots);
        context.getVariables().putAll(outputs);

        String exitNodeId = WorkflowGroupUtils.findExitNodeAfterEnd(
                context.getWorkflow(), endNode.getId(), groupId);
        if (exitNodeId == null) {
            exitNodeId = resolveNextNodeId(context);
        }

        log.info("[{}] 执行完成: groupId={}, iterations={}, exit={}",
                getClass().getSimpleName(), groupId, count, exitNodeId);

        return NodeExecutionResult.builder()
                .nextNodeId(exitNodeId)
                .outputs(outputs)
                .finished(false)
                .build();
    }

    private String resolveGroupId(NodeExecutionContext context, NodeType startType) {
        WorkflowNode current = context.getWorkflow().getNode(context.getCurrentNodeId());
        if (current == null) {
            return null;
        }
        if (current.getType() == NodeType.LOOP || current.getType() == NodeType.BATCH) {
            return current.getId();
        }
        if (current.getType() == startType) {
            return WorkflowGroupUtils.getParentNodeId(current);
        }
        return WorkflowGroupUtils.getParentNodeId(current);
    }
}
