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
import com.lightbot.util.ModelProviderCacheUtil;
import lombok.RequiredArgsConstructor;

import java.util.List;
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

    private final ModelProviderCacheUtil cacheUtil;

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

        // 2. 同步缓存
        cacheUtil.cacheProvider(provider);
        syncAllProvidersCache();
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

        // 3. 同步缓存
        cacheUtil.cacheProvider(provider);
        syncAllProvidersCache();
        return provider;
    }

    @Override
    public Page<ModelProvider> listPage(int pageNum, int pageSize) {
        return baseMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<ModelProvider>().orderByDesc(ModelProvider::getCreateTime));
    }

    @Override
    public void deleteById(Long id) {
        if (!removeById(id)) {
            throw new BizException(ErrorCode.MODEL_PROVIDER_NOT_FOUND);
        }
        // 同步缓存
        cacheUtil.evictProvider(id);
        syncAllProvidersCache();
    }

    @Override
    public void updateStatus(Long id, String status) {
        ModelProvider provider = getById(id);
        if (provider == null) {
            throw new BizException(ErrorCode.MODEL_PROVIDER_NOT_FOUND);
        }
        provider.setStatus(CommonStatus.fromValue(status));
        updateById(provider);
        // 同步缓存
        cacheUtil.cacheProvider(provider);
        syncAllProvidersCache();
    }

    /**
     * 刷新全部提供商列表缓存
     */
    private void syncAllProvidersCache() {
        List<ModelProvider> all = list(new LambdaQueryWrapper<ModelProvider>()
                .orderByDesc(ModelProvider::getCreateTime));
        cacheUtil.cacheAllProviders(all);
    }
}
