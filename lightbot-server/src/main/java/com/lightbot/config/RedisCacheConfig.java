package com.lightbot.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Redis 缓存配置
 * <p>基于 Spring Cache + RedisCacheManager，为不同业务对象配置独立 TTL</p>
 * <p>缓存 Key 前缀统一为 lightbot:{cacheName}:{key}</p>
 *
 * @author finch
 * @since 2026-06-17
 */
@Configuration
@EnableCaching
public class RedisCacheConfig {

    /** 缓存名称常量 */
    public static final String CACHE_AGENT = "agent";
    public static final String CACHE_KNOWLEDGE = "knowledge";
    public static final String CACHE_TOOL = "tool";
    public static final String CACHE_MCP_SERVER = "mcpServer";
    public static final String CACHE_SUBAGENT = "subagent";
    public static final String CACHE_SKILL = "skill";
    public static final String CACHE_AGENT_BINDING = "agentBinding";
    public static final String CACHE_PROMPT = "prompt";
    public static final String CACHE_SYSTEM_CONFIG = "systemConfig";
    public static final String CACHE_EVAL_DATASET = "evalDataset";
    public static final String CACHE_EVAL_EVALUATOR = "evalEvaluator";
    public static final String CACHE_EVAL_EXPERIMENT = "evalExperiment";
    public static final String CACHE_DASHBOARD = "dashboard";

    /** 统一缓存前缀 */
    private static final String KEY_PREFIX = "lightbot";

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        om.activateDefaultTyping(
                om.getPolymorphicTypeValidator(),
                DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(om);

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))
                .computePrefixWith(cacheName -> KEY_PREFIX + ":" + cacheName + ":")
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer))
                .disableCachingNullValues();

        // TTL 分级说明：
        // - 12h：极少变更的配置类（systemConfig）
        // - 8h ：工具/能力类，变更频率低（tool/mcpServer/subagent/skill）
        // - 2h ：常规业务实体，变更频率中等（agent/knowledge/agentBinding/prompt/evalDataset/evalEvaluator）
        // - 30m：实验运行态，进度频繁变更（evalExperiment）
        // - 30s：Dashboard 统计，需要近实时
        Map<String, RedisCacheConfiguration> configMap = Map.ofEntries(
                Map.entry(CACHE_AGENT,          defaultConfig.entryTtl(Duration.ofHours(2))),
                Map.entry(CACHE_KNOWLEDGE,      defaultConfig.entryTtl(Duration.ofHours(2))),
                Map.entry(CACHE_TOOL,           defaultConfig.entryTtl(Duration.ofHours(8))),
                Map.entry(CACHE_MCP_SERVER,      defaultConfig.entryTtl(Duration.ofHours(8))),
                Map.entry(CACHE_SUBAGENT,       defaultConfig.entryTtl(Duration.ofHours(8))),
                Map.entry(CACHE_SKILL,           defaultConfig.entryTtl(Duration.ofHours(8))),
                Map.entry(CACHE_AGENT_BINDING,  defaultConfig.entryTtl(Duration.ofHours(2))),
                Map.entry(CACHE_PROMPT,         defaultConfig.entryTtl(Duration.ofHours(2))),
                Map.entry(CACHE_SYSTEM_CONFIG,  defaultConfig.entryTtl(Duration.ofHours(12))),
                Map.entry(CACHE_EVAL_DATASET,   defaultConfig.entryTtl(Duration.ofHours(2))),
                Map.entry(CACHE_EVAL_EVALUATOR, defaultConfig.entryTtl(Duration.ofHours(2))),
                Map.entry(CACHE_EVAL_EXPERIMENT, defaultConfig.entryTtl(Duration.ofMinutes(30))),
                Map.entry(CACHE_DASHBOARD,      defaultConfig.entryTtl(Duration.ofSeconds(30)))
        );

        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(configMap)
                .transactionAware()
                .build();
    }
}
