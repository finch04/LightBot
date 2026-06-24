package com.lightbot.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lightbot.entity.PromptVersion;

import java.util.List;

/**
 * 提示词版本服务接口
 *
 * @author finch
 * @since 2026-05-27
 */
public interface PromptVersionService extends IService<PromptVersion> {

    /**
     * 创建提示词版本
     *
     * @param promptKey    提示词唯一标识
     * @param version      版本号
     * @param versionDesc  版本描述
     * @param template     提示词模板
     * @param variables    变量定义（JSON）
     * @param modelConfig  模型配置（JSON）
     * @param toolConfig   工具配置（JSON）
     * @param status       版本状态（pre/release），null时默认pre
     * @param userId       创建者ID
     * @return 提示词版本实体
     */
    PromptVersion create(String promptKey, String version, String versionDesc, String template,
                         String variables, String modelConfig, String toolConfig, String status, Long userId);

    /**
     * 根据提示词标识和版本号获取版本详情
     *
     * @param promptKey 提示词唯一标识
     * @param version   版本号
     * @return 提示词版本实体
     */
    PromptVersion getByKeyAndVersion(String promptKey, String version);

    /**
     * 查询指定提示词的所有版本
     *
     * @param promptKey 提示词唯一标识
     * @return 版本列表
     */
    List<PromptVersion> listByKey(String promptKey);

    /**
     * 删除指定提示词的所有版本（级联删除，跳过权限校验）
     *
     * @param promptKey 提示词唯一标识
     */
    void deleteByPromptKey(String promptKey);
}
