package com.lightbot.workflow;

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
}