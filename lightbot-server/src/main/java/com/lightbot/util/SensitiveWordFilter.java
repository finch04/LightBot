package com.lightbot.util;

import com.lightbot.constant.ConfigKeys;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 敏感词过滤：用户输入拦截 + AI 输出替换/拦截
 */
@Slf4j
public final class SensitiveWordFilter {

    public static final String STRATEGY_REPLACE = "replace";
    public static final String STRATEGY_BLOCK = "block";

    /** 用户输入命中敏感词时的提示（展示给用户） */
    public static final String USER_BLOCK_MESSAGE = "【安全提示】您的消息包含敏感内容，请修改后重新发送。";

    /** AI 输出命中拦截策略时的提示（展示给用户） */
    public static final String AI_BLOCK_MESSAGE = "【安全提示】回复内容包含敏感信息，已停止输出。";

    /**
     * 敏感词正则缓存：word → 编译后的 Pattern
     * <p>缓存规模由业务约束：每个 Agent 的敏感词列表在 config 中配置，数量有限（通常几十个），
     * 因此 ConcurrentHashMap 无界缓存在实际场景中不会造成内存压力，无需引入 Caffeine 等有界缓存。</p>
     */
    private static final ConcurrentHashMap<String, Pattern> PATTERN_CACHE = new ConcurrentHashMap<>();

    private SensitiveWordFilter() {
    }

    /**
     * 流式 AI 输出：按累积全文过滤后，仅返回相对已下发内容的增量
     */
    public static final class StreamState {
        private final Map<String, Object> configMap;
        private final Long agentId;
        private final Long sessionId;
        private final StringBuilder raw = new StringBuilder();
        private int filteredEmittedLength;
        private boolean lastBlocked;

        public StreamState(Map<String, Object> configMap, Long agentId, Long sessionId) {
            this.configMap = configMap;
            this.agentId = agentId;
            this.sessionId = sessionId;
        }

        public String processChunk(String chunk) {
            if (chunk == null || chunk.isEmpty()) {
                return "";
            }
            raw.append(chunk);
            FilterResult result = filterAiOutput(raw.toString(), configMap, agentId, sessionId);
            lastBlocked = result.blocked();
            String filtered = result.text();
            if (filtered.length() <= filteredEmittedLength) {
                return "";
            }
            String delta = filtered.substring(filteredEmittedLength);
            filteredEmittedLength = filtered.length();
            return delta;
        }

        /** 最近一次 processChunk 是否触发了 block 拦截 */
        public boolean isBlocked() {
            return lastBlocked;
        }
    }

    /**
     * 检测用户输入是否包含敏感词（命中则拦截，不继续对话）
     */
    public static FilterResult checkUserInput(String text, Map<String, Object> configMap,
                                             Long agentId, Long sessionId) {
        if (text == null || text.isEmpty() || configMap == null) {
            return FilterResult.unchanged(text);
        }
        if (!isUserFilterEnabled(configMap)) {
            return FilterResult.unchanged(text);
        }
        List<String> words = parseWords(configMap.get(ConfigKeys.Agent.USER_SENSITIVE_WORDS));
        if (words.isEmpty()) {
            return FilterResult.unchanged(text);
        }
        String matched = findFirstMatch(text, words);
        if (matched != null) {
            log.warn("[SensitiveWord] 用户输入拦截 agentId={}, sessionId={}, matchedWord={}",
                    agentId, sessionId, matched);
            return new FilterResult(USER_BLOCK_MESSAGE, true, true, matched, "user_input");
        }
        return FilterResult.unchanged(text);
    }

    /**
     * 过滤 AI 输出（替换或拦截）
     */
    public static FilterResult filterAiOutput(String text, Map<String, Object> configMap) {
        return filterAiOutput(text, configMap, null, null);
    }

    public static FilterResult filterAiOutput(String text, Map<String, Object> configMap,
                                              Long agentId, Long sessionId) {
        if (text == null || text.isEmpty() || configMap == null) {
            return FilterResult.unchanged(text);
        }
        if (!isAiFilterEnabled(configMap)) {
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
            String matched = findFirstMatch(text, words);
            if (matched != null) {
                log.warn("[SensitiveWord] AI输出拦截 agentId={}, sessionId={}, matchedWord={}, strategy=block",
                        agentId, sessionId, matched);
                return new FilterResult(AI_BLOCK_MESSAGE, true, true, matched, "ai_output_block");
            }
            return FilterResult.unchanged(text);
        }

        String result = text;
        boolean matched = false;
        String firstMatched = null;
        for (String word : words) {
            if (word == null || word.isBlank()) {
                continue;
            }
            String replaced = replaceIgnoreCase(result, word, replaceText);
            if (!replaced.equals(result)) {
                if (firstMatched == null) {
                    firstMatched = word;
                }
                matched = true;
                result = replaced;
            }
        }
        if (matched) {
            log.info("[SensitiveWord] AI输出替换 agentId={}, sessionId={}, matchedWord={}, strategy=replace",
                    agentId, sessionId, firstMatched);
            return new FilterResult(result, true, false, firstMatched, "ai_output_replace");
        }
        return FilterResult.unchanged(text);
    }

    /** @deprecated 使用 {@link #filterAiOutput} */
    @Deprecated
    public static FilterResult filter(String text, Map<String, Object> configMap) {
        return filterAiOutput(text, configMap);
    }

    public static boolean isUserFilterEnabled(Map<String, Object> configMap) {
        return parseBooleanFlag(configMap, ConfigKeys.Agent.USER_SENSITIVE_FILTER_ENABLED);
    }

    public static boolean isAiFilterEnabled(Map<String, Object> configMap) {
        return parseBooleanFlag(configMap, ConfigKeys.Agent.SENSITIVE_FILTER_ENABLED);
    }

    private static boolean parseBooleanFlag(Map<String, Object> configMap, String key) {
        if (configMap == null) {
            return false;
        }
        Object v = configMap.get(key);
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

    private static String findFirstMatch(String text, List<String> words) {
        for (String word : words) {
            if (word != null && !word.isBlank() && containsIgnoreCase(text, word)) {
                return word;
            }
        }
        return null;
    }

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
        return getPattern(word).matcher(text).find();
    }

    private static String replaceIgnoreCase(String text, String word, String replacement) {
        return getPattern(word).matcher(text)
                .replaceAll(java.util.regex.Matcher.quoteReplacement(replacement));
    }

    private static Pattern getPattern(String word) {
        return PATTERN_CACHE.computeIfAbsent(word,
                w -> Pattern.compile(Pattern.quote(w), Pattern.CASE_INSENSITIVE));
    }

    public record FilterResult(String text, boolean filtered, boolean blocked, String matchedWord, String scope) {
        public static FilterResult unchanged(String text) {
            return new FilterResult(text, false, false, null, null);
        }

        public FilterResult(String text, boolean filtered, boolean blocked, String matchedWord, String scope) {
            this.text = text;
            this.filtered = filtered;
            this.blocked = blocked;
            this.matchedWord = matchedWord;
            this.scope = scope;
        }
    }
}
