package com.lightbot.util;

import cn.dev33.satoken.stp.StpUtil;
import com.lightbot.constant.ToolResultPrefixes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.Map;

/**
 * 工具维度限流装饰器
 * <p>包装原始 {@link ToolCallback}，在 call 前用 {@link ToolRateLimiter} 按
 * 主体维度判定配额；企业 API Key 按 {@code k:{apiKeyId}} 分桶，避免所有 Key 共用 userId=0。</p>
 *
 * @author finch
 * @since 2026-07-21
 */
@Slf4j
public class RateLimitedToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final ToolRateLimiter rateLimiter;
    private final String rateLimitConfig;

    public RateLimitedToolCallback(ToolCallback delegate, ToolRateLimiter rateLimiter, String rateLimitConfig) {
        this.delegate = delegate;
        this.rateLimiter = rateLimiter;
        this.rateLimitConfig = rateLimitConfig;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        String subject = resolveSubject(toolContext);
        String toolName = delegate.getToolDefinition().name();

        if (!rateLimiter.tryAcquire(subject, toolName, rateLimitConfig)) {
            String msg = "工具调用已被限流，请稍后重试或改用其他方式";
            log.warn("[ToolRateLimit] 拦截调用: subject={}, tool={}", subject, toolName);
            return ToolResultPrefixes.failureJson(msg);
        }

        return toolContext != null
                ? delegate.call(toolInput, toolContext)
                : delegate.call(toolInput);
    }

    /**
     * 优先企业 API Key 分桶，其次用户 ID，最后系统调用 u:0
     */
    private String resolveSubject(ToolContext toolContext) {
        if (toolContext != null) {
            Map<String, Object> ctx = toolContext.getContext();
            if (ctx != null) {
                Object apiKeyId = ctx.get("apiKeyId");
                if (apiKeyId instanceof Number n) {
                    return "k:" + n.longValue();
                }
                if (apiKeyId instanceof String s && !s.isBlank()) {
                    return "k:" + s.trim();
                }
                Object uid = ctx.get("userId");
                if (uid instanceof Number n) {
                    return "u:" + n.longValue();
                }
                if (uid instanceof String s && !s.isBlank()) {
                    try {
                        return "u:" + Long.parseLong(s);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        try {
            return "u:" + StpUtil.getLoginIdAsLong();
        } catch (Exception e) {
            return "u:0";
        }
    }
}
