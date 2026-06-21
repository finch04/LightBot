package com.lightbot.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * RAG 参数解析器
 * 统一各处 RAG 参数解析逻辑，优先级：overrides > queryParams > config > 默认值
 *
 * @author finch
 * @since 2026-06-21
 */
@Slf4j
@Component
public class RagParamResolver {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    public static final int DEFAULT_TOP_K = 5;
    public static final double DEFAULT_THRESHOLD = 0.5;

    /**
     * 解析 TopK 参数
     * 优先级：overrides > queryParams > config > 默认值
     *
     * @param overrides   覆盖参数（如 Agent 级别配置），可为 null
     * @param queryParams 查询参数（知识库级别配置），可为 null
     * @param configJson  知识库 config JSON 字符串，可为 null
     * @param defaultVal  默认值
     * @return TopK 值
     */
    public int resolveTopK(Map<String, Object> overrides, Map<String, Object> queryParams,
                           String configJson, int defaultVal) {
        // 1. overrides 最高优先
        if (overrides != null) {
            Object val = overrides.get("final_top_k");
            if (val instanceof Number n) return n.intValue();
        }
        // 2. query_params
        if (queryParams != null) {
            Object val = queryParams.get("final_top_k");
            if (val instanceof Number n) return n.intValue();
        }
        // 3. 兼容旧 config 中的 ragTopK
        if (configJson != null) {
            Map<String, Object> config = parseJson(configJson);
            Object val = config.get("ragTopK");
            if (val instanceof Number n) return n.intValue();
        }
        // 4. 默认值
        return defaultVal;
    }

    /**
     * 解析 Threshold 参数
     * 优先级：overrides > queryParams > config > 默认值
     *
     * @param overrides   覆盖参数，可为 null
     * @param queryParams 查询参数，可为 null
     * @param configJson  知识库 config JSON 字符串，可为 null
     * @param defaultVal  默认值
     * @return Threshold 值
     */
    public double resolveThreshold(Map<String, Object> overrides, Map<String, Object> queryParams,
                                   String configJson, double defaultVal) {
        // 1. overrides 最高优先
        if (overrides != null) {
            Object val = overrides.get("similarity_threshold");
            if (val instanceof Number n) return n.doubleValue();
        }
        // 2. query_params
        if (queryParams != null) {
            Object val = queryParams.get("similarity_threshold");
            if (val instanceof Number n) return n.doubleValue();
        }
        // 3. 兼容旧 config 中的 ragThreshold
        if (configJson != null) {
            Map<String, Object> config = parseJson(configJson);
            Object val = config.get("ragThreshold");
            if (val instanceof Number n) return n.doubleValue();
        }
        // 4. 默认值
        return defaultVal;
    }

    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return MAPPER.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            return Map.of();
        }
    }
}
