package com.lightbot.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lightbot.entity.*;
import com.lightbot.mapper.*;
import com.lightbot.util.ModelCacheUtil;
import com.lightbot.util.ModelProviderCacheUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cache.CacheManager;
import org.springframework.core.annotation.Order;
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
@Order(2)
@RequiredArgsConstructor
public class CacheWarmUpRunner implements ApplicationRunner {

    private final StringRedisTemplate stringRedisTemplate;
    private final ModelProviderMapper modelProviderMapper;
    private final ModelMapper modelMapper;
    private final ModelProviderCacheUtil providerCacheUtil;
    private final ModelCacheUtil modelCacheUtil;
    private final CacheManager cacheManager;
    private final AgentMapper agentMapper;
    private final KnowledgeMapper knowledgeMapper;
    private final ToolMapper toolMapper;
    private final McpServerMapper mcpServerMapper;
    private final SubAgentMapper subAgentMapper;
    private final SkillMapper skillMapper;
    private final SystemConfigMapper systemConfigMapper;

    @Override
    public void run(ApplicationArguments args) {
        // 1. 预热模型提供商缓存（旧逻辑）
        warmUpProviders();
        // 2. 预热模型缓存（旧逻辑）
        warmUpModels();
        // 3. 预热 Spring Cache 管理的业务缓存
        warmUpSpringCaches();
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

    /**
     * 预热 Spring Cache 管理的业务缓存（Agent/Knowledge/Tool/McpServer/SubAgent/Skill/SystemConfig）
     */
    private void warmUpSpringCaches() {
        log.info("[CacheWarmUp] 开始预热业务缓存...");
        warmUpCache(RedisCacheConfig.CACHE_AGENT,
                () -> agentMapper.selectList(new LambdaQueryWrapper<Agent>().eq(Agent::getDeleted, 0)),
                agent -> agent.getId().toString());
        warmUpCache(RedisCacheConfig.CACHE_KNOWLEDGE,
                () -> knowledgeMapper.selectList(new LambdaQueryWrapper<Knowledge>().eq(Knowledge::getDeleted, 0)),
                k -> k.getId().toString());
        warmUpCache(RedisCacheConfig.CACHE_TOOL,
                () -> toolMapper.selectList(new LambdaQueryWrapper<Tool>().eq(Tool::getDeleted, 0)),
                t -> t.getId().toString());
        warmUpCache(RedisCacheConfig.CACHE_MCP_SERVER,
                () -> mcpServerMapper.selectList(new LambdaQueryWrapper<McpServer>().eq(McpServer::getDeleted, 0)),
                m -> m.getId().toString());
        warmUpCache(RedisCacheConfig.CACHE_SUBAGENT,
                () -> subAgentMapper.selectList(new LambdaQueryWrapper<SubAgent>().eq(SubAgent::getDeleted, 0)),
                s -> s.getId().toString());
        warmUpCache(RedisCacheConfig.CACHE_SKILL,
                () -> skillMapper.selectList(new LambdaQueryWrapper<Skill>().eq(Skill::getDeleted, 0)),
                s -> s.getId().toString());
        // SystemConfig 无 deleted 字段，全量加载
        warmUpCache(RedisCacheConfig.CACHE_SYSTEM_CONFIG,
                () -> systemConfigMapper.selectList(null),
                SystemConfig::getConfigKey);
    }

    @FunctionalInterface
    private interface DataSupplier<T> {
        List<T> get();
    }

    private <T> void warmUpCache(String cacheName, DataSupplier<T> supplier,
                                  java.util.function.Function<T, Object> keyExtractor) {
        try {
            var cache = cacheManager.getCache(cacheName);
            if (cache == null) return;
            List<T> data = supplier.get();
            for (T item : data) {
                cache.put(keyExtractor.apply(item), item);
            }
            log.info("[CacheWarmUp] {}缓存预热完成: count={}", cacheName, data.size());
        } catch (Exception e) {
            log.warn("[CacheWarmUp] {}缓存预热失败: {}", cacheName, e.getMessage());
        }
    }
}
