package com.lightbot.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lightbot.entity.PromptBuildTemplate;

import java.util.List;

/**
 * 提示词构建模板服务接口
 *
 * @author finch
 * @since 2026-05-27
 */
public interface PromptBuildTemplateService extends IService<PromptBuildTemplate> {

    /**
     * 查询所有提示词构建模板
     *
     * @return 模板列表
     */
    List<PromptBuildTemplate> listAll();

    /**
     * 根据模板标识获取模板详情
     *
     * @param promptTemplateKey 模板唯一标识
     * @return 模板实体
     */
    PromptBuildTemplate getByKey(String promptTemplateKey);
}
