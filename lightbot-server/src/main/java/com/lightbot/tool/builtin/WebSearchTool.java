package com.lightbot.tool.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.tool.annotation.SystemTool;
import com.lightbot.tool.annotation.ToolParamMeta;
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
@SystemTool(displayName = "联网搜索", description = "联网搜索互联网获取最新信息", tags = {"搜索"})
public class WebSearchTool {

    @Value("${lightbot.tavily.api-key:}")
    private String apiKey;

    private static final ObjectMapper MAPPER = new ObjectMapper();
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
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("[Tool:web_search] API返回错误: status={}, body={}", response.statusCode(), response.body());
                return "搜索请求失败，HTTP状态码: " + response.statusCode();
            }

            JsonNode root = MAPPER.readTree(response.body());
            StringBuilder sb = new StringBuilder();

            // 摘要
            JsonNode answer = root.get("answer");
            if (answer != null && !answer.isNull() && !answer.asText().isBlank()) {
                sb.append("摘要：").append(answer.asText()).append("\n\n");
            }

            // 搜索结果
            JsonNode results = root.get("results");
            if (results != null && results.isArray() && !results.isEmpty()) {
                sb.append("搜索结果：\n");
                for (int i = 0; i < results.size(); i++) {
                    JsonNode item = results.get(i);
                    String title = item.has("title") ? item.get("title").asText() : "无标题";
                    String url = item.has("url") ? item.get("url").asText() : "";
                    String content = item.has("content") ? item.get("content").asText() : "";
                    sb.append(String.format("%d. %s\n   %s\n   %s\n\n", i + 1, title, url, content));
                }
            } else {
                sb.append("未找到相关结果。");
            }

            return sb.toString();
        } catch (Exception e) {
            log.error("[Tool:web_search] 搜索异常: query={}, error={}", query, e.getMessage());
            return "搜索过程中发生错误: " + e.getMessage();
        }
    }
}
