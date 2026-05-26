package com.lightbot.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lightbot.dto.DefaultAiConfigDTO;
import com.lightbot.entity.SystemConfig;

/**
 * 系统配置服务接口
 *
 * @author finch
 * @since 2026-05-24
 */
public interface SystemConfigService extends IService<SystemConfig> {

    /**
     * 获取配置值
     *
     * @param configKey 配置键
     * @return 配置值（JSON字符串），不存在返回null
     */
    String getConfigValue(String configKey);

    /**
     * 更新配置值
     *
     * @param configKey   配置键
     * @param configValue 配置值（JSON字符串）
     */
    void updateConfigValue(String configKey, String configValue);

    /**
     * 获取默认对话模型配置（兼容旧接口）
     *
     * @return 默认对话模型配置
     */
    DefaultAiConfigDTO getDefaultAiConfig();

    /**
     * 更新默认对话模型配置（兼容旧接口）
     *
     * @param config 默认对话模型配置
     */
    void updateDefaultAiConfig(DefaultAiConfigDTO config);

    /**
     * 获取默认对话模型配置
     *
     * @return 默认对话模型配置
     */
    DefaultAiConfigDTO getDefaultChatModelConfig();

    /**
     * 更新默认对话模型配置
     *
     * @param config 默认对话模型配置
     */
    void updateDefaultChatModelConfig(DefaultAiConfigDTO config);

    /**
     * 获取默认向量模型配置
     *
     * @return 默认向量模型配置
     */
    DefaultAiConfigDTO getDefaultEmbeddingModelConfig();

    /**
     * 更新默认向量模型配置
     *
     * @param config 默认向量模型配置
     */
    void updateDefaultEmbeddingModelConfig(DefaultAiConfigDTO config);

    /**
     * 获取默认TTS模型配置
     *
     * @return 默认TTS模型配置
     */
    DefaultAiConfigDTO getDefaultTtsModelConfig();

    /**
     * 更新默认TTS模型配置
     *
     * @param config 默认TTS模型配置
     */
    void updateDefaultTtsModelConfig(DefaultAiConfigDTO config);

    /**
     * 获取默认重排模型配置
     */
    DefaultAiConfigDTO getDefaultRerankModelConfig();

    /**
     * 更新默认重排模型配置
     */
    void updateDefaultRerankModelConfig(DefaultAiConfigDTO config);
}