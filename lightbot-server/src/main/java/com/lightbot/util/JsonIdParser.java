package com.lightbot.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * JSONB 数组 ID 解析工具
 *
 * @author finch
 * @since 2026-06-21
 */
public final class JsonIdParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonIdParser() {}

    /**
     * 解析 JSONB 数组字段中的 ID 列表
     * 支持字符串和数字类型的 ID，自动转换为 Long
     *
     * @param json JSON 数组字符串
     * @return ID 列表，解析失败返回空列表
     */
    public static List<Long> parseIds(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<Object> raw = MAPPER.readValue(json, new TypeReference<>() {});
            List<Long> ids = new ArrayList<>();
            for (Object item : raw) {
                if (item == null) continue;
                String text = item.toString().trim();
                if (text.isBlank()) continue;
                try {
                    ids.add(Long.parseLong(text));
                } catch (NumberFormatException ignored) {
                }
            }
            return ids;
        } catch (Exception e) {
            return List.of();
        }
    }
}
