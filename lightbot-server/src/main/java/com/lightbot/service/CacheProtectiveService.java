package com.lightbot.service;

import com.lightbot.util.BloomFilterHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * 缓存防护服务
 * <p>封装布隆过滤器防穿透 + 分布式锁防击穿 + 随机TTL防雪崩的三层防护逻辑</p>
 *
 * @author finch
 * @since 2026-06-21
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheProtectiveService {

    private final CacheManager cacheManager;
    private final BloomFilterHelper bloomFilterHelper;
    private final StringRedisTemplate stringRedisTemplate;

    /** 基础 TTL（秒） */
    private static final long BASE_TTL_SECONDS = 2 * 60 * 60;
    /** 随机偏移范围（秒）：0 ~ 5 分钟 */
    private static final long RANDOM_OFFSET_SECONDS = 300;
    /** 分布式锁超时（秒） */
    private static final long LOCK_TIMEOUT_SECONDS = 3;
    /** 空值缓存 TTL（秒）：防穿透，短 TTL */
    private static final long NULL_VALUE_TTL_SECONDS = 60;
    /** 锁前缀 */
    private static final String LOCK_PREFIX = "lightbot:cache:lock:";
    /** 空值标记 */
    private static final String NULL_MARKER = "##NULL##";

    /**
     * 带防护的缓存查询
     * <p>流程：缓存 → 布隆过滤器 → 分布式锁 → DB → 回填缓存</p>
     *
     * @param cacheName 缓存名称
     * @param key       缓存 key
     * @param id        实体 ID（用于布隆过滤器判断）
     * @param dbLoader  DB 查询函数
     * @param <T>       实体类型
     * @return 实体对象，不存在返回 null
     */
    @SuppressWarnings("unchecked")
    public <T> T getWithProtection(String cacheName, Object id, Long entityId, Supplier<T> dbLoader) {
        // 1. 先查缓存
        var cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            T cached = (T) cache.get(id, () -> null);
            if (cached != null) {
                // 空值标记命中，直接返回 null
                if (NULL_MARKER.equals(cached.toString())) {
                    return null;
                }
                return cached;
            }
        }

        // 2. 布隆过滤器判断：一定不存在则直接返回 null（防穿透）
        if (entityId != null && bloomFilterHelper.isInitialized(cacheName)
                && !bloomFilterHelper.mightExist(cacheName, entityId)) {
            log.debug("[CacheProtective] 布隆过滤器拦截: cacheName={}, id={}", cacheName, entityId);
            return null;
        }

        // 3. 分布式锁防击穿
        String lockKey = LOCK_PREFIX + cacheName + ":" + id;
        boolean locked = tryLock(lockKey);
        if (!locked) {
            // 未获取到锁，等待后重试缓存
            sleepQuietly(50);
            if (cache != null) {
                T retry = (T) cache.get(id, () -> null);
                if (retry != null && !NULL_MARKER.equals(retry.toString())) {
                    return retry;
                }
            }
            return null;
        }

        try {
            // 4. double-check 缓存
            if (cache != null) {
                T doubleCheck = (T) cache.get(id, () -> null);
                if (doubleCheck != null && !NULL_MARKER.equals(doubleCheck.toString())) {
                    return doubleCheck;
                }
            }

            // 5. 查询 DB
            T result = dbLoader.get();

            // 6. 回填缓存
            if (cache != null) {
                if (result == null) {
                    // 空值缓存（短 TTL，防穿透）
                    cache.put(id, NULL_MARKER);
                } else {
                    cache.put(id, result);
                }
            }

            return result;
        } finally {
            unlock(lockKey);
        }
    }

    /**
     * 计算带随机偏移的 TTL（防雪崩）
     *
     * @return 随机 TTL（秒）
     */
    public long getRandomTtl() {
        return BASE_TTL_SECONDS + ThreadLocalRandom.current().nextLong(RANDOM_OFFSET_SECONDS);
    }

    private boolean tryLock(String lockKey) {
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", Duration.ofSeconds(LOCK_TIMEOUT_SECONDS));
        return Boolean.TRUE.equals(success);
    }

    private void unlock(String lockKey) {
        try {
            stringRedisTemplate.delete(lockKey);
        } catch (Exception e) {
            log.debug("[CacheProtective] 释放锁失败: {}", lockKey);
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
