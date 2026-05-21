package com.lightbot.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Redis 工具类，封装任务队列操作
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
}
