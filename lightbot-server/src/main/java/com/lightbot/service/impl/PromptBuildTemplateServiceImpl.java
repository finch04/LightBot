package com.lightbot.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lightbot.entity.PromptBuildTemplate;
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
}
