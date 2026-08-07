package com.lightbot.workflow.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.entity.SubAgent;
import com.lightbot.enums.NodeType;
import com.lightbot.service.SubAgentService;
import com.lightbot.service.chat.ChatContext;
import com.lightbot.subagent.SubAgentRuntime;
import com.lightbot.workflow.NodeExecutionContext;
import com.lightbot.workflow.NodeExecutionResult;
import com.lightbot.workflow.NodeProcessor;
import com.lightbot.workflow.WorkflowMappingUtils;
import com.lightbot.workflow.WorkflowNodeDataUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SubAgent 节点：在工作流 DAG 中同步委派已启用的 SubAgent，输出 reply / threadId
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubAgentNodeProcessor extends AbstractFlowNodeProcessor implements NodeProcessor {

    private final SubAgentService subAgentService;
    private final SubAgentRuntime subAgentRuntime;
    private final ObjectMapper objectMapper;

    @Override
    public NodeType getType() {
        return NodeType.SUB_AGENT;
    }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext context) {
        Map<String, Object> nodeData = context.getCurrentNodeData() != null
                ? context.getCurrentNodeData() : Map.of();

        // 1. 校验并加载 SubAgent（节点级选择，不依赖主 Agent 绑定列表）
        Long subAgentId = WorkflowNodeDataUtils.parseLongId(nodeData.get("subAgentId"));
        if (subAgentId == null) {
            throw new IllegalArgumentException("请选择 SubAgent");
        }
        SubAgent subAgent = subAgentService.getById(subAgentId);
        if (subAgent == null) {
            throw new IllegalArgumentException("SubAgent 不存在: " + subAgentId);
        }
        if (!Integer.valueOf(1).equals(subAgent.getEnabled())) {
            throw new IllegalArgumentException("SubAgent 未启用: " + subAgent.getName());
        }

        // 2. 渲染任务描述（支持 {{变量}} / {{nodeId.field}}）
        String task = resolveTask(nodeData, context);
        if (task == null || task.isBlank()) {
            throw new IllegalArgumentException("SubAgent 任务描述不能为空");
        }

        // 3. 构建最小 ChatContext，供模型继承（SubAgent 自带 modelId 时优先用自有配置）
        ChatContext chatContext = buildInheritChatContext(context);
        String requestId = "wf-sa-" + UUID.randomUUID();
        String parentThreadId = context.getSessionId() != null
                ? String.valueOf(context.getSessionId()) : null;

        log.info("[SubAgentNodeProcessor] 委派 SubAgent: id={}, name={}, nodeId={}, taskLen={}",
                subAgentId, subAgent.getName(), context.getCurrentNodeId(), task.length());

        SubAgentRuntime.SubAgentResult result = subAgentRuntime.run(
                subAgent, task, requestId, null, parentThreadId, chatContext);

        String reply = result.reply() != null ? result.reply() : "";
        if (reply.startsWith("SubAgent 执行失败:") || "SubAgent 不存在".equals(reply)) {
            throw new IllegalArgumentException(reply);
        }

        // 4. 组装出参（固定字段 + outputMappings）
        Map<String, Object> sourceVars = new LinkedHashMap<>();
        sourceVars.put("reply", reply);
        sourceVars.put("output", reply);
        sourceVars.put("result", reply);
        if (result.threadId() != null) {
            sourceVars.put("threadId", result.threadId());
        }
        Map<String, Object> outputs = WorkflowMappingUtils.applyOutputMappings(
                nodeData, sourceVars, "reply", reply);
        outputs.putIfAbsent("reply", reply);
        outputs.putIfAbsent("output", reply);
        if (result.threadId() != null) {
            outputs.putIfAbsent("threadId", result.threadId());
        }
        String displayName = WorkflowNodeDataUtils.parseString(nodeData.get("subAgentName"));
        if (displayName == null || displayName.isBlank()) {
            displayName = subAgent.getDisplayName() != null ? subAgent.getDisplayName() : subAgent.getName();
        }
        outputs.put("subAgentName", displayName);
        outputs.put("subAgentId", String.valueOf(subAgentId));

        String streamContent = Boolean.TRUE.equals(nodeData.get("streamSwitch")) ? reply : null;
        return NodeExecutionResult.builder()
                .nextNodeId(resolveNextNodeId(context))
                .outputs(outputs)
                .streamContent(streamContent)
                .build();
    }

    private String resolveTask(Map<String, Object> nodeData, NodeExecutionContext context) {
        String taskTemplate = WorkflowNodeDataUtils.parseString(nodeData.get("task"));
        if (taskTemplate == null || taskTemplate.isBlank()) {
            // 兼容：未配置 task 时从 inputMappings.task 或 query/input 兜底
            Map<String, Object> inputs = WorkflowMappingUtils.buildInputArgs(nodeData, context);
            Object mapped = inputs.get("task");
            if (mapped == null) {
                mapped = inputs.get("query");
            }
            if (mapped == null) {
                mapped = inputs.get("input");
            }
            if (mapped != null) {
                return String.valueOf(mapped);
            }
            Map<String, Object> vars = context.getVariables() != null ? context.getVariables() : Map.of();
            Object q = vars.get("query");
            if (q == null) {
                q = vars.get("input");
            }
            return q != null ? String.valueOf(q) : context.getUserInput();
        }
        Object rendered = WorkflowMappingUtils.resolveTemplateValue(taskTemplate, context);
        return rendered != null ? String.valueOf(rendered) : "";
    }

    private ChatContext buildInheritChatContext(NodeExecutionContext context) {
        ChatContext chatContext = new ChatContext();
        chatContext.setSessionId(context.getSessionId());
        Map<String, Object> configMap = parseAgentConfig(context.getAgentConfig());
        chatContext.setConfigMap(configMap);
        Long providerId = WorkflowNodeDataUtils.parseLongId(configMap.get("providerId"));
        if (providerId != null) {
            chatContext.setProviderId(providerId);
        }
        return chatContext;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseAgentConfig(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(configJson, Map.class);
        } catch (Exception e) {
            log.warn("[SubAgentNodeProcessor] 解析 Agent.config 失败: {}", e.getMessage());
            return new HashMap<>();
        }
    }
}
