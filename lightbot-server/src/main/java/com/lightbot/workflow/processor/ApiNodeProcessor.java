package com.lightbot.workflow.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.enums.NodeType;
import com.lightbot.workflow.NodeExecutionContext;
import com.lightbot.workflow.NodeExecutionResult;
import com.lightbot.workflow.NodeProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTP API 节点：按配置发起请求并返回响应体
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiNodeProcessor extends AbstractFlowNodeProcessor implements NodeProcessor {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{([^}]+)}}");
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private final ObjectMapper objectMapper;

    @Override
    public NodeType getType() {
        return NodeType.API;
    }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext context) {
        Map<String, Object> nodeData = context.getCurrentNodeData();
        if (nodeData == null) {
            return passThrough(context, "result", null);
        }

        String url = resolveTemplate(stringVal(nodeData.get("url")), context.getVariables());
        if (url.isBlank()) {
            throw new IllegalArgumentException("API 地址不能为空");
        }

        // SSRF 防护：校验 URL 合法性
        String ssrfError = validateUrl(url);
        if (ssrfError != null) {
            throw new IllegalArgumentException(ssrfError);
        }
        String method = stringVal(nodeData.get("method"));
        if (method.isBlank()) method = "GET";

        int timeoutSec = 30;
        Object timeout = nodeData.get("timeout");
        if (timeout instanceof Number n) {
            timeoutSec = n.intValue();
        } else if (timeout != null) {
            try {
                timeoutSec = Integer.parseInt(timeout.toString());
            } catch (NumberFormatException ignored) {
            }
        }

        String body = resolveTemplate(stringVal(nodeData.get("body")), context.getVariables());
        Map<String, String> headers = parseHeaders(nodeData.get("headers"), context.getVariables());

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(Math.max(1, timeoutSec)));

        headers.forEach(builder::header);
        if (!headers.containsKey("Content-Type") && !body.isBlank()) {
            builder.header("Content-Type", "application/json");
        }

        String upper = method.toUpperCase();
        switch (upper) {
            case "POST" -> builder.POST(HttpRequest.BodyPublishers.ofString(body));
            case "PUT" -> builder.PUT(HttpRequest.BodyPublishers.ofString(body));
            case "DELETE" -> builder.method("DELETE", body.isBlank()
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body));
            case "PATCH" -> builder.method("PATCH", HttpRequest.BodyPublishers.ofString(body));
            default -> builder.GET();
        }

        try {
            HttpResponse<String> response = HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            Map<String, Object> outputs = new HashMap<>();
            outputs.put("statusCode", response.statusCode());
            outputs.put("body", response.body());
            outputs.put("result", response.body());
            context.getVariables().putAll(outputs);
            return NodeExecutionResult.builder()
                    .nextNodeId(resolveNextNodeId(context))
                    .outputs(outputs)
                    .build();
        } catch (Exception e) {
            throw new IllegalArgumentException("HTTP 请求失败: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parseHeaders(Object headersRaw, Map<String, Object> variables) {
        Map<String, String> headers = new HashMap<>();
        if (headersRaw == null) return headers;
        try {
            String json = resolveTemplate(headersRaw.toString(), variables);
            if (json.isBlank() || "{}".equals(json.trim())) return headers;
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            map.forEach((k, v) -> headers.put(k, v == null ? "" : String.valueOf(v)));
        } catch (Exception e) {
            log.warn("[ApiNodeProcessor] 解析 headers 失败: {}", e.getMessage());
        }
        return headers;
    }

    private String resolveTemplate(String text, Map<String, Object> variables) {
        if (text == null) return "";
        Matcher m = VAR_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String key = m.group(1).trim();
            Object val = variables.get(key);
            m.appendReplacement(sb, Matcher.quoteReplacement(val == null ? "" : String.valueOf(val)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String stringVal(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    /**
     * SSRF 防护：校验 URL 是否允许访问
     */
    private String validateUrl(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return "URL 格式不合法: " + url;
        }

        String scheme = uri.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            return "仅允许 http/https 协议";
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return "URL 缺少主机名";
        }

        String lowerHost = host.toLowerCase();

        // 禁止 localhost
        if ("localhost".equals(lowerHost)) {
            return "禁止访问 localhost";
        }

        // 禁止 IPv6 回环
        if ("[::1]".equals(lowerHost) || "[0:0:0:0:0:0:0:1]".equals(lowerHost)) {
            return "禁止访问本地回环地址";
        }

        // 解析 IP 并检查私有地址
        try {
            InetAddress addr = InetAddress.getByName(host);
            if (addr.isLoopbackAddress()) {
                return "禁止访问回环地址";
            }
            if (addr.isSiteLocalAddress()) {
                return "禁止访问内网地址（10.x/172.16-31.x/192.168.x）";
            }
            if (addr.isLinkLocalAddress()) {
                return "禁止访问链路本地地址";
            }
            // 禁止云厂商元数据服务
            byte[] rawAddr = addr.getAddress();
            if (rawAddr != null && rawAddr.length == 4
                    && (rawAddr[0] & 0xFF) == 169 && (rawAddr[1] & 0xFF) == 254) {
                return "禁止访问元数据服务（169.254.x.x）";
            }
        } catch (Exception e) {
            // DNS 解析失败不阻断，由后续 HTTP 请求处理
            log.warn("[ApiNodeProcessor] URL DNS 解析失败: {}", e.getMessage());
        }

        return null;
    }
}
