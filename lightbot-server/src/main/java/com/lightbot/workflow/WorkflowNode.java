package com.lightbot.workflow;

import com.lightbot.enums.NodeType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 工作流节点定义
 * <p>前端传递的节点数据结构</p>
 *
 * @author finch
 * @since 2026-05-24
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowNode {

    /**
     * 节点 ID（前端生成）
     */
    private String id;

    /**
     * 节点类型
     */
    private NodeType type;

    /**
     * 节点位置（用于前端渲染，x/y 可为小数）
     */
    private Map<String, Object> position;

    /**
     * 节点配置数据（不同类型节点有不同配置）
     */
    private Map<String, Object> data;

    /**
     * 父容器节点 ID（循环/批处理子节点）
     */
    private String parentNode;
}