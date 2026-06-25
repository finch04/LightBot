package com.lightbot.util;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

/**
 * JSON 安全解析工具类
 *
 * @author finch
 * @since 2026-06-25
 */
public final class JsonUtil {

    private JsonUtil() {}

    /**
     * 安全解析JSON字符串为Map，解析失败返回空Map
     *
     * @param objectMapper Jackson ObjectMapper
     * @param json         JSON字符串
     * @return 解析后的Map，null/空白/"{}" 返回空Map
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseJsonToMap(ObjectMapper objectMapper, String json) {
        if (json == null || json.isBlank() || "{}".equals(json)) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
}
