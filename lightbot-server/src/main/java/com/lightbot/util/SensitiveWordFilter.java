package com.lightbot.util;

import com.lightbot.constant.ConfigKeys;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 敏感词过滤：对模型输出进行替换或拦截
 */
public final class SensitiveWordFilter {

    public static final String STRATEGY_REPLACE = "replace";
    public static final String STRATEGY_BLOCK = "block";

    private static final String BLOCK_MESSAGE = "[内容因包含敏感词已被拦截]";

    private SensitiveWordFilter() {
    }

    /**
     * 流式输出：按累积全文过滤后，仅返回相对已下发内容的增量（支持拦截整段替换）
     */
    public static final class StreamState {
        private final Map<String, Object> configMap;
        private final StringBuilder raw = new StringBuilder();
        private int filteredEmittedLength;

        public StreamState(Map<String, Object> configMap) {
            this.configMap = configMap;
        }

        /**
         * @param chunk 本轮模型流式片段（增量文本）
         * @return 过滤后应下发给前端的增量
         */
        public String processChunk(String chunk) {
            if (chunk == null || chunk.isEmpty()) {
                return "";
            }
            raw.append(chunk);
            FilterResult result = filter(raw.toString(), configMap);
            String filtered = result.text();
            if (filtered.length() <= filteredEmittedLength) {
                return "";
            }
            String delta = filtered.substring(filteredEmittedLength);
            filteredEmittedLength = filtered.length();
            return delta;
        }
    }

    /**
     * 根据 Agent config 过滤文本
     */
    public static FilterResult filter(String text, Map<String, Object> configMap) {
        if (text == null || text.isEmpty() || configMap == null) {
            return FilterResult.unchanged(text);
        }
        if (!isFilterEnabled(configMap)) {
            return FilterResult.unchanged(text);
        }
        List<String> words = parseWords(configMap.get(ConfigKeys.Agent.SENSITIVE_WORDS));
        if (words.isEmpty()) {
            return FilterResult.unchanged(text);
        }
        String strategy = configMap.get(ConfigKeys.Agent.SENSITIVE_FILTER_STRATEGY) != null
                ? configMap.get(ConfigKeys.Agent.SENSITIVE_FILTER_STRATEGY).toString() : STRATEGY_REPLACE;
        String replaceText = configMap.get(ConfigKeys.Agent.SENSITIVE_FILTER_REPLACE_TEXT) != null
                ? configMap.get(ConfigKeys.Agent.SENSITIVE_FILTER_REPLACE_TEXT).toString() : "***";

        if (STRATEGY_BLOCK.equalsIgnoreCase(strategy)) {
            for (String word : words) {
                if (word != null && !word.isBlank() && containsIgnoreCase(text, word)) {
                    return new FilterResult(BLOCK_MESSAGE, true, true);
                }
            }
            return FilterResult.unchanged(text);
        }

        String result = text;
        boolean matched = false;
        for (String word : words) {
            if (word == null || word.isBlank()) {
                continue;
            }
            String replaced = replaceIgnoreCase(result, word, replaceText);
            if (!replaced.equals(result)) {
                matched = true;
                result = replaced;
            }
        }
        return matched ? new FilterResult(result, true, false) : FilterResult.unchanged(text);
    }

    /**
     * 解析开关：兼容 JSON 中的 Boolean、数字 1/0、字符串 true/false
     */
    public static boolean isFilterEnabled(Map<String, Object> configMap) {
        if (configMap == null) {
            return false;
        }
        Object v = configMap.get(ConfigKeys.Agent.SENSITIVE_FILTER_ENABLED);
        if (v == null) {
            return false;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof Number n) {
            return n.intValue() != 0;
        }
        String s = v.toString().trim();
        return "true".equalsIgnoreCase(s) || "1".equals(s) || "yes".equalsIgnoreCase(s);
    }

    @SuppressWarnings("unchecked")
    private static List<String> parseWords(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof List<?> list) {
            List<String> words = new ArrayList<>();
            for (Object item : list) {
                if (item != null && !item.toString().isBlank()) {
                    words.add(item.toString().trim());
                }
            }
            return words;
        }
        if (raw instanceof String str && !str.isBlank()) {
            String[] parts = str.split("[,，\\n;；]+");
            List<String> words = new ArrayList<>();
            for (String part : parts) {
                if (!part.isBlank()) {
                    words.add(part.trim());
                }
            }
            return words;
        }
        return List.of();
    }

    private static boolean containsIgnoreCase(String text, String word) {
        return Pattern.compile(Pattern.quote(word), Pattern.CASE_INSENSITIVE).matcher(text).find();
    }

    private static String replaceIgnoreCase(String text, String word, String replacement) {
        return Pattern.compile(Pattern.quote(word), Pattern.CASE_INSENSITIVE)
                .matcher(text)
                .replaceAll(java.util.regex.Matcher.quoteReplacement(replacement));
    }

    public record FilterResult(String text, boolean filtered, boolean blocked) {
        public static FilterResult unchanged(String text) {
            return new FilterResult(text, false, false);
        }
    }
}
