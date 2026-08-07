package com.lightbot.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.Result;
import com.lightbot.entity.ApiKey;
import com.lightbot.enums.ApiKeyPermission;
import com.lightbot.enums.ErrorCode;
import com.lightbot.service.ApiKeyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * API Key 认证拦截器
 * <p>在 Sa-Token 拦截器之前执行，识别 lbkey_ 前缀的 Bearer Token 并走 API Key 认证。</p>
 * <p>按 Key 权限放行：</p>
 * <ul>
 *   <li>{@code chat}：仅对话相关接口</li>
 *   <li>{@code full}：对话 + {@code /api/open/v1/**} 开放数据接口</li>
 * </ul>
 * <p>控制台资产 / 管理端接口一律不可用 API Key 访问。</p>
 *
 * @author finch
 * @since 2026-06-25
 */
@Component
@RequiredArgsConstructor
public class ApiKeyAuthInterceptor implements HandlerInterceptor {

    public static final String ATTR_API_KEY_USER_ID = "apiKeyUserId";
    public static final String ATTR_API_KEY_ENTITY = "apiKeyEntity";

    private final ApiKeyService apiKeyService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return true;
        }

        String token = auth.substring(7).trim();
        if (!token.startsWith("lbkey_")) {
            return true;
        }

        // 1. 先认证，再按权限判断路径（需知道 chat / full）
        ApiKey apiKey = apiKeyService.authenticateWithDetails(token);
        if (apiKey == null) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    ErrorCode.API_KEY_INVALID.getCode(), "API Key无效或已过期");
            return false;
        }

        // 2. 请求频率限制
        if (!apiKeyService.checkRateLimit(apiKey.getId(), apiKey.getRateLimit())) {
            writeError(response, HttpStatus.TOO_MANY_REQUESTS.value(),
                    10005, "请求过于频繁，限制 " + apiKey.getRateLimit() + " 次/分钟");
            return false;
        }

        // 3. 按权限校验路径
        ApiKeyPermission permission = apiKey.getPermissions() != null
                ? apiKey.getPermissions()
                : ApiKeyPermission.CHAT;
        if (!isAllowedPath(request, permission)) {
            ErrorCode error = isOpenApiPath(request.getRequestURI()) && permission == ApiKeyPermission.CHAT
                    ? ErrorCode.API_KEY_PERMISSION_INSUFFICIENT
                    : ErrorCode.API_KEY_PATH_FORBIDDEN;
            writeError(response, HttpServletResponse.SC_FORBIDDEN, error.getCode(), error.getMessage());
            return false;
        }

        // 4. Agent 作用域在 InitMiddleware 按请求体 agentId 校验
        request.setAttribute(ATTR_API_KEY_USER_ID, com.lightbot.constant.EnterpriseActors.API_KEY);
        request.setAttribute(ATTR_API_KEY_ENTITY, apiKey);
        return true;
    }

    /**
     * chat：对话能力；full：对话 + 开放 API（/api/open/v1/**）
     */
    private boolean isAllowedPath(HttpServletRequest request, ApiKeyPermission permission) {
        String uri = request.getRequestURI();
        if (isChatApiPath(request.getMethod(), uri)) {
            return true;
        }
        return permission == ApiKeyPermission.FULL && isOpenApiPath(uri);
    }

    private boolean isOpenApiPath(String uri) {
        return uri != null && uri.startsWith("/api/open/v1/");
    }

    /**
     * 对话集成入口（不含 /api/chat/sessions 会话管理，那是控制台排障用）
     */
    private boolean isChatApiPath(String method, String uri) {
        if (uri == null) {
            return false;
        }
        if ("POST".equalsIgnoreCase(method)) {
            return "/api/chat".equals(uri)
                    || "/api/chat/stream".equals(uri)
                    || "/api/chat/stream/stop".equals(uri)
                    || "/api/chat/reconnect".equals(uri)
                    || "/api/chat/attachments".equals(uri);
        }
        if ("GET".equalsIgnoreCase(method)) {
            return "/api/chat/rag-references".equals(uri);
        }
        return false;
    }

    private void writeError(HttpServletResponse response, int status, int code, String message) throws Exception {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(code, message)));
    }
}
