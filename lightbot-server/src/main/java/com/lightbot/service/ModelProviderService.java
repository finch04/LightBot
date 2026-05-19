package com.lightbot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lightbot.common.BizException;
import com.lightbot.dto.ModelProviderRequest;
import com.lightbot.entity.ModelProvider;
import com.lightbot.enums.CommonStatus;
import com.lightbot.mapper.ModelProviderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 模型提供商服务
 *
 * @author finch
 * @since 2026-05-19
 */
@Service
@RequiredArgsConstructor
public class ModelProviderService extends ServiceImpl<ModelProviderMapper, ModelProvider> {

    public ModelProvider create(ModelProviderRequest request) {
        ModelProvider provider = new ModelProvider();
        provider.setName(request.getName());
        provider.setType(request.getType());
        provider.setApiKey(request.getApiKey());
        provider.setBaseUrl(request.getBaseUrl());
        provider.setConfig(request.getConfig());
        provider.setStatus(CommonStatus.ACTIVE);
        save(provider);
        return provider;
    }

    public ModelProvider update(ModelProviderRequest request) {
        ModelProvider provider = getById(request.getId());
        if (provider == null) {
            throw new BizException("模型提供商不存在");
        }
        provider.setName(request.getName());
        provider.setType(request.getType());
        provider.setApiKey(request.getApiKey());
        provider.setBaseUrl(request.getBaseUrl());
        provider.setConfig(request.getConfig());
        updateById(provider);
        return provider;
    }

    public Page<ModelProvider> listPage(int pageNum, int pageSize) {
        return page(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<ModelProvider>().orderByDesc(ModelProvider::getCreateTime));
    }

    public void deleteById(Long id) {
        if (!removeById(id)) {
            throw new BizException("模型提供商不存在");
        }
    }
}
