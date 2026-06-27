package com.lightbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.BizException;
import com.lightbot.constant.ConfigKeys;
import com.lightbot.dto.ModelProviderRequest;
import com.lightbot.entity.ModelProvider;
import com.lightbot.enums.CommonStatus;
import com.lightbot.enums.ErrorCode;
import com.lightbot.mapper.ModelProviderMapper;
import com.lightbot.service.ModelProviderService;
import com.lightbot.service.ModelService;
import com.lightbot.util.ModelProviderCacheUtil;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final ModelService modelService;
    private final ObjectMapper objectMapper;

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
        provider.setConfig(buildProviderConfig(request));
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
        provider.setConfig(buildProviderConfig(request));
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
        // 级联删除关联模型
        try {
            modelService.deleteByProviderId(id);
        } catch (Exception e) {
            log.warn("[ModelProvider] 级联删除模型失败, providerId={}, error={}", id, e.getMessage());
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

    @Override
    public List<ModelProvider> listAllActive() {
        return list(new LambdaQueryWrapper<ModelProvider>()
                .eq(ModelProvider::getStatus, CommonStatus.ACTIVE)
                .orderByDesc(ModelProvider::getCreateTime));
    }

    /**
     * 构建提供商配置 JSON
     *
     * @param request 提供商请求
     * @return 配置 JSON
     */
    private String buildProviderConfig(ModelProviderRequest request) {
        Map<String, Object> config = parseConfig(request.getConfig());
        String defaultModelId = request.getDefaultModelId();
        if (defaultModelId != null) {
            if (defaultModelId.isBlank()) {
                config.remove(ConfigKeys.Agent.MODEL_ID);
            } else {
                config.put(ConfigKeys.Agent.MODEL_ID, defaultModelId.trim());
            }
        }
        try {
            return objectMapper.writeValueAsString(config);
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, e);
        }
    }

    /**
     * 解析配置 JSON
     *
     * @param config 配置 JSON
     * @return 配置 Map
     */
    private Map<String, Object> parseConfig(String config) {
        if (config == null || config.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(config, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("[ModelProvider] 配置JSON解析失败: {}", e.getMessage());
            return new HashMap<>();
        }
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
