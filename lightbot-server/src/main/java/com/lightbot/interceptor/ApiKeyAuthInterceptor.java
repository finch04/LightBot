package com.lightbot.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.Result;
import com.lightbot.entity.ApiKey;
import com.lightbot.service.ApiKeyService;
import com.lightbot.service.impl.ApiKeyServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * API Key 认证拦截器
 * <p>在 Sa-Token 拦截器之前执行，识别 lbkey_ 前缀的 Bearer Token 并走 API Key 认证</p>
 * <p>同时执行：请求频率限制、Agent 作用域校验</p>
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
    private final ApiKeyServiceImpl apiKeyServiceImpl;
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

        // 1. API Key 认证
        ApiKey apiKey = apiKeyService.authenticateWithDetails(token);
        if (apiKey == null) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "API Key无效或已过期");
            return false;
        }

        // 2. 请求频率限制
        if (!apiKeyServiceImpl.checkRateLimit(apiKey.getId(), apiKey.getRateLimit())) {
            writeError(response, HttpStatus.TOO_MANY_REQUESTS.value(),
                    "请求过于频繁，限制 " + apiKey.getRateLimit() + " 次/分钟");
            return false;
        }

        // 3. Agent 作用域校验（仅 /api/chat/** 路径）
        String uri = request.getRequestURI();
        if (uri.startsWith("/api/chat")) {
            String agentId = request.getParameter("agentId");
            if (agentId != null && !agentId.isBlank()) {
                if (!apiKeyServiceImpl.checkAgentScope(apiKey, agentId)) {
                    writeError(response, HttpServletResponse.SC_FORBIDDEN, "该 API Key 无权访问此 Agent");
                    return false;
                }
            }
        }

        // 4. 将 userId 存入 request，Sa-Token 拦截器检查时跳过
        request.setAttribute(ATTR_API_KEY_USER_ID, apiKey.getUserId());
        request.setAttribute(ATTR_API_KEY_ENTITY, apiKey);
        // 同时登录 Sa-Token，使下游 getLoginIdAsLong() 可用
        cn.dev33.satoken.stp.StpUtil.login(apiKey.getUserId(), "apikey");
        return true;
    }

    private void writeError(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(10005, message)));
    }
}
