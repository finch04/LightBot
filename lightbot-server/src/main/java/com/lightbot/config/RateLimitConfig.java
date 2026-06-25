package com.lightbot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.filter.RateLimitFilter;
import com.lightbot.util.RedisUtil;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 速率限制过滤器注册配置
 * <p>对登录/注册接口限流：同一 IP 每分钟最多 10 次</p>
 * <p>对对话接口限流：同一用户每分钟最多 30 次</p>
 *
 * @author finch
 * @since 2026-06-25
 */
@Configuration
public class RateLimitConfig {

    @Bean
    public FilterRegistrationBean<RateLimitFilter> authRateLimitFilter(
            RedisUtil redisUtil, ObjectMapper objectMapper) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RateLimitFilter(redisUtil, objectMapper, 10, 60));
        registration.addUrlPatterns("/api/auth/login", "/api/auth/register");
        registration.setOrder(1);
        registration.setName("authRateLimitFilter");
        return registration;
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> chatRateLimitFilter(
            RedisUtil redisUtil, ObjectMapper objectMapper) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RateLimitFilter(redisUtil, objectMapper, 30, 60));
        registration.addUrlPatterns("/api/chat", "/api/chat/stream");
        registration.setOrder(2);
        registration.setName("chatRateLimitFilter");
        return registration;
    }
}
