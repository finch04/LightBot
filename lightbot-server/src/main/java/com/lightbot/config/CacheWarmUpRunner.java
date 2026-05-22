package com.lightbot.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lightbot.entity.Model;
import com.lightbot.entity.ModelProvider;
import com.lightbot.mapper.ModelMapper;
import com.lightbot.mapper.ModelProviderMapper;
import com.lightbot.util.ModelCacheUtil;
import com.lightbot.util.ModelProviderCacheUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动时预热模型和提供商缓存到Redis
 * <p>先检查Redis是否已有数据，避免重复加载</p>
 *
 * @author finch
 * @since 2026-05-22
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheWarmUpRunner implements ApplicationRunner {

    private final StringRedisTemplate stringRedisTemplate;
    private final ModelProviderMapper modelProviderMapper;
    private final ModelMapper modelMapper;
    private final ModelProviderCacheUtil providerCacheUtil;
    private final ModelCacheUtil modelCacheUtil;

    @Override
    public void run(ApplicationArguments args) {
        // 1. 预热模型提供商缓存
        warmUpProviders();
        // 2. 预热模型缓存
        warmUpModels();
        log.info("[CacheWarmUp] 缓存预热完成");
    }

    private void warmUpProviders() {
        try {
            String existing = stringRedisTemplate.opsForValue().get("lightbot:model_provider:all");
            if (existing != null && !existing.isEmpty()) {
                log.info("[CacheWarmUp] Redis已有提供商缓存，跳过预热");
                return;
            }
            List<ModelProvider> providers = modelProviderMapper.selectList(
                    new LambdaQueryWrapper<ModelProvider>().orderByDesc(ModelProvider::getCreateTime));
            providerCacheUtil.cacheAllProviders(providers);
            log.info("[CacheWarmUp] 提供商缓存预热完成: count={}", providers.size());
        } catch (Exception e) {
            log.warn("[CacheWarmUp] 提供商缓存预热失败: {}", e.getMessage());
        }
    }

    private void warmUpModels() {
        try {
            String existing = stringRedisTemplate.opsForValue().get("lightbot:model:all");
            if (existing != null && !existing.isEmpty()) {
                log.info("[CacheWarmUp] Redis已有模型缓存，跳过预热");
                return;
            }
            List<Model> models = modelMapper.selectList(
                    new LambdaQueryWrapper<Model>().orderByAsc(Model::getProviderId));
            modelCacheUtil.cacheAllModels(models);
            log.info("[CacheWarmUp] 模型缓存预热完成: count={}", models.size());
        } catch (Exception e) {
            log.warn("[CacheWarmUp] 模型缓存预热失败: {}", e.getMessage());
        }
    }
}
