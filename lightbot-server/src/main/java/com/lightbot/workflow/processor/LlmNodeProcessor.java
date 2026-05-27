package com.lightbot.workflow.processor;

import com.lightbot.enums.NodeType;
import com.lightbot.model.ModelFactory;
import com.lightbot.util.LlmTraceContext;
import com.lightbot.workflow.NodeExecutionContext;
import com.lightbot.workflow.NodeExecutionResult;
import com.lightbot.workflow.NodeProcessor;
import com.lightbot.workflow.WorkflowPromptUtils;
import com.lightbot.workflow.WorkflowEdge;
import com.lightbot.workflow.WorkflowNodeDataUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM节点处理器
 * <p>调用大模型生成响应</p>
 *
 * @author finch
 * @since 2026-05-24
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmNodeProcessor implements NodeProcessor {

    private final ModelFactory modelFactory;

    @Override
    public NodeType getType() {
        return NodeType.LLM;
    }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext context) {
        // 1. 从节点数据获取配置
        Map<String, Object> nodeData = context.getCurrentNodeData();
        if (nodeData == null) {
            nodeData = new HashMap<>();
        }

        // 2. 获取提供商 ID 与具体模型名
        Long providerId = resolveProviderId(nodeData, context.getAgent().getConfig());
        if (providerId == null) {
            log.warn("[LlmNodeProcessor] 未配置 providerId，节点ID={}, nodeDataKeys={}",
                    context.getCurrentNodeId(), nodeData.keySet());
            String errMsg = "LLM节点未配置模型提供商，请在节点配置中选择提供商和模型";
            return NodeExecutionResult.builder()
                    .streamContent(errMsg)
                    .finished(false)
                    .build();
        }

        String modelName = resolveModelName(nodeData);

        // 3. 构建 Prompt（系统提示词 + 用户提示词）
        String promptTemplate = String.valueOf(nodeData.getOrDefault("promptTemplate", "{{input}}"));
        String userPrompt = WorkflowPromptUtils.render(promptTemplate, context.getVariables());
        String sysPromptRaw = WorkflowNodeDataUtils.parseString(nodeData.get("sysPrompt"));
        String systemPrompt = sysPromptRaw != null
                ? WorkflowPromptUtils.render(sysPromptRaw, context.getVariables()) : null;

        // 4. 构建 ChatOptions
        Map<String, Object> configParams = new HashMap<>();
        if (modelName != null && !modelName.isEmpty()) {
            configParams.put("modelId", modelName);
        }
        ChatOptions chatOptions = modelFactory.buildChatOptions(providerId, configParams);

        // 5. 调用 LLM
        ChatModel chatModel = modelFactory.getChatModel(providerId);
        List<Message> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new SystemMessage(systemPrompt));
        }
        messages.add(new UserMessage(userPrompt));
        ChatResponse response = LlmTraceContext.callWithoutTrace(() ->
                chatModel.call(new Prompt(messages, chatOptions)));
        String llmOutput = response.getResult().getOutput().getText();

        // 6. 获取下一个节点
        List<WorkflowEdge> outEdges = context.getWorkflow().getOutEdges(context.getCurrentNodeId());
        String nextNodeId = outEdges.isEmpty() ? null : outEdges.get(0).getTarget();

        // 7. 构建输出
        Map<String, Object> outputs = new HashMap<>();
        outputs.put("llmOutput", llmOutput);

        log.info("[LlmNodeProcessor] LLM调用完成: nodeId={}, outputLength={}",
                context.getCurrentNodeId(), llmOutput.length());

        return NodeExecutionResult.builder()
                .nextNodeId(nextNodeId)
                .outputs(outputs)
                .streamContent(llmOutput)
                .finished(false)
                .build();
    }

    /**
     * 解析 Agent.config JSON
     */
    private Long resolveProviderId(Map<String, Object> nodeData, String agentConfigJson) {
        Long providerId = WorkflowNodeDataUtils.parseLongId(nodeData.get("providerId"));
        if (providerId != null) {
            return providerId;
        }
        if (nodeData.get("modelId") instanceof Number) {
            return ((Number) nodeData.get("modelId")).longValue();
        }
        Map<String, Object> agentConfig = parseAgentConfig(agentConfigJson);
        return WorkflowNodeDataUtils.parseLongId(agentConfig.get("providerId"));
    }

    /**
     * 解析具体模型名称（字符串 modelId 或 model 字段）
     */
    private String resolveModelName(Map<String, Object> nodeData) {
        String model = WorkflowNodeDataUtils.parseString(nodeData.get("model"));
        if (model != null) {
            return model;
        }
        Object modelIdVal = nodeData.get("modelId");
        if (modelIdVal instanceof String && !((String) modelIdVal).isEmpty()) {
            return (String) modelIdVal;
        }
        return WorkflowNodeDataUtils.parseString(nodeData.get("modelName"));
    }

    /**
     * 解析 Agent.config JSON
     */
    private Map<String, Object> parseAgentConfig(String configJson) {
        if (configJson == null || configJson.isEmpty()) {
            return new HashMap<>();
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(configJson, Map.class);
        } catch (Exception e) {
            log.warn("[LlmNodeProcessor] 解析Agent.config失败: {}", e.getMessage());
            return new HashMap<>();
        }
    }
}