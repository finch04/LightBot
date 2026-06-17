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

import java.time.Duration;
import java.util.Map;

/**
 * Redis 缓存配置
 * <p>基于 Spring Cache + RedisCacheManager，为不同业务对象配置独立 TTL</p>
 * <p>缓存 Key 前缀统一为 cacheName::key，便于 Redis 管理</p>
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
    public static final String CACHE_PROMPT = "prompt";
    public static final String CACHE_SYSTEM_CONFIG = "systemConfig";

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
                .entryTtl(Duration.ofMinutes(30))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer))
                .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> configMap = Map.of(
                CACHE_AGENT,        defaultConfig.entryTtl(Duration.ofMinutes(10)),
                CACHE_KNOWLEDGE,    defaultConfig.entryTtl(Duration.ofMinutes(10)),
                CACHE_TOOL,         defaultConfig.entryTtl(Duration.ofMinutes(10)),
                CACHE_MCP_SERVER,   defaultConfig.entryTtl(Duration.ofMinutes(10)),
                CACHE_SUBAGENT,     defaultConfig.entryTtl(Duration.ofMinutes(10)),
                CACHE_PROMPT,       defaultConfig.entryTtl(Duration.ofMinutes(30)),
                CACHE_SYSTEM_CONFIG, defaultConfig.entryTtl(Duration.ofHours(1))
        );

        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(configMap)
                .transactionAware()
                .build();
    }
}
