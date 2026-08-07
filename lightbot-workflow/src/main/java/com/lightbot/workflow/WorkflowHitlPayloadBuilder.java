package com.lightbot.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.tool.builtin.AskUserTool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流 HITL 挂起载荷构建：将 ask_user 等工具结果转为与 confirm 节点一致的 confirmForm 结构
 */
public final class WorkflowHitlPayloadBuilder {

    public static final String HITL_TYPE_ASK_USER = "ask_user";
    public static final String HITL_TYPE_BUSINESS_PAGE = "business_page";
    public static final String ANSWER_FIELD_KEY = "answer";
    /** 有选项时单选字段（与 {@link #ANSWER_FIELD_KEY} 自定义文本二选一或优先文本） */
    public static final String SELECTED_OPTION_FIELD_KEY = "selectedOption";
    public static final String BUSINESS_RESULT_FIELD_KEY = "businessResult";

    private WorkflowHitlPayloadBuilder() {
    }

    /**
     * 判断工具结果是否要求等待用户输入（ask_user）
     * <p>以 JSON 中 {@code wait_for_user} 为准（仅 ask_user 会产出该标记），
     * 兼容节点 data 中 toolName 误存为 displayName 的历史配置。</p>
     */
    public static boolean isAskUserWaitForUser(String toolName, String rawResult, ObjectMapper objectMapper) {
        return hasWaitForUserFlag(rawResult, objectMapper);
    }

    /**
     * 解析工具 JSON 结果中的 wait_for_user 标记
     */
    public static boolean hasWaitForUserFlag(String rawResult, ObjectMapper objectMapper) {
        if (rawResult == null || rawResult.isBlank()) {
            return false;
        }
        try {
            JsonNode node = objectMapper.readTree(rawResult.trim());
            return node.path("wait_for_user").asBoolean(false);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 是否为业务办理页工具结果（含 pageType 字段）
     */
    public static boolean isBusinessPageResult(String rawResult, ObjectMapper objectMapper) {
        if (rawResult == null || rawResult.isBlank()) {
            return false;
        }
        try {
            JsonNode node = objectMapper.readTree(rawResult.trim());
            return node.hasNonNull("pageType") && node.path("success").asBoolean(true);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 将 present_business_page 工具 JSON 转为 confirmForm（前端渲染固化业务页）
     */
    public static Map<String, Object> fromBusinessPageTool(String nodeId, String rawResult, ObjectMapper objectMapper) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("nodeId", nodeId);
        payload.put("hitlType", HITL_TYPE_BUSINESS_PAGE);
        payload.put("toolName", "present_business_page");
        payload.put("message", "请完成业务办理后继续");
        try {
            JsonNode node = objectMapper.readTree(rawResult.trim());
            Map<String, Object> pagePayload = objectMapper.convertValue(node, Map.class);
            payload.put("pagePayload", pagePayload);
            if (node.hasNonNull("title")) {
                payload.put("message", node.get("title").asText());
            }
        } catch (Exception ignored) {
            payload.put("pagePayload", Map.of());
        }
        // 隐藏字段：前端业务页提交后写入 JSON
        List<Map<String, Object>> fields = new ArrayList<>();
        Map<String, Object> resultField = new HashMap<>();
        resultField.put("key", BUSINESS_RESULT_FIELD_KEY);
        resultField.put("label", "办理结果");
        resultField.put("type", "textarea");
        resultField.put("required", true);
        fields.add(resultField);
        payload.put("formFields", fields);
        return payload;
    }

    /**
     * 将 ask_user 工具 JSON 转为 workflow_confirm_required.confirmForm
     */
    public static Map<String, Object> fromAskUserTool(String nodeId, String rawResult, ObjectMapper objectMapper) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("nodeId", nodeId);
        payload.put("hitlType", HITL_TYPE_ASK_USER);
        payload.put("toolName", AskUserTool.TOOL_NAME);

        String question = "请回答以下问题";
        boolean openEnded = true;
        List<String> options = List.of();

        try {
            JsonNode node = objectMapper.readTree(rawResult.trim());
            if (node.has("question") && !node.get("question").isNull()) {
                question = node.get("question").asText(question);
            }
            openEnded = node.path("is_open_ended").asBoolean(true);
            if (node.has("options") && node.get("options").isArray()) {
                List<String> list = new ArrayList<>();
                for (JsonNode opt : node.get("options")) {
                    if (opt != null && !opt.isNull()) {
                        String text = opt.asText("").trim();
                        if (!text.isEmpty()) {
                            list.add(text);
                        }
                    }
                }
                options = list;
                if (!list.isEmpty()) {
                    openEnded = false;
                }
            }
        } catch (Exception ignored) {
        }

        payload.put("message", question);
        payload.put("formFields", buildAskUserFormFields(openEnded, options));
        return payload;
    }

    /**
     * 合并 ask_user 表单：自定义文本优先，否则取选项（与 Agent 弹窗行为一致）
     */
    public static Map<String, Object> normalizeAskUserSubmittedForm(Map<String, Object> formData) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (formData == null || formData.isEmpty()) {
            return result;
        }
        String customAnswer = trimToNull(formData.get(ANSWER_FIELD_KEY));
        String selected = trimToNull(formData.get(SELECTED_OPTION_FIELD_KEY));
        String finalAnswer = customAnswer != null ? customAnswer : selected;
        if (finalAnswer != null) {
            result.put(ANSWER_FIELD_KEY, finalAnswer);
        }
        formData.forEach((key, value) -> {
            if (key == null || key.isBlank() || key.startsWith("_")) {
                return;
            }
            if (ANSWER_FIELD_KEY.equals(key) || SELECTED_OPTION_FIELD_KEY.equals(key)) {
                return;
            }
            result.put(key, value);
        });
        return result;
    }

    /**
     * 合并 ask_user 挂起阶段 outputs 与用户 resume 提交的 answer（保留 question/options 等）
     */
    public static Map<String, Object> mergePhaseOutputsWithAnswer(Map<String, Object> phaseOutputs,
                                                                   Map<String, Object> submitted) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (phaseOutputs != null) {
            merged.putAll(phaseOutputs);
        }
        if (submitted != null) {
            merged.putAll(submitted);
        }
        return merged;
    }

