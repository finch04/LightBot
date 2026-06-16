package com.lightbot.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.dto.DefaultAiConfigDTO;
import com.lightbot.entity.ModelProvider;
import com.lightbot.service.ModelProviderService;
import com.lightbot.service.SystemConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 重排序工具类（封装 DashScope Rerank API）
 *
 * @author finch
 * @since 2026-06-16
 */
@Slf4j
@Component
public class RerankerUtil {

    private static final String DASHSCOPE_RERANK_URL =
            "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank";

    private final SystemConfigService systemConfigService;
    private final ModelProviderService modelProviderService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public RerankerUtil(SystemConfigService systemConfigService,
                        ModelProviderService modelProviderService,
                        ObjectMapper objectMapper) {
        this.systemConfigService = systemConfigService;
        this.modelProviderService = modelProviderService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * 对候选结果进行重排序
     *
     * @param query         查询文本
     * @param candidates    候选结果（需含 "content" 字段）
     * @param topK          返回数量
     * @param rerankerModel 指定重排序模型（格式："{providerId}:{modelId}"），为空时使用系统默认配置
     * @return 重排序后的结果列表，无 reranker 配置时返回原列表
     */
    public List<Map<String, Object>> rerank(String query, List<Map<String, Object>> candidates,
                                             int topK, String rerankerModel) {
        if (candidates == null || candidates.isEmpty()) {
            return candidates;
        }

        // 1. 获取 rerank 模型配置（指定模型 > 系统默认）
        Long providerId = null;
        String modelId = null;
        if (rerankerModel != null && !rerankerModel.isBlank() && rerankerModel.contains(":")) {
            String[] parts = rerankerModel.split(":", 2);
            try {
                providerId = Long.parseLong(parts[0]);
                modelId = parts[1];
            } catch (NumberFormatException e) {
                log.warn("[Reranker] 无效的 rerankerModel 格式: {}", rerankerModel);
            }
        }

        ModelProvider provider = null;
        if (providerId != null) {
            provider = modelProviderService.getById(providerId);
        }
        if (provider == null || provider.getApiKey() == null || provider.getApiKey().isBlank()) {
            // 回退到系统默认配置
            DefaultAiConfigDTO config = systemConfigService.getDefaultRerankModelConfig();
            if (config == null || config.getProviderId() == null) {
                log.debug("[Reranker] 未配置默认重排模型，跳过重排序");
                return candidates;
            }
            provider = modelProviderService.getById(config.getProviderId());
            if (provider == null || provider.getApiKey() == null || provider.getApiKey().isBlank()) {
                log.warn("[Reranker] 模型提供商不存在或 API Key 为空, providerId={}", config.getProviderId());
                return candidates;
            }
            if (modelId == null || modelId.isBlank()) {
                modelId = config.getModelId();
            }
        }

        if (modelId == null || modelId.isBlank()) {
            modelId = "gte-rerank-v2";
        }

        // 2. 提取候选文档内容
        List<String> documents = new ArrayList<>(candidates.size());
        for (Map<String, Object> candidate : candidates) {
            Object content = candidate.get("content");
            documents.add(content != null ? content.toString() : "");
        }

        // 3. 调用 DashScope Rerank API
        try {
            List<RerankResult> rerankResults = callDashScopeRerank(
                    provider.getApiKey(), provider.getBaseUrl(), modelId, query, documents, topK);

            // 4. 按 rerank 分数重新排序
            List<Map<String, Object>> reranked = new ArrayList<>(rerankResults.size());
            for (RerankResult rr : rerankResults) {
                if (rr.index >= 0 && rr.index < candidates.size()) {
                    Map<String, Object> row = new LinkedHashMap<>(candidates.get(rr.index));
                    row.put("rerank_score", rr.score);
                    reranked.add(row);
                }
            }
            return reranked;
        } catch (Exception e) {
            log.error("[Reranker] 重排序失败，返回原结果: {}", e.getMessage());
            return candidates;
        }
    }

    /**
     * 调用 DashScope Rerank API
     */
    private List<RerankResult> callDashScopeRerank(String apiKey, String baseUrl,
                                                    String modelId, String query,
                                                    List<String> documents, int topN) throws Exception {
        String url = (baseUrl != null && !baseUrl.isBlank())
                ? baseUrl + "/api/v1/services/rerank/text-rerank/text-rerank"
                : DASHSCOPE_RERANK_URL;

        // 构建请求体
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelId);

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("query", query);
        input.put("documents", documents);
        body.put("input", input);

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("top_n", Math.min(topN, documents.size()));
        parameters.put("return_documents", false);
        body.put("parameters", parameters);

        String jsonBody = objectMapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("DashScope Rerank API 返回 HTTP " + response.statusCode()
                    + ": " + response.body());
        }

        return parseRerankResponse(response.body());
    }

    /**
     * 解析 DashScope Rerank API 响应
     *
     * 响应格式:
     * {
     *   "output": {
     *     "results": [
     *       { "index": 0, "relevance_score": 0.95 },
     *       { "index": 2, "relevance_score": 0.80 }
     *     ]
     *   }
     * }
     */
    private List<RerankResult> parseRerankResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode results = root.path("output").path("results");

        List<RerankResult> list = new ArrayList<>();
        if (results.isArray()) {
            for (JsonNode item : results) {
                int index = item.path("index").asInt(-1);
                double score = item.path("relevance_score").asDouble(0);
                if (index >= 0) {
                    list.add(new RerankResult(index, score));
                }
            }
        }
        return list;
    }

    private record RerankResult(int index, double score) {}
}
