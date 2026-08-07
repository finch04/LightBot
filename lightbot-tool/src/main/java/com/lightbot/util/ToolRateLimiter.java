package com.lightbot.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.enums.RateLimitWindow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 工具维度限流器
 * <p>按 (subject, toolName) 维度对工具调用做固定窗口限流。
 * Redis Key 形如 {@code tool:rate:{subject}:{toolName}:{windowStart}}，
 * 其中 {@code windowStart} 是窗口起点 epoch 秒（按窗口大小对齐），
 * 窗口结束时 key 自然过期。</p>
 *
 * <p>限流配置存储于 {@code tool.rate_limit_config}，JSON 结构：
 * {@code {"limit": 10, "window": "MINUTE|HOUR|DAY"}}。</p>
 *
 * @author finch
 * @since 2026-07-21
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolRateLimiter {

    private static final String KEY_PREFIX = "tool:rate:";

    private final RedisUtil redisUtil;
    private final ObjectMapper objectMapper;

    /**
     * 尝试获取一次调用配额（按用户 ID）
     *
     * @param userId          当前用户 ID（系统级调用传 0）
     * @param toolName        工具标识
     * @param rateLimitConfig {@code {"limit":N,"window":"MINUTE|HOUR|DAY"}}
     * @return true=允许调用，false=已超阈值
     */
    public boolean tryAcquire(Long userId, String toolName, String rateLimitConfig) {
        return tryAcquire("u:" + (userId != null ? userId : 0L), toolName, rateLimitConfig);
    }

    /**
     * 尝试获取一次调用配额（按任意主体，如企业 API Key：{@code k:{apiKeyId}}）
     *
     * @param subject         限流主体
     * @param toolName        工具标识
     * @param rateLimitConfig 限流配置 JSON
     * @return true=允许调用，false=已超限额
     */
    public boolean tryAcquire(String subject, String toolName, String rateLimitConfig) {
        RateLimitConfig cfg = parse(rateLimitConfig);
        if (cfg == null) {
            return true;
        }
        String safeSubject = (subject == null || subject.isBlank()) ? "u:0" : subject;
        long windowSeconds = cfg.window.toSeconds();
        long windowStart = alignToWindowStart(windowSeconds);
        String key = KEY_PREFIX + safeSubject + ":" + toolName + ":" + windowStart;
        long count = redisUtil.rateLimitIncrement(key, windowSeconds);
        if (count > cfg.limit) {
            log.warn("[ToolRateLimit] 触发限流: subject={}, tool={}, count={}, limit={}, window={}",
                    safeSubject, toolName, count, cfg.limit, cfg.window.getCode());
            return false;
        }
        return true;
    }

    private long alignToWindowStart(long windowSeconds) {
        long now = System.currentTimeMillis() / 1000L;
        return now - (now % windowSeconds);
    }

    private RateLimitConfig parse(String rateLimitConfig) {
        if (rateLimitConfig == null || rateLimitConfig.isBlank()) {
            return null;
        }
        try {
            var node = objectMapper.readTree(rateLimitConfig);
            if (!node.has("limit") || !node.has("window")) {
                return null;
            }
            int limit = node.get("limit").asInt(0);
            String windowCode = node.get("window").asText("MINUTE");
            if (limit <= 0) {
                return null;
            }
            return new RateLimitConfig(limit, RateLimitWindow.fromValue(windowCode));
        } catch (Exception e) {
            log.warn("[ToolRateLimit] 解析限流配置失败: config={}, error={}", rateLimitConfig, e.getMessage());
            return null;
        }
    }

    private record RateLimitConfig(int limit, RateLimitWindow window) {}
}
