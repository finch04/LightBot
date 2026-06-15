package com.lightbot.workflow;

import com.lightbot.entity.Agent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 工作流节点执行上下文
 * <p>包含执行过程中需要的所有数据和变量</p>
 *
 * @author finch
 * @since 2026-05-24
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeExecutionContext {

    /**
     * Agent ID
     */
    private Long agentId;

    /**
     * 会话 ID
     */
    private Long sessionId;

    /**
     * 用户输入
     */
    private String userInput;

    /**
     * 节点间传递的变量
     */
    private Map<String, Object> variables;

    /**
     * 各节点输出结果（key为节点ID）
     */
    private Map<String, Object> nodeOutputs;

    /**
     * 当前节点 ID
     */
    private String currentNodeId;

    /**
     * 当前节点数据（前端配置的 data 字段）
     */
    private Map<String, Object> currentNodeData;

    /**
     * Workflow 定义（所有节点和边）
     */
    private WorkflowDefinition workflow;

    /**
     * Agent 实体
     */
    private Agent agent;

    /**
     * 流式输出回调（LLM 逐 token 回调，非空时启用流式调用）
     */
    @Builder.Default
    private Consumer<String> onStreamChunk = null;

    // ========== 容器子图事件推送 ==========

    /**
     * 工作流事件列表（子图节点执行时追加事件）
     */
    @Builder.Default
    private List<Map<String, Object>> workflowEvents = null;

    /**
     * 实时事件回调（SSE 推送）
     */
    @Builder.Default
    private Consumer<Map<String, Object>> onEvent = null;

    /**
     * 父容器节点 ID（子图节点执行时标识归属）
     */
    @Builder.Default
    private String parentNodeId = null;

    /**
     * 当前迭代序号（从 0 开始，子图节点执行时标识所属迭代）
     */
    @Builder.Default
    private Integer iterationIndex = null;
}