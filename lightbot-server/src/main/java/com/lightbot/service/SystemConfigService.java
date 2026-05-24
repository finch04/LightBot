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
     * 获取默认AI配置
     *
     * @return 默认AI配置
     */
    DefaultAiConfigDTO getDefaultAiConfig();

    /**
     * 更新默认AI配置
     *
     * @param config 默认AI配置
     */
    void updateDefaultAiConfig(DefaultAiConfigDTO config);
}