    /**
     * 将 ask_user 工具 JSON 解析出的字段写入节点 outputs（挂起阶段，不含 answer）
     */
    public static void enrichAskUserPhaseOutputs(Map<String, Object> outputs, Map<String, Object> toolVars) {
        if (outputs == null || toolVars == null) {
            return;
        }
        copyIfPresent(outputs, toolVars, "question");
        copyIfPresent(outputs, toolVars, "options");
        copyIfPresent(outputs, toolVars, "is_open_ended");
    }

    /**
     * resume 后 applyOutputMappings 仅保留映射字段，需从挂起阶段 outputs 回填 tool 渲染元数据
     */
    public static void preserveAskUserRenderMeta(Map<String, Object> target, Map<String, Object> source) {
        if (target == null || source == null || source.isEmpty()) {
            return;
        }
        for (String key : List.of(
                "toolResultText", "toolResult", "output",
                "toolName", "toolId", "toolDisplayName",
                "question", "options", "is_open_ended")) {
            if (source.containsKey(key) && source.get(key) != null) {
                target.putIfAbsent(key, source.get(key));
            }
        }
    }

    private static void copyIfPresent(Map<String, Object> target, Map<String, Object> source, String key) {
        if (source.containsKey(key) && source.get(key) != null) {
            target.put(key, source.get(key));
        }
    }

    private static String trimToNull(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static List<Map<String, Object>> buildAskUserFormFields(boolean openEnded, List<String> options) {
        List<Map<String, Object>> fields = new ArrayList<>();
        if (!openEnded && options != null && !options.isEmpty()) {
            Map<String, Object> choice = new HashMap<>();
            choice.put("key", SELECTED_OPTION_FIELD_KEY);
            choice.put("label", "请选择");
            choice.put("type", "radio");
            choice.put("required", false);
            choice.put("options", options);
            fields.add(choice);

            Map<String, Object> text = new HashMap<>();
            text.put("key", ANSWER_FIELD_KEY);
            text.put("label", "或输入自定义回答");
            text.put("type", "textarea");
            text.put("required", false);
            fields.add(text);
        } else {
            Map<String, Object> text = new HashMap<>();
            text.put("key", ANSWER_FIELD_KEY);
            text.put("label", "您的回答");
            text.put("type", "textarea");
            text.put("required", true);
            fields.add(text);
        }
        return fields;
    }
}
