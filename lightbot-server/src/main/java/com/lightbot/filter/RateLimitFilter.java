package com.lightbot.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.Result;
import com.lightbot.util.RedisUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 接口速率限制过滤器
 * <p>基于 Redis INCR + TTL 实现滑动窗口计数，超限返回 429</p>
 *
 * @author finch
 * @since 2026-06-25
 */
@Slf4j
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String KEY_PREFIX = "lightbot:rate:";

    private final RedisUtil redisUtil;
    private final ObjectMapper objectMapper;

    /** 窗口内最大请求数 */
    private final int maxRequests;

    /** 窗口时长（秒） */
    private final int windowSeconds;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String clientIp = getClientIp(request);
        String uri = request.getRequestURI();
        String key = KEY_PREFIX + uri + ":" + clientIp;

        try {
            Long count = redisUtil.increment(key);
            if (count != null && count == 1) {
                redisUtil.set(key, String.valueOf(count), windowSeconds);
            }

            if (count != null && count > maxRequests) {
                log.warn("[速率限制] IP=[{}] URI=[{}] 当前次数=[{}]", clientIp, uri, count);
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write(objectMapper.writeValueAsString(
                        Result.fail(10005, "请求过于频繁，请稍后再试")));
                return;
            }
        } catch (Exception e) {
            // Redis 不可用时放行，不阻断正常业务
            log.error("[速率限制] Redis 操作失败，放行请求", e);
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty()) {
            ip = ip.split(",")[0].trim();
        }
        if (ip == null || ip.isEmpty()) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
