package com.lightbot.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import com.lightbot.enums.UserRole;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 路由鉴权配置
 *
 * @author finch
 * @since 2026-05-20
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> {
            // 日志/任务 SSE 接口：需要登录 + ADMIN 角色
            SaRouter.match("/api/logs/**").check(r -> {
                StpUtil.checkLogin();
                StpUtil.checkRole(UserRole.ADMIN.getCode());
            });
            SaRouter.match("/api/tasks/stream").check(r -> StpUtil.checkLogin());
            // 排除不需要认证的路径
            SaRouter.match("/api/auth/login").stop();
            SaRouter.match("/api/auth/register").stop();
            SaRouter.match("/api/auth/init-status").stop();
            SaRouter.match("/api/auth/init-admin").stop();
            SaRouter.match("GET", "/api/landing/config").stop();
            SaRouter.match("GET", "/api/ocr/health").stop();
            SaRouter.match("GET", "/api/system-config/health").stop();
            // 其余接口需要登录
            StpUtil.checkLogin();
        })).addPathPatterns("/api/**");
    }
}
