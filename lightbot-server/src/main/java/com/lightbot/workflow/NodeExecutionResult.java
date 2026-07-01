package com.lightbot.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 工作流节点执行结果
 * <p>节点执行完成后返回的结果，包含下一个节点ID和本节点输出</p>
 *
 * @author finch
 * @since 2026-05-24
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeExecutionResult {

    /**
     * 下一个节点 ID
     * <p>条件节点会根据分支选择不同的下一个节点</p>
     */
    private String nextNodeId;

    /**
     * 本节点输出数据
     */
    private Map<String, Object> outputs;

    /**
     * 流式内容（LLM节点）
     */
    private String streamContent;

    /**
     * 是否结束（END节点返回 true）
     */
    private boolean finished;

    /**
     * 链路追踪专用数据（不进入工作流变量，仅供 trace 记录）
     */
    private Map<String, Object> traceData;

    /**
     * 是否挂起等待人工确认（HITL）
     */
    private boolean suspended;

    /**
     * 挂起时的表单/提示信息（供前端渲染）
     */
    private Map<String, Object> suspendPayload;
}