package com.lightbot.tool.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.tool.annotation.SystemTool;
import com.lightbot.tool.annotation.ToolParamMeta;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
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
 * 系统内置工具 — 联网搜索（Tavily API）
 * <p>通过 Tavily API 搜索互联网获取最新信息</p>
 *
 * @author finch
 * @since 2026-05-22
 */
@Slf4j
@Component("webSearchTool")
@SystemTool(displayName = "联网搜索", description = "联网搜索互联网获取最新信息", tags = {"搜索"},
        outputExample = "{\"query\":\"今天天气\",\"answer\":\"今天北京晴，气温25-32℃\",\"results\":[{\"title\":\"北京今日天气预报\",\"url\":\"https://weather.com.cn/...\",\"content\":\"今天白天晴间多云...\"}],\"total\":1}",
        outputSchema = "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\",\"description\":\"搜索关键词\"},\"answer\":{\"type\":\"string\",\"description\":\"AI总结的答案摘要（可能为null）\"},\"results\":{\"type\":\"array\",\"description\":\"搜索结果列表\",\"items\":{\"type\":\"object\",\"properties\":{\"title\":{\"type\":\"string\",\"description\":\"网页标题\"},\"url\":{\"type\":\"string\",\"description\":\"网页链接\"},\"content\":{\"type\":\"string\",\"description\":\"网页摘要内容\"}}}},\"total\":{\"type\":\"integer\",\"description\":\"结果总数\"}}}}")
@RequiredArgsConstructor
public class WebSearchTool {

    @Value("${lightbot.tavily.api-key:}")
    private String apiKey;

    private final ObjectMapper objectMapper;
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Tool(name = "web_search",
          description = "联网搜索互联网获取最新信息。当用户问题涉及时事、新闻、股票价格、天气、或任何需要最新数据的问题时调用此工具。")
    public String search(
            @ToolParam(description = "搜索关键词")
            @ToolParamMeta(example = "今天天气") String query,
            @ToolParam(description = "返回结果数量（默认5，最大10）")
            @ToolParamMeta(example = "5") int maxResults) {
        log.info("[Tool:web_search] 搜索: query={}, maxResults={}", query, maxResults);

        if (apiKey == null || apiKey.isBlank()) {
            return "联网搜索未配置，请在配置文件中设置 lightbot.tavily.api-key";
        }

        try {
            Map<String, Object> body = Map.of(
                    "query", query,
                    "max_results", Math.min(Math.max(maxResults, 1), 10),
                    "search_depth", "basic",
                    "include_answer", true);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.tavily.com/search"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("[Tool:web_search] API返回错误: status={}, body={}", response.statusCode(), response.body());
                return "搜索请求失败，HTTP状态码: " + response.statusCode();
            }

            JsonNode root = objectMapper.readTree(response.body());

            // 1. 构建 JSON 返回
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("query", query);

            JsonNode answer = root.get("answer");
            output.put("answer", (answer != null && !answer.isNull() && !answer.asText().isBlank())
                    ? answer.asText() : null);

            JsonNode results = root.get("results");
            List<Map<String, Object>> items = new ArrayList<>();
            if (results != null && results.isArray()) {
                for (JsonNode item : results) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("title", item.has("title") ? item.get("title").asText() : "无标题");
                    entry.put("url", item.has("url") ? item.get("url").asText() : "");
                    entry.put("content", item.has("content") ? item.get("content").asText() : "");
                    items.add(entry);
                }
            }
            output.put("results", items);
            output.put("total", items.size());

            return objectMapper.writeValueAsString(output);
        } catch (Exception e) {
            log.error("[Tool:web_search] 搜索异常: query={}, error={}", query, e.getMessage());
            return "搜索过程中发生错误: " + e.getMessage();
        }
    }
}
