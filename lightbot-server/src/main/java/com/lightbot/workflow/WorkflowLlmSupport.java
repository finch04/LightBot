package com.lightbot.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.model.ModelFactory;
import com.lightbot.util.LlmTraceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流 LLM 调用封装
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowLlmSupport {

    private final ModelFactory modelFactory;
    private final ObjectMapper objectMapper;

    /**
     * 同步调用大模型并返回文本
     */
    public String chat(Long providerId, String modelName, List<Message> messages) {
        Map<String, Object> configParams = new HashMap<>();
        if (modelName != null && !modelName.isEmpty()) {
            configParams.put("modelId", modelName);
        }
        ChatOptions chatOptions = modelFactory.buildChatOptions(providerId, configParams);
        ChatModel chatModel = modelFactory.getChatModel(providerId);
        ChatResponse response = LlmTraceContext.callWithoutTrace(() ->
                chatModel.call(new Prompt(messages, chatOptions)));
        return response.getResult().getOutput().getText();
    }

    /**
     * 解析节点配置的 providerId
     */
    public Long resolveProviderId(Map<String, Object> nodeData, String agentConfigJson) {
        Long providerId = WorkflowNodeDataUtils.parseLongId(nodeData.get("providerId"));
        if (providerId != null) {
            return providerId;
        }
        if (nodeData.get("modelId") instanceof Number number) {
            return number.longValue();
        }
        Map<String, Object> agentConfig = parseAgentConfig(agentConfigJson);
        return WorkflowNodeDataUtils.parseLongId(agentConfig.get("providerId"));
    }

    /**
     * 解析具体模型名称
     */
    public String resolveModelName(Map<String, Object> nodeData) {
        String model = WorkflowNodeDataUtils.parseString(nodeData.get("model"));
        if (model != null) {
            return model;
        }
        Object modelIdVal = nodeData.get("modelId");
        if (modelIdVal instanceof String str && !str.isEmpty()) {
            return str;
        }
        return WorkflowNodeDataUtils.parseString(nodeData.get("modelName"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseAgentConfig(String configJson) {
        if (configJson == null || configJson.isEmpty()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(configJson, Map.class);
        } catch (Exception e) {
            log.warn("[WorkflowLlmSupport] 解析Agent.config失败: {}", e.getMessage());
            return new HashMap<>();
        }
    }
}
