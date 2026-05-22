package com.lightbot.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.entity.ModelProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 模型提供商缓存工具类（Redis 缓存）
 * <p>读取：Redis → 未命中返回null，由调用方回源数据库</p>
 * <p>写入：先改库，再调用本类方法更新缓存</p>
 * <p>启动时由 CacheWarmUpRunner 预热</p>
 *
 * @author finch
 * @since 2026-05-22
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModelProviderCacheUtil {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /** Redis key前缀 */
    private static final String CACHE_PREFIX = "lightbot:model_provider:";
    private static final String ALL_PROVIDERS_KEY = CACHE_PREFIX + "all";

    /**
     * 获取所有模型提供商
     *
     * @return 提供商列表，Redis未命中返回空列表
     */
    public List<ModelProvider> getAllProviders() {
        try {
            String json = stringRedisTemplate.opsForValue().get(ALL_PROVIDERS_KEY);
            if (json != null) {
                return objectMapper.readValue(json, new TypeReference<>() {});
            }
        } catch (Exception e) {
            log.warn("[Cache] 从Redis加载提供商列表失败: {}", e.getMessage());
        }
        return List.of();
    }

    /**
     * 根据ID获取模型提供商
     *
     * @param providerId 提供商ID
     * @return 提供商，Redis未命中返回null
     */
    public ModelProvider getProvider(Long providerId) {
        try {
            String json = stringRedisTemplate.opsForValue().get(CACHE_PREFIX + providerId);
            if (json != null) {
                return objectMapper.readValue(json, ModelProvider.class);
            }
        } catch (Exception e) {
            log.warn("[Cache] 从Redis加载提供商失败: id={}, error={}", providerId, e.getMessage());
        }
        return null;
    }

    /**
     * 缓存单个模型提供商（新增或更新后调用）
     *
     * @param provider 提供商实体
     */
    public void cacheProvider(ModelProvider provider) {
        try {
            String json = objectMapper.writeValueAsString(provider);
            stringRedisTemplate.opsForValue().set(CACHE_PREFIX + provider.getId(), json);
            log.debug("[Cache] 缓存提供商: id={}", provider.getId());
        } catch (Exception e) {
            log.warn("[Cache] 缓存提供商失败: id={}, error={}", provider.getId(), e.getMessage());
        }
    }

    /**
     * 缓存所有模型提供商列表（启动预热或刷新时调用）
     *
     * @param providers 所有提供商列表
     */
    public void cacheAllProviders(List<ModelProvider> providers) {
        try {
            String json = objectMapper.writeValueAsString(providers);
            stringRedisTemplate.opsForValue().set(ALL_PROVIDERS_KEY, json);
            // 同时缓存每个提供商到单独的key
            for (ModelProvider provider : providers) {
                stringRedisTemplate.opsForValue().set(CACHE_PREFIX + provider.getId(),
                        objectMapper.writeValueAsString(provider));
            }
            log.info("[Cache] 缓存所有提供商: count={}", providers.size());
        } catch (Exception e) {
            log.warn("[Cache] 缓存所有提供商失败: {}", e.getMessage());
        }
    }

    /**
     * 清除指定提供商的缓存（删除后调用）
     *
     * @param providerId 提供商ID
     */
    public void evictProvider(Long providerId) {
        stringRedisTemplate.delete(CACHE_PREFIX + providerId);
        log.debug("[Cache] 清除提供商缓存: id={}", providerId);
    }

    /**
     * 清除所有缓存（手动刷新时调用）
     */
    public void evictAll() {
        var keys = stringRedisTemplate.keys(CACHE_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
        log.info("[Cache] 清除所有提供商缓存");
    }

    /**
     * 刷新缓存：清除旧缓存，从传入的列表重新加载到Redis
     *
     * @param allProviders 所有提供商列表（由调用方从数据库查询）
     */
    public void refreshCache(List<ModelProvider> allProviders) {
        evictAll();
        cacheAllProviders(allProviders);
        log.info("[Cache] 提供商缓存刷新完成: count={}", allProviders.size());
    }
}
