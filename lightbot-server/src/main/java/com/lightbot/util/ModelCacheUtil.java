package com.lightbot.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.entity.Model;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 模型缓存工具类（Redis 缓存）
 * <p>读取：Redis → 未命中返回空列表，由调用方回源数据库</p>
 * <p>写入：先改库，再调用本类方法更新缓存</p>
 * <p>启动时由 CacheWarmUpRunner 预热</p>
 *
 * @author finch
 * @since 2026-05-22
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModelCacheUtil {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    private static final String CACHE_PREFIX = "lightbot:model:";
    private static final String ALL_MODELS_KEY = CACHE_PREFIX + "all";

    /**
     * 获取所有模型
     *
     * @return 模型列表，Redis未命中返回空列表
     */
    public List<Model> getAllModels() {
        try {
            String json = stringRedisTemplate.opsForValue().get(ALL_MODELS_KEY);
            if (json != null) {
                return objectMapper.readValue(json, new TypeReference<>() {});
            }
        } catch (Exception e) {
            log.warn("[Cache] 从Redis加载模型列表失败: {}", e.getMessage());
        }
        return List.of();
    }

    /**
     * 获取指定提供商下的模型列表
     *
     * @param providerId 提供商ID
     * @return 模型列表，Redis未命中返回空列表
     */
    public List<Model> getModelsByProviderId(Long providerId) {
        try {
            String json = stringRedisTemplate.opsForValue().get(CACHE_PREFIX + providerId);
            if (json != null) {
                return objectMapper.readValue(json, new TypeReference<>() {});
            }
        } catch (Exception e) {
            log.warn("[Cache] 从Redis加载模型失败: providerId={}, error={}", providerId, e.getMessage());
        }
        return List.of();
    }

    /**
     * 缓存所有模型（启动预热或刷新时调用）
     *
     * @param models 所有模型列表
     */
    public void cacheAllModels(List<Model> models) {
        try {
            String json = objectMapper.writeValueAsString(models);
            stringRedisTemplate.opsForValue().set(ALL_MODELS_KEY, json);
            // 按providerId分组写入各分组的Redis key
            Map<Long, List<Model>> grouped = models.stream()
                    .collect(Collectors.groupingBy(Model::getProviderId));
            grouped.forEach((providerId, list) -> {
                try {
                    stringRedisTemplate.opsForValue().set(CACHE_PREFIX + providerId,
                            objectMapper.writeValueAsString(list));
                } catch (Exception e) {
                    log.warn("[Cache] 缓存模型分组失败: providerId={}, error={}", providerId, e.getMessage());
                }
            });
            log.info("[Cache] 缓存所有模型: count={}", models.size());
        } catch (Exception e) {
            log.warn("[Cache] 缓存所有模型失败: {}", e.getMessage());
        }
    }

    /**
     * 清除指定提供商的模型缓存（删除模型后调用）
     *
     * @param providerId 提供商ID
     */
    public void evictModelsByProviderId(Long providerId) {
        stringRedisTemplate.delete(CACHE_PREFIX + providerId);
        log.debug("[Cache] 清除模型缓存: providerId={}", providerId);
    }

    /**
     * 清除所有模型缓存（手动刷新时调用）
     */
    public void evictAll() {
        var keys = stringRedisTemplate.keys(CACHE_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
        log.info("[Cache] 清除所有模型缓存");
    }

    /**
     * 刷新缓存：清除旧缓存，从传入的列表重新加载到Redis
     *
     * @param allModels 所有模型列表（由调用方从数据库查询）
     */
    public void refreshCache(List<Model> allModels) {
        evictAll();
        cacheAllModels(allModels);
        log.info("[Cache] 模型缓存刷新完成: count={}", allModels.size());
    }
}
