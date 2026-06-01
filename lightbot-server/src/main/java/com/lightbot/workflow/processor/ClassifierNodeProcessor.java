package com.lightbot.workflow.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.enums.NodeType;
import com.lightbot.model.ModelFactory;
import com.lightbot.util.LlmTraceContext;
import com.lightbot.workflow.NodeExecutionContext;
import com.lightbot.workflow.NodeExecutionResult;
import com.lightbot.workflow.NodeProcessor;
import com.lightbot.workflow.WorkflowEdge;
import com.lightbot.workflow.WorkflowPromptUtils;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 意图分类节点：调用大模型判断用户意图，并路由到对应分支出口
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClassifierNodeProcessor extends AbstractFlowNodeProcessor implements NodeProcessor {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{([^}]+)}}");
    private static final String DEFAULT_INTENT_ID = "default";

    private final ModelFactory modelFactory;
    private final ObjectMapper objectMapper;

    @Override
    public NodeType getType() {
        return NodeType.CLASSIFIER;
    }

    @Override
    @SuppressWarnings("unchecked")
    public NodeExecutionResult execute(NodeExecutionContext context) {
        Map<String, Object> nodeData = context.getCurrentNodeData() != null
                ? context.getCurrentNodeData() : Map.of();

        String inputText = resolveInputText(nodeData, context.getVariables(), context.getUserInput());
        if (inputText == null || inputText.isBlank()) {
            return buildResult(context, DEFAULT_INTENT_ID, "其他意图", "", "输入文本为空，走默认分支");
        }

        Long providerId = resolveProviderId(nodeData, context.getAgent().getConfig());
        if (providerId == null) {
            String err = "意图分类节点未配置模型提供商，请在节点配置中选择提供商和模型";
            log.warn("[ClassifierNodeProcessor] {}", err);
            return buildResult(context, DEFAULT_INTENT_ID, "其他意图", "", err);
        }

        String modelName = resolveModelName(nodeData);
        List<Map<String, Object>> conditions = (List<Map<String, Object>>) nodeData.get("conditions");
        if (conditions == null || conditions.isEmpty()) {
            return buildResult(context, DEFAULT_INTENT_ID, "其他意图", "", "未配置意图分类项，走默认分支");
        }

        String modeSwitch = nodeData.get("mode_switch") != null
                ? nodeData.get("mode_switch").toString() : "efficient";
        boolean advancedMode = "advanced".equalsIgnoreCase(modeSwitch);
        String instruction = WorkflowNodeDataUtils.parseString(nodeData.get("instruction"));

        try {
            Map<String, Object> configParams = new HashMap<>();
            if (modelName != null && !modelName.isEmpty()) {
                configParams.put("modelId", modelName);
            }
            ChatOptions chatOptions = modelFactory.buildChatOptions(providerId, configParams);
            ChatModel chatModel = modelFactory.getChatModel(providerId);

            String systemPrompt = buildSystemPrompt(advancedMode);
            String userPrompt = buildUserPrompt(inputText, conditions, instruction, advancedMode);

            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(systemPrompt));
            messages.add(new UserMessage(userPrompt));

            ChatResponse response = LlmTraceContext.callWithoutTrace(() ->
                    chatModel.call(new Prompt(messages, chatOptions)));
            String raw = response.getResult().getOutput().getText();
            ClassificationResult parsed = parseClassificationResult(raw, conditions);

            log.info("[ClassifierNodeProcessor] 分类完成: nodeId={}, intentId={}, subject={}",
                    context.getCurrentNodeId(), parsed.intentId(), parsed.subject());

            return buildResult(context, parsed.intentId(), parsed.subject(), parsed.thought(), raw);
        } catch (Exception e) {
            log.error("[ClassifierNodeProcessor] 意图分类失败: nodeId={}, error={}",
                    context.getCurrentNodeId(), e.getMessage(), e);
            return buildResult(context, DEFAULT_INTENT_ID, "其他意图", "",
                    "意图分类失败: " + e.getMessage());
        }
    }

    private NodeExecutionResult buildResult(NodeExecutionContext context, String intentId,
                                            String subject, String thought, String rawResponse) {
        String matchedIntentId = normalizeIntentId(intentId);
        String sourceHandle = DEFAULT_INTENT_ID.equals(matchedIntentId)
                ? context.getCurrentNodeId() + "_default"
                : context.getCurrentNodeId() + "_" + matchedIntentId;
        String nextNodeId = resolveTargetByHandle(context, sourceHandle);
        if (nextNodeId == null) {
            nextNodeId = resolveDefaultOutEdge(context);
        }

        Map<String, Object> outputs = new HashMap<>();
        outputs.put("subject", subject);
        outputs.put("intentId", matchedIntentId);
        outputs.put("matchedIntentId", matchedIntentId);
        if (thought != null && !thought.isBlank()) {
            outputs.put("thought", thought);
        }
        outputs.put("classificationRaw", rawResponse);

        return NodeExecutionResult.builder()
                .nextNodeId(nextNodeId)
                .outputs(outputs)
                .streamContent(null)
                .finished(false)
                .build();
    }

    private String resolveInputText(Map<String, Object> nodeData, Map<String, Object> variables, String userInput) {
        String inputVariable = WorkflowNodeDataUtils.parseString(nodeData.get("inputVariable"));
        if (inputVariable == null || inputVariable.isBlank()) {
            inputVariable = "{{query}}";
        }
        String rendered = WorkflowPromptUtils.render(inputVariable, variables);
        if (rendered != null && !rendered.isBlank() && !rendered.equals(inputVariable)) {
            return rendered.trim();
        }
        Matcher matcher = VAR_PATTERN.matcher(inputVariable.trim());
        if (matcher.matches()) {
            String key = matcher.group(1).trim();
            Object val = variables != null ? variables.get(key) : null;
            if (val != null && !String.valueOf(val).isBlank()) {
                return String.valueOf(val).trim();
            }
        }
        if (variables != null) {
            Object query = variables.get("query");
            if (query != null && !String.valueOf(query).isBlank()) {
                return String.valueOf(query).trim();
            }
            Object input = variables.get("input");
            if (input != null && !String.valueOf(input).isBlank()) {
                return String.valueOf(input).trim();
            }
        }
        return userInput != null ? userInput.trim() : rendered;
    }

    private String buildSystemPrompt(boolean advancedMode) {
        if (advancedMode) {
            return """
                    你是工作流意图分类器。根据用户输入，从给定意图列表中选择最匹配的一项。
                    必须只返回 JSON，不要输出 markdown 代码块或其它说明文字。
                    JSON 格式：{"intentId":"意图id","subject":"命中的意图描述","thought":"简要推理过程"}
                    若都不匹配，返回 {"intentId":"default","subject":"其他意图","thought":"..."}
                    """;
        }
        return """
                你是工作流意图分类器。根据用户输入，从给定意图列表中选择最匹配的一项。
                必须只返回 JSON，不要输出 markdown 代码块或其它说明文字。
                JSON 格式：{"intentId":"意图id","subject":"命中的意图描述"}
                若都不匹配，返回 {"intentId":"default","subject":"其他意图"}
                """;
    }

    @SuppressWarnings("unchecked")
    private String buildUserPrompt(String inputText, List<Map<String, Object>> conditions,
                                   String instruction, boolean advancedMode) {
        StringBuilder sb = new StringBuilder();
        sb.append("待分类文本：\n").append(inputText).append("\n\n意图列表：\n");
        int idx = 1;
        for (Map<String, Object> cond : conditions) {
            if (cond == null) {
                continue;
            }
            String id = cond.get("id") != null ? cond.get("id").toString() : "";
            if (id.isBlank() || DEFAULT_INTENT_ID.equals(id)) {
                continue;
            }
            String subject = cond.get("subject") != null ? cond.get("subject").toString().trim() : "";
            if (subject.isBlank()) {
                continue;
            }
            sb.append(idx++).append(". intentId=").append(id).append("，描述：").append(subject).append('\n');
        }
        sb.append("\n若以上意图均不匹配，请使用 intentId=default，subject=其他意图。\n");
        if (instruction != null && !instruction.isBlank()) {
            sb.append("\n额外约束：\n").append(instruction).append('\n');
        }
        if (advancedMode) {
            sb.append("\n请逐步思考后给出 JSON 结果。");
        } else {
            sb.append("\n请直接给出 JSON 结果，不要输出思考过程。");
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private ClassificationResult parseClassificationResult(String raw, List<Map<String, Object>> conditions) {
        String jsonText = extractJsonObject(raw);
        String intentId = DEFAULT_INTENT_ID;
        String subject = "其他意图";
        String thought = "";

        if (jsonText != null) {
            try {
                JsonNode node = objectMapper.readTree(jsonText);
                if (node.hasNonNull("intentId")) {
                    intentId = node.get("intentId").asText(DEFAULT_INTENT_ID);
                } else if (node.hasNonNull("id")) {
                    intentId = node.get("id").asText(DEFAULT_INTENT_ID);
                }
                if (node.hasNonNull("subject")) {
                    subject = node.get("subject").asText(subject);
                }
                if (node.hasNonNull("thought")) {
                    thought = node.get("thought").asText("");
                }
            } catch (Exception e) {
                log.warn("[ClassifierNodeProcessor] 解析模型 JSON 失败，尝试文本匹配: {}", e.getMessage());
            }
        }

        intentId = normalizeIntentId(intentId);
        if (DEFAULT_INTENT_ID.equals(intentId)) {
            return new ClassificationResult(DEFAULT_INTENT_ID, "其他意图", thought);
        }

        for (Map<String, Object> cond : conditions) {
            if (cond == null || cond.get("id") == null) {
                continue;
            }
            String id = cond.get("id").toString();
            if (id.equals(intentId)) {
                String configuredSubject = cond.get("subject") != null
                        ? cond.get("subject").toString().trim() : subject;
                return new ClassificationResult(id, configuredSubject.isBlank() ? subject : configuredSubject, thought);
            }
        }

        // 模型可能直接返回 subject 文本，尝试按描述反查 intentId
        for (Map<String, Object> cond : conditions) {
            if (cond == null || cond.get("subject") == null || cond.get("id") == null) {
                continue;
            }
            String configuredSubject = cond.get("subject").toString().trim();
            if (!configuredSubject.isBlank()
                    && (configuredSubject.equals(subject) || subject.contains(configuredSubject))) {
                return new ClassificationResult(cond.get("id").toString(), configuredSubject, thought);
            }
        }

        return new ClassificationResult(DEFAULT_INTENT_ID, "其他意图", thought);
    }

    private String extractJsonObject(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.trim();
        if (text.startsWith("```")) {
            int start = text.indexOf('{');
            int end = text.lastIndexOf('}');
            if (start >= 0 && end > start) {
                return text.substring(start, end + 1);
            }
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private String normalizeIntentId(String intentId) {
        if (intentId == null || intentId.isBlank()) {
            return DEFAULT_INTENT_ID;
        }
        return intentId.trim();
    }

    private String resolveTargetByHandle(NodeExecutionContext context, String sourceHandle) {
        if (sourceHandle == null || sourceHandle.isBlank()) {
            return resolveDefaultOutEdge(context);
        }
        List<WorkflowEdge> outEdges = context.getWorkflow().getOutEdges(context.getCurrentNodeId());
        for (WorkflowEdge edge : outEdges) {
            if (sourceHandle.equals(edge.getSourceHandle())) {
                return edge.getTarget();
            }
        }
        return null;
    }

    private String resolveDefaultOutEdge(NodeExecutionContext context) {
        List<WorkflowEdge> outEdges = context.getWorkflow().getOutEdges(context.getCurrentNodeId());
        return outEdges.isEmpty() ? null : outEdges.get(0).getTarget();
    }

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

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseAgentConfig(String configJson) {
        if (configJson == null || configJson.isEmpty()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(configJson, Map.class);
        } catch (Exception e) {
            log.warn("[ClassifierNodeProcessor] 解析 Agent.config 失败: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    private record ClassificationResult(String intentId, String subject, String thought) {
    }
}
