package com.lightbot.workflow;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工作流边定义
 * <p>节点之间的连接关系</p>
 *
 * @author finch
 * @since 2026-05-24
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkflowEdge {

    /**
     * 边 ID
     */
    private String id;

    /**
     * 源节点 ID
     */
    private String source;

    /**
     * 目标节点 ID
     */
    private String target;

    /**
     * 边标签（条件分支的标签）
     */
    private String label;

    /**
     * 源连接点（如 in / out / {nodeId}_{branchId} / {nodeId}_default）
     */
    private String sourceHandle;

    /**
     * 目标连接点（通常为 in）
     */
    private String targetHandle;
}
