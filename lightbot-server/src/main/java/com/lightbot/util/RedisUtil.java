package com.lightbot.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Redis 工具类
 *
 * @author finch
 * @since 2026-05-21
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisUtil {

    private final StringRedisTemplate stringRedisTemplate;

    private static final String TASK_QUEUE_KEY = "lightbot:task:queue";

    // ==================== 任务队列 ====================

    /**
     * 推送任务ID到队列右端（FIFO）
     */
    public void pushTask(String taskId) {
        stringRedisTemplate.opsForList().rightPush(TASK_QUEUE_KEY, taskId);
        log.debug("[Redis] 任务入队, taskId={}, queueSize={}", taskId, getQueueSize());
    }

    /**
     * 阻塞弹出队列左端任务ID，超时返回null
     */
    public String popTask(long timeoutSeconds) {
        return stringRedisTemplate.opsForList()
                .leftPop(TASK_QUEUE_KEY, timeoutSeconds, TimeUnit.SECONDS);
    }

    /**
     * 获取当前队列长度
     */
    public long getQueueSize() {
        Long size = stringRedisTemplate.opsForList().size(TASK_QUEUE_KEY);
        return size != null ? size : 0;
    }

    // ==================== 通用缓存 ====================

    /**
     * 设置缓存（带过期时间）
     *
     * @param key     缓存key
     * @param value   缓存值
     * @param timeout 过期时间（秒）
     */
    public void set(String key, String value, long timeout) {
        stringRedisTemplate.opsForValue().set(key, value, timeout, TimeUnit.SECONDS);
    }

    /**
     * 设置缓存（永不过期）
     */
    public void set(String key, String value) {
        stringRedisTemplate.opsForValue().set(key, value);
    }

    /**
     * 获取缓存
     *
     * @return 缓存值，不存在返回null
     */
    public String get(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    /**
     * 删除缓存
     */
    public void delete(String key) {
        stringRedisTemplate.delete(key);
    }

    /**
     * 判断缓存是否存在
     */
    public boolean exists(String key) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
    }

    /**
     * 自增（key 不存在时自动从 0 开始）
     *
     * @return 自增后的值
     */
    public long increment(String key) {
        Long val = stringRedisTemplate.opsForValue().increment(key);
        return val != null ? val : 1L;
    }
}
