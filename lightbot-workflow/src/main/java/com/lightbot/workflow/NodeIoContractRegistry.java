package com.lightbot.workflow;

import com.lightbot.enums.NodeType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工作流节点 IO 契约注册表：定义各节点固定输出字段，并按契约裁剪运行时 outputs
 */
public final class NodeIoContractRegistry {

    /** 仅用于 trace/debug，不写入变量池与上游变量树 */
    private static final Set<String> DEBUG_ONLY_KEYS = Set.of(
            "extractRaw", "classificationRaw", "traceData");

    private NodeIoContractRegistry() {
    }

    public static boolean isDebugOnlyKey(String key) {
        return key != null && DEBUG_ONLY_KEYS.contains(key);
    }

    /**
     * 按节点契约裁剪 outputs，过滤调试字段
     *
     * @param type     节点类型
     * @param nodeData 节点配置 data
     * @param raw      原始 outputs
     * @return 写入变量池的 outputs
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> filterOutputs(NodeType type, Map<String, Object> nodeData,
                                                    Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> filtered = new LinkedHashMap<>();
        Set<String> allowedKeys = resolveAllowedKeys(type, nodeData);
        if (allowedKeys.isEmpty()) {
            raw.forEach((k, v) -> {
                if (!isDebugOnlyKey(k)) {
                    filtered.put(k, v);
                }
            });
        } else {
            for (String key : allowedKeys) {
                if (raw.containsKey(key)) {
                    filtered.put(key, raw.get(key));
                }
            }
            raw.forEach((k, v) -> {
                if (!filtered.containsKey(k) && !isDebugOnlyKey(k) && !allowedKeys.contains(k)) {
                    filtered.put(k, v);
                }
            });
        }
        if (type == NodeType.LLM) {
            if (filtered.containsKey("output") && !filtered.containsKey("llmOutput")) {
                filtered.put("llmOutput", filtered.get("output"));
            } else if (filtered.containsKey("llmOutput") && !filtered.containsKey("output")) {
                filtered.put("output", filtered.get("llmOutput"));
            }
        }
        return filtered;
    }

    /**
     * 解析节点允许写入变量池的输出字段名
     */
    public static Set<String> resolveAllowedKeys(NodeType type, Map<String, Object> nodeData) {
        Set<String> keys = new LinkedHashSet<>(getFixedOutputKeys(type));
        if (nodeData == null) {
            return keys;
        }
        keys.addAll(extractDynamicKeys(type, nodeData));
        return keys;
    }

    private static List<String> getFixedOutputKeys(NodeType type) {
        if (type == null) {
            return List.of();
        }
        return switch (type) {
            case START -> List.of("input");
            case END -> List.of("result");
            case LLM -> List.of("output", "llmOutput");
            case PARAMETER_EXTRACTOR -> List.of("_is_completed", "_reason");
            case RETRIEVAL -> List.of("retrievalResult", "retrievalChunks", "input");
            case CONDITION -> List.of("matchedHandle", "matchedGroupLabel");
            case CLASSIFIER -> List.of("subject", "intentId", "matchedIntentId", "thought");
            case API -> List.of("statusCode", "body", "result");
            case TOOL -> List.of("output", "toolResult", "toolResultText", "toolName", "toolId",
                    "question", "options", "is_open_ended", "answer");
            case MCP -> List.of("output", "mcpResult", "toolName", "mcpServerName");
            case OUTPUT -> List.of("output");
            case VARIABLE_HANDLE -> List.of("output");
            case LOOP, BATCH -> List.of("iterations");
            case APP_COMPONENT -> List.of("result", "output");
            case SUB_AGENT -> List.of("reply", "output", "threadId", "subAgentName", "subAgentId");
            default -> List.of();
        };
    }

    @SuppressWarnings("unchecked")
    private static List<String> extractDynamicKeys(NodeType type, Map<String, Object> nodeData) {
        List<String> keys = new ArrayList<>();
        switch (type) {
            case PARAMETER_EXTRACTOR -> collectKeyFromList(nodeData.get("extractParams"), keys);
            case SCRIPT, INPUT -> collectKeyFromList(
                    firstNonNull(nodeData.get("output_params"), nodeData.get("outputParams")), keys);
            case LOOP, BATCH -> collectKeyFromList(
                    firstNonNull(nodeData.get("output_params"), nodeData.get("outputParams")), keys);
            case VARIABLE -> {
                String name = WorkflowNodeDataUtils.parseString(nodeData.get("variableName"));
                if (name != null && !name.isBlank()) {
                    keys.add(name.trim());
                }
            }
            case VARIABLE_HANDLE -> {
                String handleType = WorkflowNodeDataUtils.parseString(nodeData.get("handleType"));
                if (handleType == null) {
                    handleType = WorkflowNodeDataUtils.parseString(nodeData.get("type"));
                }
                if ("group".equalsIgnoreCase(handleType)) {
                    Object groups = nodeData.get("groups");
                    if (groups instanceof List<?> list) {
                        for (Object item : list) {
                            if (item instanceof Map<?, ?> group) {
                                String groupName = WorkflowNodeDataUtils.parseString(group.get("groupName"));
                                if (groupName == null) {
                                    groupName = WorkflowNodeDataUtils.parseString(group.get("group_name"));
                                }
                                if (groupName != null && !groupName.isBlank()) {
                                    keys.add(groupName.trim());
                                }
                            }
                        }
                    }
                } else {
                    keys.add("output");
                }
            }
            case TOOL, APP_COMPONENT, SUB_AGENT -> collectKeyFromList(nodeData.get("outputMappings"), keys);
            default -> {
            }
        }
        return keys;
    }

    private static void collectKeyFromList(Object listObj, List<String> keys) {
        if (!(listObj instanceof List<?> list)) {
            return;
        }
        for (Object item : list) {
            if (item instanceof Map<?, ?> row) {
                String key = WorkflowNodeDataUtils.parseString(row.get("key"));
                if (key != null && !key.isBlank()) {
                    keys.add(key.trim());
                }
            }
        }
    }

    private static Object firstNonNull(Object a, Object b) {
        return a != null ? a : b;
    }
}
