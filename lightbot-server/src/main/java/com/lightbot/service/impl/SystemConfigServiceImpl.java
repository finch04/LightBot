package com.lightbot.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.entity.SystemConfig;
import com.lightbot.mapper.SystemConfigMapper;
import com.lightbot.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
    public DefaultAiConfig getDefaultAiConfig() {
        String value = getConfigValue(DEFAULT_AI_PROVIDER_KEY);
        if (value == null || value.isBlank()) {
            return new DefaultAiConfig(null, null);
        }
        try {
            var node = objectMapper.readTree(value);
            Long providerId = node.has("providerId") && !node.get("providerId").isNull()
                    ? node.get("providerId").asLong() : null;
            String modelId = node.has("modelId") && !node.get("modelId").isNull()
                    ? node.get("modelId").asText() : null;
            return new DefaultAiConfig(providerId, modelId);
        } catch (Exception e) {
            log.warn("[SystemConfig] 解析default_ai_provider失败: {}", e.getMessage());
            return new DefaultAiConfig(null, null);
        }
    }

    @Override
    public void updateDefaultAiConfig(DefaultAiConfig config) {
        try {
            String value = objectMapper.writeValueAsString(config);
            updateConfigValue(DEFAULT_AI_PROVIDER_KEY, value);
        } catch (Exception e) {
            log.error("[SystemConfig] 更新default_ai_provider失败: {}", e.getMessage());
        }
    }
}