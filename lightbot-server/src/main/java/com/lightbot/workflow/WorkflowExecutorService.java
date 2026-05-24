package com.lightbot.workflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.entity.Agent;
import com.lightbot.enums.NodeType;
import com.lightbot.service.AgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
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
    private final ObjectMapper objectMapper;

    /**
     * 执行工作流
     *
     * @param agentId   Agent ID
     * @param sessionId 会话 ID
     * @param userInput 用户输入
     * @return 执行结果（最终输出）
     */
    public String execute(Long agentId, Long sessionId, String userInput) {
        // 1. 加载 Agent 和 Workflow 定义
        Agent agent = agentService.getById(agentId);
        if (agent == null) {
            throw new IllegalArgumentException("Agent不存在: " + agentId);
        }

        WorkflowDefinition workflow = parseWorkflow(agent.getConfig());
        if (workflow == null || workflow.getNodes() == null || workflow.getNodes().isEmpty()) {
            log.warn("[WorkflowExecutorService] 工作流为空: agentId={}", agentId);
            return "工作流未配置，请先编辑工作流节点";
        }

        // 2. 构建执行上下文
        NodeExecutionContext context = NodeExecutionContext.builder()
                .agentId(agentId)
                .sessionId(sessionId)
                .userInput(userInput)
                .agent(agent)
                .workflow(workflow)
                .variables(new HashMap<>())
                .nodeOutputs(new HashMap<>())
                .build();

        // 3. 从 START 节点开始执行
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

            // 获取处理器并执行
            NodeProcessor processor = registry.getProcessor(node.getType());
            log.info("[WorkflowExecutorService] 执行节点: nodeId={}, type={}",
                    currentNodeId, node.getType());

            NodeExecutionResult nodeResult = processor.execute(context);

            // 保存节点输出
            if (nodeResult.getOutputs() != null) {
                context.getNodeOutputs().put(currentNodeId, nodeResult.getOutputs());
                context.getVariables().putAll(nodeResult.getOutputs());
            }

            // 收集流式内容（LLM节点）
            if (nodeResult.getStreamContent() != null) {
                result.append(nodeResult.getStreamContent());
            }

            // END节点结束
            if (nodeResult.isFinished() || node.getType() == NodeType.END) {
                log.info("[WorkflowExecutorService] 工作流执行完成: agentId={}", agentId);
                break;
            }

            // 下一个节点
            currentNodeId = nodeResult.getNextNodeId();
        }

        // 5. 返回结果
        if (result.isEmpty() && context.getVariables().containsKey("result")) {
            return String.valueOf(context.getVariables().get("result"));
        }

        return result.toString();
    }

    /**
     * 解析 Agent.config 中的 workflow 定义
     *
     * @param configJson Agent.config JSON
     * @return WorkflowDefinition
     */
    private WorkflowDefinition parseWorkflow(String configJson) {
        if (configJson == null || configJson.isEmpty()) {
            return null;
        }

        try {
            Map<String, Object> config = objectMapper.readValue(configJson, new TypeReference<Map<String, Object>>() {});
            Object workflowObj = config.get("workflow");
            if (workflowObj == null) {
                return null;
            }

            String workflowJson = objectMapper.writeValueAsString(workflowObj);
            return objectMapper.readValue(workflowJson, WorkflowDefinition.class);
        } catch (Exception e) {
            log.warn("[WorkflowExecutorService] 解析工作流失败: {}", e.getMessage());
            return null;
        }
    }
}