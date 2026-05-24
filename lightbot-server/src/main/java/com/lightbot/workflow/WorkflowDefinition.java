package com.lightbot.workflow;

import com.lightbot.enums.NodeType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 工作流定义
 * <p>从 Agent.config.workflow JSON 解析的工作流结构</p>
 *
 * @author finch
 * @since 2026-05-24
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowDefinition {

    /**
     * 节点列表
     */
    private List<WorkflowNode> nodes;

    /**
     * 边列表（节点连接关系）
     */
    private List<WorkflowEdge> edges;

    /**
     * 根据 ID 获取节点
     *
     * @param nodeId 节点 ID
     * @return 节点定义
     */
    public WorkflowNode getNode(String nodeId) {
        return nodes.stream()
                .filter(n -> n.getId().equals(nodeId))
                .findFirst()
                .orElse(null);
    }

    /**
     * 根据源节点 ID 获取出边
     *
     * @param sourceNodeId 源节点 ID
     * @return 出边列表
     */
    public List<WorkflowEdge> getOutEdges(String sourceNodeId) {
        return edges.stream()
                .filter(e -> e.getSource().equals(sourceNodeId))
                .toList();
    }

    /**
     * 获取开始节点 ID
     *
     * @return 开始节点 ID
     */
    public String getStartNodeId() {
        return nodes.stream()
                .filter(n -> n.getType() == NodeType.START)
                .findFirst()
                .map(WorkflowNode::getId)
                .orElse(null);
    }
}