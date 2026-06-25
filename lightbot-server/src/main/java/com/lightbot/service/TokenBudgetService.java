package com.lightbot.service;

import com.lightbot.common.BizException;
import com.lightbot.entity.User;
import com.lightbot.enums.ErrorCode;
import com.lightbot.mapper.UserMapper;
import com.lightbot.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Token 预算控制服务
 * <p>基于 Redis 实现用户级和全局级日 Token 限额，防止滥用</p>
 * <p>Redis key 格式：lightbot:token_budget:{scope}:{id}:{date}</p>
 *
 * @author finch
 * @since 2026-06-21
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBudgetService {

    private final StringRedisTemplate stringRedisTemplate;
    private final SystemConfigService systemConfigService;
    private final UserMapper userMapper;

    private static final String KEY_PREFIX = "lightbot:token_budget:";
    private static final long KEY_TTL_HOURS = 25;

    /** 默认用户日限额 */
    private static final long DEFAULT_USER_DAILY_LIMIT = 1_000_000L;
    /** 默认全局日限额 */
    private static final long DEFAULT_GLOBAL_DAILY_LIMIT = 10_000_000L;
    /** 默认单次调用上限 */
    private static final int DEFAULT_SINGLE_CALL_LIMIT = 32_000;

    /**
     * 检查用户 Token 预算，超限则抛出异常
     *
     * @param userId          用户ID
     * @param estimatedTokens 预估消耗量
     */
    public void checkBudget(Long userId, int estimatedTokens) {
        // 1. 单次调用上限
        int singleLimit = getSingleCallLimit();
        if (estimatedTokens > singleLimit) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "单次调用 Token 超限: 预估 " + estimatedTokens + ", 上限 " + singleLimit);
        }

        // 2. 用户日限额
        String userKey = KEY_PREFIX + "user:" + userId + ":" + LocalDate.now();
        Long userUsed = stringRedisTemplate.opsForValue().get(userKey) != null
                ? Long.parseLong(stringRedisTemplate.opsForValue().get(userKey)) : 0L;
        long userLimit = getUserDailyLimit();
        if (userUsed + estimatedTokens > userLimit) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "今日 Token 预算已用尽: 已用 " + userUsed + ", 限额 " + userLimit);
        }

        // 3. 全局日限额
        String globalKey = KEY_PREFIX + "global:" + LocalDate.now();
        Long globalUsed = stringRedisTemplate.opsForValue().get(globalKey) != null
                ? Long.parseLong(stringRedisTemplate.opsForValue().get(globalKey)) : 0L;
        long globalLimit = getGlobalDailyLimit();
        if (globalUsed + estimatedTokens > globalLimit) {
            throw new BizException(ErrorCode.BAD_REQUEST, "系统今日 Token 预算已用尽，请明天再试");
        }
    }

    /**
     * 记录实际 Token 消耗（调用完成后）
     *
     * @param userId          用户ID
     * @param promptTokens    输入 Token 数
     * @param completionTokens 输出 Token 数
     */
    public void recordUsage(Long userId, int promptTokens, int completionTokens) {
        int totalTokens = promptTokens + completionTokens;
        String today = LocalDate.now().toString();

        // 1. 用户维度累加
        String userKey = KEY_PREFIX + "user:" + userId + ":" + today;
        stringRedisTemplate.opsForValue().increment(userKey, totalTokens);
        stringRedisTemplate.expire(userKey, Duration.ofHours(KEY_TTL_HOURS));

        // 2. 全局维度累加
        String globalKey = KEY_PREFIX + "global:" + today;
        stringRedisTemplate.opsForValue().increment(globalKey, totalTokens);
        stringRedisTemplate.expire(globalKey, Duration.ofHours(KEY_TTL_HOURS));

        log.debug("[TokenBudget] userId={}, prompt={}, completion={}, total={}", userId, promptTokens, completionTokens, totalTokens);
    }

    /**
     * 获取用户当日 Token 使用统计
     *
     * @param userId 用户ID
     * @return 统计信息
     */
    public Map<String, Object> getUsageStats(Long userId) {
        String today = LocalDate.now().toString();
        String userKey = KEY_PREFIX + "user:" + userId + ":" + today;
        String globalKey = KEY_PREFIX + "global:" + today;

        Long userUsed = stringRedisTemplate.opsForValue().get(userKey) != null
                ? Long.parseLong(stringRedisTemplate.opsForValue().get(userKey)) : 0L;
        Long globalUsed = stringRedisTemplate.opsForValue().get(globalKey) != null
                ? Long.parseLong(stringRedisTemplate.opsForValue().get(globalKey)) : 0L;

        Map<String, Object> stats = new HashMap<>();
        stats.put("userUsed", userUsed);
        stats.put("userLimit", getUserDailyLimit());
        stats.put("globalUsed", globalUsed);
        stats.put("globalLimit", getGlobalDailyLimit());
        stats.put("date", today);
        return stats;
    }

    /**
     * 获取 Token 限额配置
     */
    public Map<String, Object> getConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("userDailyLimit", getUserDailyLimit());
        config.put("globalDailyLimit", getGlobalDailyLimit());
        config.put("singleCallLimit", getSingleCallLimit());
        return config;
    }

    /**
     * 更新 Token 限额配置
     */
    public void updateConfig(Map<String, Object> config) {
        if (config.containsKey("userDailyLimit")) {
            systemConfigService.updateConfigValue("llm.token.user.dailyLimit",
                    String.valueOf(config.get("userDailyLimit")));
        }
        if (config.containsKey("globalDailyLimit")) {
            systemConfigService.updateConfigValue("llm.token.global.dailyLimit",
                    String.valueOf(config.get("globalDailyLimit")));
        }
        if (config.containsKey("singleCallLimit")) {
            systemConfigService.updateConfigValue("llm.token.singleCallLimit",
                    String.valueOf(config.get("singleCallLimit")));
        }
    }

    /**
     * 获取全局 Token 使用统计
     */
    public Map<String, Object> getGlobalStats() {
        String today = LocalDate.now().toString();
        String globalKey = KEY_PREFIX + "global:" + today;
        Long globalUsed = parseLong(stringRedisTemplate.opsForValue().get(globalKey));

        Map<String, Object> stats = new HashMap<>();
        stats.put("globalUsed", globalUsed);
        stats.put("globalLimit", getGlobalDailyLimit());
        stats.put("date", today);
        return stats;
    }

    /**
     * 获取用户 Token 消耗排行（当日 Top N）
     *
     * @param limit 返回条数
     * @return 排行列表，每项含 userId / usedTokens
     */
    public List<Map<String, Object>> getUserRanking(int limit) {
        String today = LocalDate.now().toString();
        String pattern = KEY_PREFIX + "user:*:" + today;

        // SCAN 扫描所有用户 key
        Set<String> keys = new HashSet<>();
        stringRedisTemplate.execute(connection -> {
            var cursor = connection.scan(
                    org.springframework.data.redis.core.ScanOptions.scanOptions()
                            .match(pattern).count(1000).build());
            cursor.forEachRemaining(bytes -> keys.add(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)));
            return null;
        }, true);

        // 解析 userId → usedTokens，按消耗降序排列
        return keys.stream()
                .map(key -> {
                    String userId = key.replace(KEY_PREFIX + "user:", "").replace(":" + today, "");
                    long used = parseLong(stringRedisTemplate.opsForValue().get(key));
                    Map<String, Object> row = new HashMap<>();
                    row.put("userId", userId);
                    row.put("usedTokens", used);
                    return row;
                })
                .sorted((a, b) -> Long.compare((long) b.get("usedTokens"), (long) a.get("usedTokens")))
                .limit(limit)
                .collect(Collectors.toList());
    }

    private long parseLong(String val) {
        if (val == null || val.isBlank()) return 0L;
        try {
            return Long.parseLong(val);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private long getUserDailyLimit() {
        String val = systemConfigService.getConfigValue("llm.token.user.dailyLimit");
        if (val != null && !val.isBlank()) {
            try {
                return Long.parseLong(val);
            } catch (NumberFormatException ignored) {
            }
        }
        return DEFAULT_USER_DAILY_LIMIT;
    }

    private long getGlobalDailyLimit() {
        String val = systemConfigService.getConfigValue("llm.token.global.dailyLimit");
        if (val != null && !val.isBlank()) {
            try {
                return Long.parseLong(val);
            } catch (NumberFormatException ignored) {
            }
        }
        return DEFAULT_GLOBAL_DAILY_LIMIT;
    }

    private int getSingleCallLimit() {
        String val = systemConfigService.getConfigValue("llm.token.singleCallLimit");
        if (val != null && !val.isBlank()) {
            try {
                return Integer.parseInt(val);
            } catch (NumberFormatException ignored) {
            }
        }
        return DEFAULT_SINGLE_CALL_LIMIT;
    }
}
