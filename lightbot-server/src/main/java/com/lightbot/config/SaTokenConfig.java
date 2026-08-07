package com.lightbot.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import com.lightbot.enums.UserRole;
import com.lightbot.interceptor.ApiKeyAuthInterceptor;
import com.lightbot.interceptor.PageSizeLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 路由鉴权配置
 *
 * @author finch
 * @since 2026-05-20
 */
@Configuration
@RequiredArgsConstructor
public class SaTokenConfig implements WebMvcConfigurer {

    private final ApiKeyAuthInterceptor apiKeyAuthInterceptor;
    private final PageSizeLimitInterceptor pageSizeLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. API Key 认证拦截器（在 Sa-Token 之前执行）
        registry.addInterceptor(apiKeyAuthInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/auth/init-status",
                        "/api/auth/init-admin",
                        "/api/landing/config",
                        "/api/ocr/health",
                        "/api/system-config/health"
                )
                .order(0);

        // 2. 分页参数上限拦截器（早于业务，避免恶意大分页打 DB）
        registry.addInterceptor(pageSizeLimitInterceptor)
                .addPathPatterns("/api/**")
                .order(1);

        // 3. Sa-Token 拦截器
        registry.addInterceptor(new SaInterceptor(handle -> {
            // 管理端接口：仅 Sa-Token 登录管理员（企业 API Key 不可走管理端）
            SaRouter.match("/api/admin/**").check(r -> {
                StpUtil.checkLogin();
                StpUtil.checkRole(UserRole.ADMIN.getCode());
            });
            SaRouter.match("/api/tasks/stream").check(r -> StpUtil.checkLogin());
            // 排除不需要认证的路径
            SaRouter.match("/api/auth/login").stop();
            SaRouter.match("/api/auth/init-status").stop();
            SaRouter.match("/api/auth/init-admin").stop();
            SaRouter.match("GET", "/api/landing/config").stop();
            SaRouter.match("GET", "/api/ocr/health").stop();
            SaRouter.match("GET", "/api/system-config/health").stop();
            // 其余接口：已通过 API Key 拦截器放行的请求跳过登录；否则必须登录
            checkLoginOrApiKey();
        })).addPathPatterns("/api/**").order(2);
    }

    /**
     * 检查登录状态：优先判断 API Key 认证，否则走 Sa-Token
     */
    private void checkLoginOrApiKey() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null && attrs.getRequest().getAttribute(ApiKeyAuthInterceptor.ATTR_API_KEY_USER_ID) != null) {
            return;
        }
        StpUtil.checkLogin();
    }
}
