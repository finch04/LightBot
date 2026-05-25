package com.lightbot.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.entity.Agent;
import com.lightbot.enums.NodeType;
import com.lightbot.service.AgentService;
import com.lightbot.service.AgentVersionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流执行服务
 * <p>DAG 执行引擎，从 START 节点开始，按边连接顺序执行各节点</p>
 *
 * @author finch
 * @since 2026-05-24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowExecutorService {

    private final NodeProcessorRegistry registry;
    private final AgentService agentService;
    private final AgentVersionService agentVersionService;
    private final ObjectMapper objectMapper;

    /**
     * 执行工作流
     *
     * @param agentId   Agent ID
     * @param sessionId 会话 ID
     * @param userInput 用户输入
     * @return 执行结果（最终输出）
     */
    public String execute(Long agentId, Long sessionId, String userInput, List<Map<String, Object>> workflowEvents) {
        // 1. 加载 Agent 和 Workflow 定义
        Agent agent = agentService.getById(agentId);
        if (agent == null) {
            throw new IllegalArgumentException("Agent不存在: " + agentId);
        }

        WorkflowDefinition workflow = agentVersionService.loadWorkflowDefinition(agentId, false);
        if (workflow == null || workflow.getNodes() == null || workflow.getNodes().isEmpty()) {
            log.warn("[WorkflowExecutorService] 工作流未发布或为空: agentId={}", agentId);
            return "工作流尚未发布或为空，请先在编排页发布工作流";
        }

        return executeWithDefinition(agent, workflow, sessionId, userInput, workflowEvents);
    }

    /**
     * 使用指定工作流定义执行（调试/测试）
     */
    public String executeWithDefinition(Agent agent, WorkflowDefinition workflow, Long sessionId,
                                        String userInput, List<Map<String, Object>> workflowEvents) {
        Long agentId = agent.getId();
        if (workflow == null || workflow.getNodes() == null || workflow.getNodes().isEmpty()) {
            return "工作流为空";
        }

        // 1. 构建执行上下文
        NodeExecutionContext context = NodeExecutionContext.builder()
                .agentId(agentId)
                .sessionId(sessionId)
                .userInput(userInput)
                .agent(agent)
                .workflow(workflow)
                .variables(new HashMap<>())
                .nodeOutputs(new HashMap<>())
                .build();

        // 2. 注入全局配置中的会话变量默认值
        applyGlobalConfig(context, workflow.getGlobalConfig());

        // 3. 从 START 节点开始执行 DAG
        String currentNodeId = workflow.getStartNodeId();
        if (currentNodeId == null) {
            log.warn("[WorkflowExecutorService] 未找到 START 节点: agentId={}", agentId);
            return "工作流缺少开始节点";
        }

        StringBuilder result = new StringBuilder();

        // 4. 执行 DAG 链路
        while (currentNodeId != null) {
            WorkflowNode node = workflow.getNode(currentNodeId);
            if (node == null) {
                log.warn("[WorkflowExecutorService] 节点不存在: nodeId={}", currentNodeId);
                break;
            }

            // 设置当前节点信息
            context.setCurrentNodeId(currentNodeId);
            context.setCurrentNodeData(node.getData());

            String nodeLabel = resolveNodeLabel(node);
            String nodeTypeCode = node.getType() != null ? node.getType().getCode() : "";
            final String executingNodeId = currentNodeId;

            // 推送节点开始事件
            if (workflowEvents != null) {
                workflowEvents.add(Map.of(
                        "type", "workflow_node_start",
                        "nodeId", executingNodeId,
                        "nodeType", nodeTypeCode,
                        "nodeLabel", nodeLabel,
                        "contentOffset", 0));
            }

            boolean nodeSuccess = true;
            String completeMessage = "执行完成";
            String nextNodeId = null;

            try {
                NodeProcessor processor = registry.getProcessor(node.getType());
                log.info("[WorkflowExecutorService] 执行节点: nodeId={}, type={}",
                        executingNodeId, node.getType());

                NodeExecutionResult nodeResult = processor.execute(context);

                if (nodeResult.getOutputs() != null) {
                    context.getNodeOutputs().put(executingNodeId, nodeResult.getOutputs());
                    context.getVariables().putAll(nodeResult.getOutputs());
                }

                if (nodeResult.getStreamContent() != null) {
                    result.append(nodeResult.getStreamContent());
                }

                if (nodeResult.isFinished() || node.getType() == NodeType.END) {
                    log.info("[WorkflowExecutorService] 工作流执行完成: agentId={}", agentId);
                    nextNodeId = null;
                } else {
                    nextNodeId = nodeResult.getNextNodeId();
                }
            } catch (Exception e) {
                nodeSuccess = false;
                completeMessage = "执行失败: " + e.getMessage();
                log.error("[WorkflowExecutorService] 节点执行失败: nodeId={}, error={}",
                        executingNodeId, e.getMessage(), e);
                nextNodeId = null;
            }

            if (workflowEvents != null) {
                Map<String, Object> completeEvent = new HashMap<>();
                completeEvent.put("type", "workflow_node_complete");
                completeEvent.put("nodeId", executingNodeId);
                completeEvent.put("nodeType", nodeTypeCode);
                completeEvent.put("nodeLabel", nodeLabel);
                completeEvent.put("message", completeMessage);
                completeEvent.put("success", nodeSuccess);
                completeEvent.put("contentOffset", 0);
                workflowEvents.add(completeEvent);
            }

            currentNodeId = nextNodeId;
        }

        if (workflowEvents != null) {
            workflowEvents.add(Map.of("type", "workflow_complete", "contentOffset", 0));
        }

        // 5. 返回结果
        if (result.isEmpty() && context.getVariables().containsKey("result")) {
            return String.valueOf(context.getVariables().get("result"));
        }

        return result.toString();
    }

    /**
     * 将全局配置中的会话变量写入执行上下文
     */
    @SuppressWarnings("unchecked")
    private void applyGlobalConfig(NodeExecutionContext context, Map<String, Object> globalConfig) {
        if (globalConfig == null) {
            return;
        }
        Object variableConfig = globalConfig.get("variable_config");
        if (!(variableConfig instanceof Map<?, ?> varMap)) {
            return;
        }
        Object conversationParams = varMap.get("conversation_params");
        if (!(conversationParams instanceof List<?> params)) {
            return;
        }
        for (Object item : params) {
            if (item instanceof Map<?, ?> param) {
                Object key = param.get("key");
                if (key != null && !key.toString().isEmpty()) {
                    context.getVariables().putIfAbsent(key.toString(), param.get("default_value"));
                }
            }
        }
    }

    /**
     * 从节点 data 解析展示名称
     */
    private String resolveNodeLabel(WorkflowNode node) {
        if (node.getData() != null && node.getData().containsKey("label")) {
            Object label = node.getData().get("label");
            if (label != null && !label.toString().isEmpty()) {
                return label.toString();
            }
        }
        return node.getType() != null ? node.getType().getDesc() : "节点";
    }
}