package com.lightbot.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.config.RedisCacheConfig;
import com.lightbot.constant.ConfigKeys;
import com.lightbot.dto.DefaultAiConfigDTO;
import com.lightbot.entity.SystemConfig;
import com.lightbot.mapper.SystemConfigMapper;
import com.lightbot.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.Serializable;

/**
 * 系统配置服务实现类
 *
 * @author finch
 * @since 2026-05-24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemConfigServiceImpl extends ServiceImpl<SystemConfigMapper, SystemConfig>
        implements SystemConfigService {

    private final ObjectMapper objectMapper;

    private static final String DEFAULT_AI_PROVIDER_KEY = "default_ai_provider";

    @Override
    @Cacheable(value = RedisCacheConfig.CACHE_SYSTEM_CONFIG, key = "#id")
    public SystemConfig getById(Serializable id) {
        return super.getById(id);
    }

    @Override
    @CacheEvict(value = RedisCacheConfig.CACHE_SYSTEM_CONFIG, key = "#entity.configKey")
    public boolean updateById(SystemConfig entity) {
        return super.updateById(entity);
    }

    @Override
    public String getConfigValue(String configKey) {
        SystemConfig config = getById(configKey);
        return config != null ? config.getConfigValue() : null;
    }

    @Override
    public void updateConfigValue(String configKey, String configValue) {
        SystemConfig config = getById(configKey);
        if (config == null) {
            log.warn("[SystemConfig] 配置项不存在: {}", configKey);
            return;
        }
        config.setConfigValue(configValue);
        updateById(config);
        log.info("[SystemConfig] 配置已更新: key={}, value={}", configKey, configValue);
    }

    @Override
    public DefaultAiConfigDTO getDefaultAiConfig() {
        // 兼容旧接口：默认对话模型配置
        return getDefaultChatModelConfig();
    }

    @Override
    public void updateDefaultAiConfig(DefaultAiConfigDTO config) {
        // 兼容旧接口：更新默认对话模型配置
        updateDefaultChatModelConfig(config);
    }

    @Override
    public DefaultAiConfigDTO getDefaultChatModelConfig() {
        // 优先读取新配置键，如果不存在则兼容旧配置
        String value = getConfigValue(ConfigKeys.System.DEFAULT_CHAT_MODEL);
        if (value == null || value.isBlank()) {
            // 兼容旧配置
            value = getConfigValue(DEFAULT_AI_PROVIDER_KEY);
        }
        return parseModelConfig(value);
    }

    @Override
    public void updateDefaultChatModelConfig(DefaultAiConfigDTO config) {
        try {
            String value = objectMapper.writeValueAsString(config);
            updateConfigValue(ConfigKeys.System.DEFAULT_CHAT_MODEL, value);
        } catch (Exception e) {
            log.error("[SystemConfig] 更新默认对话模型配置失败: {}", e.getMessage());
        }
    }

    @Override
    public DefaultAiConfigDTO getDefaultEmbeddingModelConfig() {
        String value = getConfigValue(ConfigKeys.System.DEFAULT_EMBEDDING_MODEL);
        return parseModelConfig(value);
    }

    @Override
    public void updateDefaultEmbeddingModelConfig(DefaultAiConfigDTO config) {
        try {
            String value = objectMapper.writeValueAsString(config);
            updateConfigValue(ConfigKeys.System.DEFAULT_EMBEDDING_MODEL, value);
        } catch (Exception e) {
            log.error("[SystemConfig] 更新默认向量模型配置失败: {}", e.getMessage());
        }
    }

    @Override
    public DefaultAiConfigDTO getDefaultTtsModelConfig() {
        String value = getConfigValue(ConfigKeys.System.DEFAULT_TTS_MODEL);
        return parseModelConfig(value);
    }

    @Override
    public void updateDefaultTtsModelConfig(DefaultAiConfigDTO config) {
        try {
            String value = objectMapper.writeValueAsString(config);
            updateConfigValue(ConfigKeys.System.DEFAULT_TTS_MODEL, value);
        } catch (Exception e) {
            log.error("[SystemConfig] 更新默认TTS模型配置失败: {}", e.getMessage());
        }
    }

    @Override
    public DefaultAiConfigDTO getDefaultRerankModelConfig() {
        String value = getConfigValue(ConfigKeys.System.DEFAULT_RERANK_MODEL);
        return parseModelConfig(value);
    }

    @Override
    public void updateDefaultRerankModelConfig(DefaultAiConfigDTO config) {
        try {
            String value = objectMapper.writeValueAsString(config);
            updateConfigValue(ConfigKeys.System.DEFAULT_RERANK_MODEL, value);
        } catch (Exception e) {
            log.error("[SystemConfig] 更新默认重排模型配置失败: {}", e.getMessage());
        }
    }

    /**
     * 解析模型配置JSON
     */
    private DefaultAiConfigDTO parseModelConfig(String value) {
        if (value == null || value.isBlank()) {
            return new DefaultAiConfigDTO();
        }
        try {
            var node = objectMapper.readTree(value);
            Long providerId = null;
            String modelId = null;

            if (node.has("providerId") && !node.get("providerId").isNull()) {
                var pidNode = node.get("providerId");
                providerId = pidNode.isTextual() ? Long.parseLong(pidNode.asText()) : pidNode.asLong();
            }

            if (node.has("modelId") && !node.get("modelId").isNull()) {
                modelId = node.get("modelId").asText();
            }

            DefaultAiConfigDTO dto = new DefaultAiConfigDTO();
            dto.setProviderId(providerId);
            dto.setModelId(modelId);
            return dto;
        } catch (Exception e) {
            log.warn("[SystemConfig] 解析模型配置失败: {}", e.getMessage());
            return new DefaultAiConfigDTO();
        }
    }
}