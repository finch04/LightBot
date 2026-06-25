package com.lightbot.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.BizException;
import com.lightbot.constant.ToolResultPrefixes;
import com.lightbot.entity.Tool;
import com.lightbot.enums.AuthType;
import com.lightbot.enums.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * API 工具执行引擎
 * <p>负责参数校验、认证注入、参数路由、SSRF 防护和 HTTP 执行</p>
 *
 * @author finch
 * @since 2026-06-25
 */
@Slf4j
@Service
public class ApiToolExecutionService {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private static final int TIMEOUT_SECONDS = 30;

    private final ObjectMapper objectMapper;

    public ApiToolExecutionService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 执行 API 工具
     *
     * @param tool   工具实体
     * @param inputs 输入参数
     * @return 执行结果字符串
     */
    public String execute(Tool tool, Map<String, Object> inputs) {
        // 1. 参数校验
        validateInputs(tool, inputs);

        // 2. SSRF 防护
        validateUrl(tool.getEndpointUrl());

        // 3. 构建认证头
        Map<String, String> authHeaders = buildAuthHeaders(tool);

        // 4. 参数路由 + 构建 HTTP 请求
        HttpRequest request = buildRequest(tool, inputs, authHeaders);

        // 5. 执行 HTTP 请求
        return doExecute(request);
    }

