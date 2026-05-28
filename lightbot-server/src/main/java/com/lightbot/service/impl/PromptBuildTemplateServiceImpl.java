package com.lightbot.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lightbot.entity.PromptBuildTemplate;
import com.lightbot.common.BizException;
import com.lightbot.mapper.PromptBuildTemplateMapper;
import com.lightbot.service.PromptBuildTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 提示词构建模板服务实现类
 *
 * @author finch
 * @since 2026-05-27
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptBuildTemplateServiceImpl extends ServiceImpl<PromptBuildTemplateMapper, PromptBuildTemplate>
        implements PromptBuildTemplateService {

    @Override
    public List<PromptBuildTemplate> listAll() {
        return list();
    }

    @Override
    public PromptBuildTemplate getByKey(String promptTemplateKey) {
        return lambdaQuery().eq(PromptBuildTemplate::getPromptTemplateKey, promptTemplateKey).one();
    }

    @Override
    public PromptBuildTemplate create(String promptTemplateKey, String templateDesc, String template,
                                      String variables, String modelConfig, String tags) {
        // 1. 校验模板标识唯一性
        PromptBuildTemplate existing = getByKey(promptTemplateKey);
        if (existing != null) {
            throw new BizException("模板标识已存在: " + promptTemplateKey);
        }

        // 2. 创建模板
        PromptBuildTemplate entity = new PromptBuildTemplate();
        entity.setPromptTemplateKey(promptTemplateKey);
        entity.setTemplateDesc(templateDesc);
        entity.setTemplate(template);
        entity.setVariables(variables);
        entity.setModelConfig(modelConfig);
        entity.setTags(tags);

        save(entity);
        return entity;
    }

    @Override
    public void update(Long id, String templateDesc, String template, String variables, String modelConfig, String tags) {
        PromptBuildTemplate entity = getById(id);
        if (entity == null) {
            throw new BizException("模板不存在");
        }

        entity.setTemplateDesc(templateDesc);
        entity.setTemplate(template);
        entity.setVariables(variables);
        entity.setModelConfig(modelConfig);
        entity.setTags(tags);

        updateById(entity);
    }
}
