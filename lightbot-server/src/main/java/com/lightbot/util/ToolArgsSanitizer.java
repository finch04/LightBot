package com.lightbot.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Set;

/**
 * 工具调用参数清理：移除由 ToolContext 注入的字段，避免 LLM 传入空字符串导致类型转换失败
 */
public final class ToolArgsSanitizer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 对话场景下由 ToolContext 注入、不应出现在 LLM 参数中的字段 */
    private static final Set<String> CONTEXT_INJECTED_KEYS = Set.of("agentId", "requestId");

    private ToolArgsSanitizer() {
    }

    /**
     * 从工具参数 JSON 中移除由 ToolContext 注入的字段（含 LLM 误传的空 agentId）
     */
    public static String forChatCall(String args) {
        return stripKeys(args, CONTEXT_INJECTED_KEYS, false);
    }

    /**
     * 测试工具：agentId 已由调用方写入 ToolContext，从 JSON 中移除避免多余字段
     */
    public static String forTestCall(String args) {
        return stripKeys(args, Set.of("agentId"), false);
    }

    private static String stripKeys(String args, Set<String> keys, boolean stripBlankOnly) {
        if (args == null || args.isBlank()) {
            return "{}";
        }
        try {
            JsonNode root = MAPPER.readTree(args);
            if (!(root instanceof ObjectNode obj)) {
                return args;
            }
            boolean changed = false;
            for (String key : keys) {
                if (!obj.has(key)) {
                    continue;
                }
                JsonNode val = obj.get(key);
                if (!stripBlankOnly || isBlankOrNull(val)) {
                    obj.remove(key);
                    changed = true;
                }
            }
            return changed ? MAPPER.writeValueAsString(obj) : args;
        } catch (Exception e) {
            return args;
        }
    }

    private static boolean isBlankOrNull(JsonNode val) {
        if (val == null || val.isNull()) {
            return true;
        }
        if (val.isTextual()) {
            return val.asText().isBlank();
        }
        return false;
    }
}
