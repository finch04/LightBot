package com.lightbot.workflow;

import com.lightbot.enums.NodeType;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 工作流节点对 Chat 气泡的曝光规则：中间态 LLM 不重复输出，由 output 节点统一对外展示。
 */
public final class WorkflowChatExposure {

    private WorkflowChatExposure() {
    }

    /**
     * 从当前节点下游是否存在可达的 output 节点
     */
    public static boolean hasReachableOutputNode(WorkflowDefinition workflow, String fromNodeId) {
        if (workflow == null || fromNodeId == null || workflow.getEdges() == null) {
            return false;
        }
        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        for (WorkflowEdge edge : workflow.getOutEdges(fromNodeId)) {
            queue.add(edge.getTarget());
        }
        while (!queue.isEmpty()) {
            String nodeId = queue.poll();
            if (!visited.add(nodeId)) {
                continue;
            }
            WorkflowNode node = workflow.getNode(nodeId);
            if (node == null) {
                continue;
            }
            if (node.getType() == NodeType.OUTPUT) {
                return true;
            }
            if (node.getType() == NodeType.END) {
                continue;
            }
            for (WorkflowEdge edge : workflow.getOutEdges(nodeId)) {
                queue.add(edge.getTarget());
            }
        }
        return false;
    }

    /**
     * LLM 是否向 Chat 流式推送 token
     */
    public static boolean shouldLlmStreamChunks(WorkflowDefinition workflow, String nodeId, Map<String, Object> nodeData) {
        if (nodeData == null || !Boolean.TRUE.equals(nodeData.get("enableStreaming"))) {
            return false;
        }
        return shouldLlmExposeToChat(workflow, nodeId);
    }

    /**
     * LLM 结果是否写入 Chat 气泡（流式 chunk 与非流式 streamResult 共用）
     */
    public static boolean shouldLlmExposeToChat(WorkflowDefinition workflow, String nodeId) {
        // 下游有 output 节点时，由 output 统一对外输出，LLM 仅作中间态
        return !hasReachableOutputNode(workflow, nodeId);
    }

    /**
     * 是否将节点 streamContent 累加入 Chat 可见结果
     */
    public static boolean shouldAppendStreamContentToChat(WorkflowDefinition workflow, WorkflowNode node,
                                                          NodeExecutionResult result) {
        if (workflow == null || node == null || result == null || result.getStreamContent() == null) {
            return false;
        }
        Map<String, Object> data = node.getData() != null ? node.getData() : Map.of();
        NodeType type = node.getType();
        if (type == NodeType.LLM) {
            return shouldLlmExposeToChat(workflow, node.getId());
        }
        if (type == NodeType.OUTPUT) {
            return isOutputStreamSwitchEnabled(data);
        }
        if (type == NodeType.APP_COMPONENT || type == NodeType.SUB_AGENT) {
            return Boolean.TRUE.equals(data.get("streamSwitch"));
        }
        return false;
    }

    /** output 节点 streamSwitch，默认 true */
    public static boolean isOutputStreamSwitchEnabled(Map<String, Object> nodeData) {
        if (nodeData == null) {
            return true;
        }
        Object sw = nodeData.get("streamSwitch");
        if (sw == null) {
            return true;
        }
        return Boolean.TRUE.equals(sw);
    }
}
