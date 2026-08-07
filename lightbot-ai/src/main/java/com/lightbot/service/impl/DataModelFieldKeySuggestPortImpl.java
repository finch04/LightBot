package com.lightbot.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.BizException;
import com.lightbot.dto.DefaultAiConfigDTO;
import com.lightbot.enums.ErrorCode;
import com.lightbot.model.ModelFactory;
import com.lightbot.model.ProviderResolver;
import com.lightbot.service.SystemConfigService;
import com.lightbot.service.port.DataModelFieldKeySuggestPort;
import com.lightbot.util.LlmTraceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 数据模型字段英文名 AI 补全。
 *
 * @author finch
 * @since 2026-07-28
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataModelFieldKeySuggestPortImpl implements DataModelFieldKeySuggestPort {

    private static final Pattern VALID_KEY = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]{0,63}$");
    private static final Set<String> RESERVED = Set.of(
            "id", "deleted", "createTime", "updateTime",
            "create_time", "update_time", "createtime", "updatetime"
    );
    private static final String[] OBJECT_KEY_FIELDS = {
            "key", "en", "english", "englishName", "fieldKey", "column", "name"
    };

    private static final String SYSTEM_PROMPT = """
            你是数据库字段命名助手。根据中文显示名生成英文标识（将作为数据库列名）。
            要求：
            - 只输出 JSON 字符串数组，例如 ["full_name","remark","quantity"]
            - 数组长度必须与输入 names 一致，顺序一一对应
            - 每个元素必须是字符串，禁止对象、禁止 markdown、禁止解释
            - 标识：小写字母开头，仅含小写字母/数字/下划线，长度 2-64，优先 snake_case
            - 同一批内不得重复；也不得使用 occupiedKeys 中已占用的名字
            """;

    private final ModelFactory modelFactory;
    private final ProviderResolver providerResolver;
    private final SystemConfigService systemConfigService;
    private final ObjectMapper objectMapper;

    @Override
    public List<String> suggestKeys(List<String> names, List<String> occupiedKeys) {
        if (names == null || names.isEmpty()) {
            return List.of();
        }

        // 1. 占用集合（系统保留 + 已有英文名）
        Set<String> occupied = new HashSet<>();
        if (occupiedKeys != null) {
            for (String k : occupiedKeys) {
                if (StringUtils.hasText(k)) {
                    occupied.add(k.trim());
                    occupied.add(k.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        for (String r : RESERVED) {
            occupied.add(r);
            occupied.add(r.toLowerCase(Locale.ROOT));
        }

        String userMessage;
        try {
            userMessage = objectMapper.writeValueAsString(Map.of(
                    "names", names,
                    "occupiedKeys", occupiedKeys == null ? List.of() : occupiedKeys
            ));
        } catch (Exception e) {
            throw new BizException(ErrorCode.AI_GENERATE_FAILED, e);
        }

        // 2. 使用系统默认对话模型（provider + modelId）
        Long providerId = providerResolver.resolve();
        DefaultAiConfigDTO defaultConfig = systemConfigService.getDefaultChatModelConfig();
        Map<String, Object> optionMap = new HashMap<>();
        if (defaultConfig != null && StringUtils.hasText(defaultConfig.getModelId())) {
            optionMap.put("modelId", defaultConfig.getModelId());
        }
        ChatOptions chatOptions = modelFactory.buildChatOptions(providerId, optionMap);
        ChatModel chatModel = modelFactory.getChatModel(providerId);

        String raw;
        try {
            ChatResponse response = LlmTraceContext.callWithoutTrace(() ->
                    chatModel.call(new Prompt(List.of(
                            new SystemMessage(SYSTEM_PROMPT),
                            new UserMessage(userMessage)
                    ), chatOptions)));
            raw = response.getResult().getOutput().getText();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[DataModel] AI补全字段英文名失败: count={}, error={}", names.size(), e.getMessage());
            throw new BizException(ErrorCode.AI_GENERATE_FAILED, e);
        }

        // 3. 解析并规范化，保证合法、唯一
        List<String> parsed = parseKeys(raw, names.size());
        List<String> result = new ArrayList<>(names.size());
        for (int i = 0; i < names.size(); i++) {
            String candidate = i < parsed.size() ? parsed.get(i) : null;
            String key = normalizeKey(candidate, i, occupied);
            result.add(key);
            occupied.add(key);
            occupied.add(key.toLowerCase(Locale.ROOT));
        }
        return result;
    }

    private List<String> parseKeys(String raw, int expectedSize) {
        if (!StringUtils.hasText(raw)) {
            throw new BizException(ErrorCode.AI_GENERATE_FAILED);
        }
        String json = raw.trim()
                .replaceAll("^```(?:json)?\\s*", "")
                .replaceAll("\\s*```$", "")
                .trim();
        try {
            JsonNode root = objectMapper.readTree(json);
            // 兼容 {"keys":[...]} / {"data":[...]}
            if (root != null && root.isObject()) {
                if (root.has("keys") && root.get("keys").isArray()) {
                    root = root.get("keys");
                } else if (root.has("data") && root.get("data").isArray()) {
                    root = root.get("data");
                }
            }
            if (root == null || !root.isArray() || root.size() != expectedSize) {
                log.warn("[DataModel] AI英文名数量不匹配: expect={}, actual={}, raw={}",
                        expectedSize, root == null ? null : root.size(), abbreviate(json));
                throw new BizException(ErrorCode.AI_GENERATE_FAILED);
            }
            List<String> list = new ArrayList<>(expectedSize);
            for (JsonNode node : root) {
                list.add(extractKey(node));
            }
            return list;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[DataModel] AI英文名JSON解析失败: {}, raw={}", e.getMessage(), abbreviate(json));
            throw new BizException(ErrorCode.AI_GENERATE_FAILED);
        }
    }

    /**
     * 兼容字符串或对象（key/en/englishName 等）
     */
    private static String extractKey(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        if (node.isTextual() || node.isNumber()) {
            return node.asText();
        }
        if (node.isObject()) {
            for (String field : OBJECT_KEY_FIELDS) {
                JsonNode v = node.get(field);
                if (v != null && (v.isTextual() || v.isNumber()) && StringUtils.hasText(v.asText())) {
                    return v.asText();
                }
            }
        }
        return "";
    }

    /**
     * 清洗 AI 输出；不合法或冲突时回退为 field_{n} 并消解冲突。
     */
    private String normalizeKey(String candidate, int index, Set<String> occupied) {
        String key = sanitize(candidate);
        if (!StringUtils.hasText(key) || !VALID_KEY.matcher(key).matches() || isOccupied(key, occupied)) {
            key = "field_" + (index + 1);
        }
        String base = key;
        int suffix = 2;
        while (isOccupied(key, occupied) || !VALID_KEY.matcher(key).matches()) {
            key = base + "_" + suffix;
            suffix++;
            if (suffix > 200) {
                key = "field_" + System.currentTimeMillis();
                break;
            }
        }
        return key;
    }

    private static boolean isOccupied(String key, Set<String> occupied) {
        return occupied.contains(key) || occupied.contains(key.toLowerCase(Locale.ROOT));
    }

    private static String sanitize(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String s = raw.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .replaceAll("[^a-zA-Z0-9_]", "")
                .toLowerCase(Locale.ROOT);
        while (s.startsWith("_")) {
            s = s.substring(1);
        }
        if (s.length() > 64) {
            s = s.substring(0, 64);
        }
        return s;
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= 200 ? text : text.substring(0, 200) + "...";
    }
}
