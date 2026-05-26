package com.lightbot.workflow;

import com.lightbot.enums.NodeType;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 循环/批处理容器内子图执行器
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowSubgraphExecutor {

    private final WorkflowNodeRunner nodeRunner;

    /**
     * 执行一次容器内迭代（从 loop_start/batch_start 之后到 loop_end/batch_end）
     */
    public IterationSnapshot executeOneIteration(NodeExecutionContext parentContext,
                                               String groupId,
                                               String startNodeId,
                                               String endNodeId,
                                               Map<String, Object> iterationOverlay) {
        WorkflowDefinition workflow = parentContext.getWorkflow();
        Set<String> childIds = WorkflowGroupUtils.getGroupChildIds(workflow, groupId);

        Map<String, Object> variables = new HashMap<>(parentContext.getVariables());
        if (iterationOverlay != null) {
            variables.putAll(iterationOverlay);
            Object scoped = iterationOverlay.get(groupId);
            if (scoped instanceof Map<?, ?> scopedMap) {
                @SuppressWarnings("unchecked")
                Map<String, Object> scopedVars = (Map<String, Object>) scopedMap;
                variables.putAll(scopedVars);
            }
        }

        Map<String, Object> nodeOutputs = new HashMap<>(parentContext.getNodeOutputs());
        NodeExecutionContext iterContext = NodeExecutionContext.builder()
                .agentId(parentContext.getAgentId())
                .sessionId(parentContext.getSessionId())
                .userInput(parentContext.getUserInput())
                .agent(parentContext.getAgent())
                .workflow(workflow)
                .variables(variables)
                .nodeOutputs(nodeOutputs)
                .build();

        String currentNodeId = WorkflowGroupUtils.findFirstInnerNode(workflow, startNodeId, groupId);
        if (currentNodeId == null) {
            currentNodeId = endNodeId;
        }

        int steps = 0;
        while (currentNodeId != null && steps++ < 200) {
            WorkflowNode node = workflow.getNode(currentNodeId);
            if (node == null || !childIds.contains(currentNodeId)) {
                break;
            }

            iterContext.setCurrentNodeId(currentNodeId);
            iterContext.setCurrentNodeData(node.getData());

            NodeExecutionResult result = nodeRunner.executeNodeInContext(iterContext, currentNodeId);
            if (result == null) {
                throw new IllegalStateException("子图节点执行失败: " + currentNodeId);
            }

            if (node.getType() == NodeType.LOOP_END || node.getType() == NodeType.BATCH_END) {
                break;
            }

            if (result.isFinished()) {
                break;
            }

            currentNodeId = result.getNextNodeId();
            if (currentNodeId != null && !childIds.contains(currentNodeId) && !currentNodeId.equals(endNodeId)) {
                break;
            }
            if (currentNodeId == null) {
                break;
            }
        }

        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("variables", new HashMap<>(iterContext.getVariables()));
        snapshot.put("nodeOutputs", new HashMap<>(iterContext.getNodeOutputs()));
        Object resultVal = iterContext.getVariables().get("result");
        if (resultVal == null && iterContext.getVariables().containsKey("output")) {
            resultVal = iterContext.getVariables().get("output");
        }
        if (resultVal != null) {
            snapshot.put("result", resultVal);
        }
        return IterationSnapshot.builder()
                .success(true)
                .snapshot(snapshot)
                .variables(iterContext.getVariables())
                .build();
    }

    /**
     * 顺序执行多轮迭代
     */
    public List<Map<String, Object>> runSequentialIterations(NodeExecutionContext context,
                                                             String groupId,
                                                             String startNodeId,
                                                             String endNodeId,
                                                             int count,
                                                             Map<String, List<Object>> itemListMap,
                                                             boolean continueOnError) {
        List<Map<String, Object>> snapshots = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            try {
                Map<String, Object> overlay = WorkflowGroupUtils.buildIterationOverlay(
                        groupId, i, itemListMap, context.getVariables());
                IterationSnapshot snap = executeOneIteration(context, groupId, startNodeId, endNodeId, overlay);
                snapshots.add(snap.getSnapshot());
            } catch (Exception e) {
                log.error("[WorkflowSubgraphExecutor] 迭代失败: groupId={}, index={}, error={}",
                        groupId, i, e.getMessage(), e);
                if (!continueOnError) {
                    throw new IllegalStateException("循环/批处理第 " + (i + 1) + " 轮失败: " + e.getMessage(), e);
                }
                Map<String, Object> failSnap = new HashMap<>();
                failSnap.put("error", e.getMessage());
                failSnap.put("index", i + 1);
                snapshots.add(failSnap);
            }
        }
        return snapshots;
    }

    /**
     * 并发执行批处理（受 concurrentSize 限制）
     */
    public List<Map<String, Object>> runParallelIterations(NodeExecutionContext context,
                                                           String groupId,
                                                           String startNodeId,
                                                           String endNodeId,
                                                           int count,
                                                           Map<String, List<Object>> itemListMap,
                                                           int concurrentSize,
                                                           boolean continueOnError) {
        int threads = Math.min(Math.max(concurrentSize, 1), 10);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Map<String, Object>>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                final int index = i;
                Callable<Map<String, Object>> task = () -> {
                    Map<String, Object> overlay = WorkflowGroupUtils.buildIterationOverlay(
                            groupId, index, itemListMap, context.getVariables());
                    IterationSnapshot snap = executeOneIteration(
                            context, groupId, startNodeId, endNodeId, overlay);
                    return snap.getSnapshot();
                };
                futures.add(pool.submit(task));
            }
            List<Map<String, Object>> snapshots = new ArrayList<>();
            for (int i = 0; i < futures.size(); i++) {
                try {
                    snapshots.add(futures.get(i).get());
                } catch (Exception e) {
                    log.error("[WorkflowSubgraphExecutor] 批处理任务失败: index={}, error={}", i, e.getMessage(), e);
                    if (!continueOnError) {
                        throw new IllegalStateException("批处理第 " + (i + 1) + " 项失败: " + e.getMessage(), e);
                    }
                    Map<String, Object> failSnap = new HashMap<>();
                    failSnap.put("error", e.getMessage());
                    failSnap.put("index", i + 1);
                    snapshots.add(failSnap);
                }
            }
            return snapshots;
        } finally {
            pool.shutdown();
        }
    }

    @Data
    @Builder
    public static class IterationSnapshot {
        private boolean success;
        private Map<String, Object> snapshot;
        private Map<String, Object> variables;
    }
}