    /**
     * 校验必填参数
     */
    private void validateInputs(Tool tool, Map<String, Object> inputs) {
        String schema = tool.getInputSchema();
        if (schema == null || schema.isBlank()) return;

        try {
            JsonNode root = objectMapper.readTree(schema);
            JsonNode required = root.get("required");
            if (required == null || !required.isArray()) return;

            List<String> missing = new ArrayList<>();
            for (JsonNode field : required) {
                String name = field.asText();
                Object value = inputs.get(name);
                if (value == null || (value instanceof String s && s.isBlank())) {
                    missing.add(name);
                }
            }
            if (!missing.isEmpty()) {
                throw new BizException(ErrorCode.TOOL_MISSING_PARAM,
                        "缺少必填参数: " + String.join(", ", missing));
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[ApiToolExecution] 解析 inputSchema 失败: toolId={}, error={}", tool.getId(), e.getMessage());
        }
    }

    /**
     * SSRF 防护：校验 URL 是否允许访问
     */
    private void validateUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new BizException(ErrorCode.TOOL_EXEC_FAILED, "API 地址不能为空");
        }

        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.TOOL_SSRF_BLOCKED, "URL 格式不合法");
        }

        String scheme = uri.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw new BizException(ErrorCode.TOOL_SSRF_BLOCKED, "仅允许 http/https 协议");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new BizException(ErrorCode.TOOL_SSRF_BLOCKED, "URL 缺少主机名");
        }

        String lowerHost = host.toLowerCase();
        if ("localhost".equals(lowerHost)) {
            throw new BizException(ErrorCode.TOOL_SSRF_BLOCKED, "禁止访问 localhost");
        }
        if ("[::1]".equals(lowerHost) || "[0:0:0:0:0:0:0:1]".equals(lowerHost)) {
            throw new BizException(ErrorCode.TOOL_SSRF_BLOCKED, "禁止访问本地回环地址");
        }

        try {
            InetAddress addr = InetAddress.getByName(host);
            if (addr.isLoopbackAddress()) {
                throw new BizException(ErrorCode.TOOL_SSRF_BLOCKED, "禁止访问回环地址");
            }
            if (addr.isSiteLocalAddress()) {
                throw new BizException(ErrorCode.TOOL_SSRF_BLOCKED, "禁止访问内网地址");
            }
            if (addr.isLinkLocalAddress()) {
                throw new BizException(ErrorCode.TOOL_SSRF_BLOCKED, "禁止访问链路本地地址");
            }
            byte[] rawAddr = addr.getAddress();
            if (rawAddr != null && rawAddr.length == 4
                    && (rawAddr[0] & 0xFF) == 169 && (rawAddr[1] & 0xFF) == 254) {
                throw new BizException(ErrorCode.TOOL_SSRF_BLOCKED, "禁止访问元数据服务");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[ApiToolExecution] DNS 解析失败: {}", e.getMessage());
        }
    }

    /**
     * 构建认证头
     */
    private Map<String, String> buildAuthHeaders(Tool tool) {
        Map<String, String> headers = new HashMap<>();
        AuthType authType = tool.getAuthType();
        if (authType == null || authType == AuthType.NONE) {
            return headers;
        }

        String authConfig = tool.getAuthConfig();
        Map<String, Object> config = parseJson(authConfig);

        switch (authType) {
            case BEARER -> {
                String token = getStr(config, "token");
                if (token != null && !token.isBlank()) {
                    headers.put("Authorization", "Bearer " + token);
                }
            }
            case API_KEY -> {
                String apiKey = getStr(config, "apiKey");
                String headerName = getStr(config, "headerName");
                if (apiKey != null && !apiKey.isBlank()) {
                    String hName = (headerName != null && !headerName.isBlank()) ? headerName : "X-API-Key";
                    headers.put(hName, apiKey);
                }
            }
            case OAUTH -> {
                String accessToken = getStr(config, "accessToken");
                if (accessToken != null && !accessToken.isBlank()) {
                    headers.put("Authorization", "Bearer " + accessToken);
                }
            }
        }
        return headers;
    }

    /**
     * 构建 HTTP 请求：参数路由 + URL 模板替换
     */
    @SuppressWarnings("unchecked")
    private HttpRequest buildRequest(Tool tool, Map<String, Object> inputs, Map<String, String> authHeaders) {
        String url = tool.getEndpointUrl();
        String method = resolveMethod(tool);
        Map<String, String> headers = new HashMap<>(authHeaders);

        // 从 inputSchema 解析参数位置定义
        Map<String, String> paramLocations = parseParamLocations(tool.getInputSchema());

        Map<String, String> queryParams = new LinkedHashMap<>();
        Map<String, Object> bodyParams = new LinkedHashMap<>();
        Map<String, String> headerParams = new LinkedHashMap<>();

        // 按 x-location 分发参数
        for (Map.Entry<String, Object> entry : inputs.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value == null) continue;

            String location = paramLocations.getOrDefault(key, getDefaultLocation(method));
            switch (location) {
                case "path" -> url = url.replace("{" + key + "}", String.valueOf(value));
                case "header" -> headerParams.put(key, String.valueOf(value));
                case "query" -> queryParams.put(key, String.valueOf(value));
                default -> bodyParams.put(key, value);
            }
        }

        headers.putAll(headerParams);

        // 构建 URL（附加 query 参数）
        if (!queryParams.isEmpty()) {
            String query = queryParams.entrySet().stream()
                    .map(e -> urlEncode(e.getKey()) + "=" + urlEncode(e.getValue()))
                    .collect(Collectors.joining("&"));
            url = url.contains("?") ? url + "&" + query : url + "?" + query;
        }

        // 构建请求体
        String body = null;
        if (!bodyParams.isEmpty()) {
            try {
                body = objectMapper.writeValueAsString(bodyParams);
            } catch (Exception e) {
                log.warn("[ApiToolExecution] 序列化请求体失败: {}", e.getMessage());
            }
        }

        // SSRF 校验替换后的 URL
        validateUrl(url);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS));

        headers.forEach(builder::header);

        if (body != null && !body.isBlank()) {
            if (!headers.containsKey("Content-Type")) {
                builder.header("Content-Type", "application/json");
            }
        }

        String upper = method.toUpperCase();
        switch (upper) {
            case "POST" -> builder.POST(bodyPublisher(body));
            case "PUT" -> builder.PUT(bodyPublisher(body));
            case "PATCH" -> builder.method("PATCH", bodyPublisher(body));
            case "DELETE" -> builder.method("DELETE", body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body));
            default -> builder.GET();
        }

        return builder.build();
    }

    /**
     * 执行 HTTP 请求并处理响应
     */
    private String doExecute(HttpRequest request) {
        try {
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            String body = response.body();

            if (statusCode >= 200 && statusCode < 300) {
                return body;
            }

            log.warn("[ApiToolExecution] HTTP 错误: status={}, body={}", statusCode, truncate(body, 500));
            return ToolResultPrefixes.failureJson("HTTP " + statusCode + ": " + truncate(body, 200));
        } catch (java.net.ConnectException e) {
            log.error("[ApiToolExecution] 连接失败: {}", e.getMessage());
            return ToolResultPrefixes.failureJson("连接失败: " + e.getMessage());
        } catch (java.net.http.HttpTimeoutException e) {
            log.error("[ApiToolExecution] 请求超时: {}", e.getMessage());
            return ToolResultPrefixes.failureJson("请求超时（" + TIMEOUT_SECONDS + "秒）");
        } catch (Exception e) {
            log.error("[ApiToolExecution] 请求异常: {}", e.getMessage(), e);
            return ToolResultPrefixes.failureJson("请求异常: " + e.getMessage());
        }
    }

    // ── 工具方法 ──

    /**
     * 从 inputSchema 解析参数位置（x-location 扩展字段）
     */
    private Map<String, String> parseParamLocations(String inputSchema) {
        Map<String, String> locations = new HashMap<>();
        if (inputSchema == null || inputSchema.isBlank()) return locations;

        try {
            JsonNode root = objectMapper.readTree(inputSchema);
            JsonNode properties = root.get("properties");
            if (properties == null || !properties.isObject()) return locations;

            properties.fields().forEachRemaining(entry -> {
                JsonNode xLoc = entry.getValue().get("x-location");
                if (xLoc != null && xLoc.isTextual()) {
                    locations.put(entry.getKey(), xLoc.asText().toLowerCase());
                }
            });
        } catch (Exception e) {
            log.warn("[ApiToolExecution] 解析参数位置失败: {}", e.getMessage());
        }
        return locations;
    }

    /**
     * 从 Tool 配置中解析 HTTP 方法，默认 GET
     */
    private String resolveMethod(Tool tool) {
        String config = tool.getConfig();
        if (config != null && !config.isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(config);
                JsonNode methodNode = root.get("method");
                if (methodNode != null && methodNode.isTextual()) {
                    return methodNode.asText().toUpperCase();
                }
            } catch (Exception ignored) {
            }
        }
        return "GET";
    }

    /**
     * 无 x-location 时的默认参数位置
     */
    private String getDefaultLocation(String method) {
        return switch (method.toUpperCase()) {
            case "GET", "DELETE" -> "query";
            default -> "body";
        };
    }

    private HttpRequest.BodyPublisher bodyPublisher(String body) {
        if (body == null || body.isBlank()) {
            return HttpRequest.BodyPublishers.noBody();
        }
        return HttpRequest.BodyPublishers.ofString(body);
    }

    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("[ApiToolExecution] JSON 解析失败: {}", e.getMessage());
            return Map.of();
        }
    }

    private String getStr(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
