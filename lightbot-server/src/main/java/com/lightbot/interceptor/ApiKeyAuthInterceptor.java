package com.lightbot.interceptor;

import com.lightbot.service.ApiKeyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * API Key 认证拦截器
 * <p>在 Sa-Token 拦截器之前执行，识别 lbkey_ 前缀的 Bearer Token 并走 API Key 认证</p>
 *
 * @author finch
 * @since 2026-06-25
 */
@Component
@RequiredArgsConstructor
public class ApiKeyAuthInterceptor implements HandlerInterceptor {

    public static final String ATTR_API_KEY_USER_ID = "apiKeyUserId";

    private final ApiKeyService apiKeyService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return true;
        }

        String token = auth.substring(7).trim();
        if (!token.startsWith("lbkey_")) {
            return true;
        }

        // API Key 认证
        Long userId = apiKeyService.authenticate(token);
        if (userId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            try {
                response.getWriter().write("{\"code\":10001,\"message\":\"API Key无效或已过期\"}");
            } catch (Exception ignored) {
            }
            return false;
        }

        // 将 userId 存入 request，Sa-Token 拦截器检查时跳过
        request.setAttribute(ATTR_API_KEY_USER_ID, userId);
        // 同时登录 Sa-Token，使下游 getLoginIdAsLong() 可用
        cn.dev33.satoken.stp.StpUtil.login(userId, "apikey");
        return true;
    }
}
