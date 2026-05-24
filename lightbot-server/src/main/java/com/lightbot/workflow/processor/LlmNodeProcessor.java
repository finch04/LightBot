package com.lightbot.workflow.processor;

import com.lightbot.enums.NodeType;
import com.lightbot.model.ModelFactory;
import com.lightbot.workflow.NodeExecutionContext;
import com.lightbot.workflow.NodeExecutionResult;
import com.lightbot.workflow.NodeProcessor;
import com.lightbot.workflow.WorkflowEdge;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

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

        // 2. 获取 modelId（从节点配置或 Agent 默认配置）
        Long modelId = null;
        if (nodeData.containsKey("modelId")) {
            modelId = ((Number) nodeData.get("modelId")).longValue();
        } else {
            // 从 Agent.config 获取默认 providerId
            Map<String, Object> agentConfig = parseAgentConfig(context.getAgent().getConfig());
            if (agentConfig.containsKey("providerId")) {
                modelId = ((Number) agentConfig.get("providerId")).longValue();
            }
        }

        if (modelId == null) {
            log.warn("[LlmNodeProcessor] 未配置 modelId，节点ID={}", context.getCurrentNodeId());
            return NodeExecutionResult.builder()
                    .finished(false)
                    .build();
        }

        // 3. 构建 Prompt
        String promptTemplate = (String) nodeData.getOrDefault("promptTemplate", "{{input}}");
        String prompt = renderPrompt(promptTemplate, context.getVariables());

        // 4. 构建 ChatOptions
        Map<String, Object> configParams = new HashMap<>();
        if (nodeData.containsKey("model")) {
            configParams.put("modelId", nodeData.get("model"));
        }
        ChatOptions chatOptions = modelFactory.buildChatOptions(modelId, configParams);

        // 5. 调用 LLM
        ChatModel chatModel = modelFactory.getChatModel(modelId);
        ChatResponse response = chatModel.call(new Prompt(prompt, chatOptions));
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
     * 渲染 Prompt模板，替换变量
     */
    private String renderPrompt(String template, Map<String, Object> variables) {
        String result = template;
        if (variables != null) {
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                String placeholder = "{{" + entry.getKey() + "}}";
                String value = entry.getValue() != null ? entry.getValue().toString() : "";
                result = result.replace(placeholder, value);
            }
        }
        return result;
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