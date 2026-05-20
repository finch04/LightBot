package com.lightbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lightbot.common.BizException;
import com.lightbot.dto.ModelProviderRequest;
import com.lightbot.entity.ModelProvider;
import com.lightbot.enums.CommonStatus;
import com.lightbot.enums.ErrorCode;
import com.lightbot.mapper.ModelProviderMapper;
import com.lightbot.service.ModelProviderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 模型提供商服务实现类
 * <p>纯数据层 CRUD，不依赖 ModelFactory，避免循环依赖</p>
 *
 * @author finch
 * @since 2026-05-19
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelProviderServiceImpl extends ServiceImpl<ModelProviderMapper, ModelProvider>
        implements ModelProviderService {

    @Override
    public ModelProvider create(ModelProviderRequest request) {
        // 1. 构建实体并保存
        ModelProvider provider = new ModelProvider();
        provider.setName(request.getName());
        provider.setType(request.getType());
        provider.setApiKey(request.getApiKey());
        provider.setBaseUrl(request.getBaseUrl());
        provider.setModelsEndpoint(request.getModelsEndpoint());
        provider.setHeadersJson(request.getHeadersJson());
        provider.setExtraJson(request.getExtraJson());
        provider.setConfig(request.getConfig());
        provider.setStatus(CommonStatus.ACTIVE);
        save(provider);
        return provider;
    }

    @Override
    public ModelProvider update(ModelProviderRequest request) {
        // 1. 校验存在性
        ModelProvider provider = getById(request.getId());
        if (provider == null) {
            throw new BizException(ErrorCode.MODEL_PROVIDER_NOT_FOUND);
        }
        // 2. 更新字段
        provider.setName(request.getName());
        provider.setType(request.getType());
        provider.setApiKey(request.getApiKey());
        provider.setBaseUrl(request.getBaseUrl());
        provider.setModelsEndpoint(request.getModelsEndpoint());
        provider.setHeadersJson(request.getHeadersJson());
        provider.setExtraJson(request.getExtraJson());
        provider.setConfig(request.getConfig());
        updateById(provider);
        return provider;
    }

    @Override
    public Page<ModelProvider> listPage(int pageNum, int pageSize) {
        return page(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<ModelProvider>().orderByDesc(ModelProvider::getCreateTime));
    }

    @Override
    public void deleteById(Long id) {
        if (!removeById(id)) {
            throw new BizException(ErrorCode.MODEL_PROVIDER_NOT_FOUND);
        }
    }
}
