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
import com.lightbot.model.ModelFactory;
import com.lightbot.service.ModelProviderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

/**
 * 模型提供商服务实现类
 *
 * @author finch
 * @since 2026-05-19
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelProviderServiceImpl extends ServiceImpl<ModelProviderMapper, ModelProvider>
        implements ModelProviderService {

    private final ModelFactory modelFactory;

    private static final String CONNECTIVITY_CHECK_PROMPT = "你好，请回复OK";

    @Override
    public ModelProvider create(ModelProviderRequest request) {
        // 1. 构建实体并保存
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
        provider.setConfig(request.getConfig());
        updateById(provider);

        // 3. 清除缓存，下次使用时重新创建ChatModel
        modelFactory.invalidateCache(request.getId());
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

    @Override
    public String checkConnectivity(Long id) {
        // 1. 校验提供商存在性
        ModelProvider provider = getById(id);
        if (provider == null) {
            throw new BizException(ErrorCode.MODEL_PROVIDER_NOT_FOUND);
        }

        // 2. 清除缓存后重新创建ChatModel（确保使用最新凭证）
        modelFactory.invalidateCache(id);

        // 3. 发送简单请求测试连通性
        try {
            ChatModel chatModel = modelFactory.getChatModel(id);
            UserMessage userMessage = new UserMessage(CONNECTIVITY_CHECK_PROMPT);
            chatModel.call(new Prompt(userMessage));
            log.info("[ModelProvider] 连通性检查通过: id={}, name={}", id, provider.getName());
            return "连接成功，API Key 有效";
        } catch (Exception e) {
            log.warn("[ModelProvider] 连通性检查失败: id={}, name={}, error={}", id, provider.getName(), e.getMessage());
            throw new BizException(ErrorCode.MODEL_PROVIDER_CHECK_FAILED, e.getMessage());
        }
    }
}
