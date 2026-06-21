package com.lightbot.model;

import com.lightbot.common.BizException;
import com.lightbot.constant.ConfigKeys;
import com.lightbot.enums.ErrorCode;
import com.lightbot.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 模型提供商 ID 解析器
 * 统一各处 providerId 解析逻辑，优先级：显式指定 > Agent配置 > 系统默认 > 第一个可用
 *
 * @author finch
 * @since 2026-06-21
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProviderResolver {

    private final ModelFactory modelFactory;
    private final SystemConfigService systemConfigService;

    /**
     * 解析 providerId（无显式值，用于 AgentServiceImpl 等场景）
     * 优先级：系统默认 > 第一个可用
     *
     * @return providerId
     * @throws BizException 无可用提供商时抛出
     */
    public Long resolve() {
        return resolve(null);
    }

    /**
     * 解析 providerId（带显式值，用于 RagServiceImpl 等场景）
     * 优先级：显式指定 > 系统默认 > 第一个可用
     *
     * @param explicitProviderId 显式指定的 providerId，可为 null
     * @return providerId
     * @throws BizException 无可用提供商时抛出
     */
    public Long resolve(Long explicitProviderId) {
        // 1. 显式指定
        if (explicitProviderId != null) {
            return explicitProviderId;
        }

        // 2. 系统默认AI配置
        var defaultConfig = systemConfigService.getDefaultAiConfig();
        if (defaultConfig != null && defaultConfig.getProviderId() != null) {
            return defaultConfig.getProviderId();
        }

        // 3. 第一个可用的提供商
        List<Long> providerIds = modelFactory.getAvailableProviderIds();
        if (providerIds.isEmpty()) {
            throw new BizException(ErrorCode.AI_NO_PROVIDER);
        }
        log.info("[ProviderResolver] 未配置providerId，使用默认提供商: id={}", providerIds.get(0));
        return providerIds.get(0);
    }

    /**
     * 从 Agent 配置 Map 中解析 providerId（用于 InitMiddleware 场景）
     * 优先级：Agent配置 > 第一个可用
     *
     * @param configMap Agent 配置 Map
     * @return providerId
     * @throws BizException 无可用提供商时抛出
     */
    public Long resolveFromConfig(Map<String, Object> configMap) {
        Object providerId = configMap.get(ConfigKeys.Agent.PROVIDER_ID);
        if (providerId != null) {
            return providerId instanceof Number
                    ? ((Number) providerId).longValue()
                    : Long.parseLong(providerId.toString());
        }

        List<Long> providerIds = modelFactory.getAvailableProviderIds();
        if (providerIds.isEmpty()) {
            throw new BizException(ErrorCode.AI_NO_PROVIDER);
        }
        log.info("[ProviderResolver] Agent未配置providerId，使用默认提供商: id={}", providerIds.get(0));
        return providerIds.get(0);
    }
}
