package com.lightbot.workflow.processor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.enums.NodeType;
import com.lightbot.workflow.NodeExecutionContext;
import com.lightbot.workflow.NodeExecutionResult;
import com.lightbot.workflow.NodeProcessor;
import com.lightbot.workflow.WorkflowLlmSupport;
import com.lightbot.workflow.WorkflowNodeDataUtils;
import com.lightbot.workflow.WorkflowVariableUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 参数提取节点：调用大模型从文本中提取结构化参数
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ParameterExtractorNodeProcessor extends AbstractFlowNodeProcessor implements NodeProcessor {

    private static final String SYSTEM_PROMPT = """
            你是专业的参数提取助手，需要从用户输入中准确提取指定参数。
            若某些必填参数无法提取，请设置 _is_completed 为 false，并在 _reason 中说明原因。
            必须只返回 JSON，不要输出 markdown 代码块或其它说明文字。
            """;

    private final WorkflowLlmSupport llmSupport;
    private final ObjectMapper objectMapper;

    @Override
    public NodeType getType() {
        return NodeType.PARAMETER_EXTRACTOR;
    }

    @Override
    @SuppressWarnings("unchecked")
    public NodeExecutionResult execute(NodeExecutionContext context) {
        Map<String, Object> nodeData = context.getCurrentNodeData() != null
                ? context.getCurrentNodeData() : Map.of();

        String inputText = WorkflowVariableUtils.resolveInputText(
                WorkflowNodeDataUtils.parseString(nodeData.get("inputVariable")),
                context.getVariables(),
                context.getUserInput());
        if (inputText == null || inputText.isBlank()) {
            throw new IllegalArgumentException("参数提取节点输入为空");
        }

        Long providerId = llmSupport.resolveProviderId(nodeData, context.getAgent().getConfig());
        if (providerId == null) {
            throw new IllegalArgumentException("参数提取节点未配置模型提供商");
        }
        String modelName = llmSupport.resolveModelName(nodeData);

        List<Map<String, Object>> extractParams = (List<Map<String, Object>>) nodeData.get("extractParams");
        if (extractParams == null || extractParams.isEmpty()) {
            throw new IllegalArgumentException("参数提取节点未配置 extractParams");
        }

        String instruction = WorkflowNodeDataUtils.parseString(nodeData.get("instruction"));
        String userPrompt = buildUserPrompt(inputText, extractParams, instruction);

        List<Message> messages = new ArrayList<>();
        if (instruction != null && !instruction.isBlank()) {
            messages.add(new SystemMessage(SYSTEM_PROMPT + "\n补充规则：" + instruction));
        } else {
            messages.add(new SystemMessage(SYSTEM_PROMPT));
        }
        messages.add(new UserMessage(userPrompt));

        String raw = llmSupport.chat(providerId, modelName, messages);
        Map<String, Object> extracted = parseExtractedParams(raw);

        Map<String, Object> outputs = new HashMap<>(extracted);
        outputs.put("extractRaw", raw);

        log.info("[ParameterExtractorNodeProcessor] 提取完成: keys={}", extracted.keySet());

        return NodeExecutionResult.builder()
                .nextNodeId(resolveNextNodeId(context))
                .outputs(outputs)
                .streamContent(raw)
                .build();
    }

    private String buildUserPrompt(String inputText, List<Map<String, Object>> extractParams, String instruction) {
        StringBuilder sb = new StringBuilder();
        if (instruction != null && !instruction.isBlank()) {
            sb.append("提取规则：\n").append(instruction).append("\n\n");
        }
        sb.append("待提取参数（格式 key:type:desc:required）：\n");
        for (Map<String, Object> param : extractParams) {
            if (param == null) {
                continue;
            }
            String key = param.get("key") != null ? param.get("key").toString() : "";
            String type = param.get("type") != null ? param.get("type").toString() : "String";
            String desc = param.get("desc") != null ? param.get("desc").toString() : "";
            boolean required = param.get("required") == null || Boolean.parseBoolean(param.get("required").toString());
            sb.append("- ").append(key).append(":").append(type).append(":").append(desc)
                    .append(":").append(required).append("\n");
        }
        sb.append("\n用户输入：\n").append(inputText).append("\n\n");
        sb.append("请返回 JSON，包含 _is_completed、_reason 以及上述各 key 字段。");
        return sb.toString();
    }

    private Map<String, Object> parseExtractedParams(String raw) {
        try {
            String json = WorkflowVariableUtils.extractJsonObject(raw);
            if (json == null || json.isBlank()) {
                throw new IllegalArgumentException("模型未返回有效 JSON");
            }
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("参数解析失败: " + e.getMessage(), e);
        }
    }
}
