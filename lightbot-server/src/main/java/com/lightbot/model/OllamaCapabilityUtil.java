package com.lightbot.model;

import com.lightbot.entity.ModelProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 查询 Ollama 模型能力（/api/show capabilities），用于判断是否支持 tools 等。
 */
@Slf4j
@Component
public class OllamaCapabilityUtil {

    private final ConcurrentHashMap<String, Boolean> toolsSupportCache = new ConcurrentHashMap<>();

    /**
     * 判断指定 Ollama 模型是否声明支持 tools（capabilities 含 "tools"）。
     *
     * @param provider 提供商（含 baseUrl）
     * @param modelId  模型 ID，如 llama3.1:8b、deepseek-r1:1.5b
     * @return 支持 tools 时 true；查询失败或不支持时 false
     */
    public boolean supportsTools(ModelProvider provider, String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return false;
        }
        String baseUrl = resolveBaseUrl(provider);
        String cacheKey = baseUrl + "|" + modelId.trim();
        return toolsSupportCache.computeIfAbsent(cacheKey, k -> queryToolsSupport(baseUrl, modelId.trim()));
    }

    private boolean queryToolsSupport(String baseUrl, String modelId) {
        try {
            RestClient client = RestClient.builder().baseUrl(baseUrl).build();
            @SuppressWarnings("unchecked")
            Map<String, Object> response = client.post()
                    .uri("/api/show")
                    .body(Map.of("model", modelId))
                    .retrieve()
                    .body(Map.class);
            if (response == null) {
                return false;
            }
            Object capsObj = response.get("capabilities");
            if (capsObj instanceof List<?> caps) {
                boolean supported = caps.stream().anyMatch("tools"::equals);
                log.debug("[OllamaCapability] model={}, capabilities={}, tools={}", modelId, caps, supported);
                return supported;
            }
            log.debug("[OllamaCapability] model={}, capabilities 为空，视为不支持 tools", modelId);
            return false;
        } catch (Exception e) {
            log.warn("[OllamaCapability] 查询模型能力失败: model={}, error={}", modelId, e.getMessage());
            return false;
        }
    }

    private String resolveBaseUrl(ModelProvider provider) {
        if (provider != null && provider.getBaseUrl() != null && !provider.getBaseUrl().isBlank()) {
            String url = provider.getBaseUrl().trim();
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "http://" + url;
            }
            url = url.replaceAll("/+$", "");
            if (url.endsWith("/v1")) {
                url = url.substring(0, url.length() - 3);
            }
            return url;
        }
        return "http://localhost:11434";
    }
}
