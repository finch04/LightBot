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
import org.springframework.ai.chat.messages.AssistantMessage;
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
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * LLM节点处理器
 * <p>调用大模型生成响应，支持流式输出和对话记忆</p>
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
    @SuppressWarnings("unchecked")
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

        // 3. 构建消息列表（含对话历史）
        List<Message> messages = buildMessages(nodeData, context);

        // 4. 构建 ChatOptions
        Map<String, Object> configParams = new HashMap<>();
        if (modelName != null && !modelName.isEmpty()) {
            configParams.put("modelId", modelName);
        }
        ChatOptions chatOptions = modelFactory.buildChatOptions(providerId, configParams);

        // 5. 调用 LLM（流式 or 非流式）
        ChatModel chatModel = modelFactory.getChatModel(providerId);
        Consumer<String> streamCallback = context.getOnStreamChunk();
        boolean useStream = streamCallback != null && Boolean.TRUE.equals(nodeData.get("enableStreaming"));

        String llmOutput;
        if (useStream) {
            llmOutput = callStream(chatModel, messages, chatOptions, streamCallback);
        } else {
            llmOutput = callSync(chatModel, messages, chatOptions);
        }

        // 6. 获取下一个节点
        List<WorkflowEdge> outEdges = context.getWorkflow().getOutEdges(context.getCurrentNodeId());
        String nextNodeId = outEdges.isEmpty() ? null : outEdges.get(0).getTarget();

        // 7. 构建输出
        Map<String, Object> outputs = new HashMap<>();
        outputs.put("llmOutput", llmOutput);

        log.info("[LlmNodeProcessor] LLM调用完成: nodeId={}, stream={}, outputLength={}",
                context.getCurrentNodeId(), useStream, llmOutput.length());

        return NodeExecutionResult.builder()
                .nextNodeId(nextNodeId)
                .outputs(outputs)
                .streamContent(llmOutput)
                .finished(false)
                .build();
    }

    /**
     * 构建完整消息列表：系统提示词 + 对话历史 + 用户提示词
     */
    @SuppressWarnings("unchecked")
    private List<Message> buildMessages(Map<String, Object> nodeData, NodeExecutionContext context) {
        List<Message> messages = new ArrayList<>();

        // 系统提示词
        String sysPromptRaw = WorkflowNodeDataUtils.parseString(nodeData.get("sysPrompt"));
        if (sysPromptRaw != null) {
            String systemPrompt = WorkflowPromptUtils.render(sysPromptRaw, context.getVariables());
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                messages.add(new SystemMessage(systemPrompt));
            }
        }

        // 对话历史：从 history_list 变量中提取，构建为 Message 对象
        Object historyObj = context.getVariables().get("history_list");
        if (historyObj instanceof List<?> historyList) {
            for (Object item : historyList) {
                if (item instanceof Map<?, ?> map) {
                    String role = map.get("role") != null ? map.get("role").toString() : "user";
                    String content = map.get("content") != null ? map.get("content").toString() : "";
                    if (content.isBlank()) continue;
                    if ("assistant".equals(role)) {
                        messages.add(new AssistantMessage(content));
                    } else {
                        messages.add(new UserMessage(content));
                    }
                }
            }
        }

        // 用户提示词（模板渲染）
        String promptTemplate = String.valueOf(nodeData.getOrDefault("promptTemplate", "{{input}}"));
        String userPrompt = WorkflowPromptUtils.render(promptTemplate, context.getVariables());
        messages.add(new UserMessage(userPrompt));

        return messages;
    }

    /**
     * 同步调用 LLM
     */
    private String callSync(ChatModel chatModel, List<Message> messages, ChatOptions chatOptions) {
        ChatResponse response = LlmTraceContext.callWithoutTrace(() ->
                chatModel.call(new Prompt(messages, chatOptions)));
        return response.getResult().getOutput().getText();
    }

    /**
     * 流式调用 LLM，逐 token 回调
     */
    private String callStream(ChatModel chatModel, List<Message> messages,
                              ChatOptions chatOptions, Consumer<String> onChunk) {
        StringBuilder full = new StringBuilder();
        chatModel.stream(new Prompt(messages, chatOptions))
                .doOnError(e -> log.error("[LlmNodeProcessor] 流式调用异常: {}", e.getMessage(), e))
                .toStream()
                .forEach(response -> {
                    if (response.getResult() == null || response.getResult().getOutput() == null) {
                        return;
                    }
                    String text = response.getResult().getOutput().getText();
                    if (text != null && !text.isEmpty()) {
                        full.append(text);
                        onChunk.accept(text);
                    }
                });
        return full.toString();
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
