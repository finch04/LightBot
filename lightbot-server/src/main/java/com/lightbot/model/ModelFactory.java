package com.lightbot.model;

import com.lightbot.entity.ModelProvider;
import com.lightbot.enums.ModelProviderType;
import com.lightbot.service.ModelProviderService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 模型工厂 — 根据 ModelProvider 动态创建 ChatModel
 * <p>参考 spring-ai-alibaba-admin 的 ModelFactory 模式</p>
 * <p>ChatModel 按 providerId 缓存，ChatOptions 每次调用时构建</p>
 *
 * @author finch
 * @since 2026-05-19
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModelFactory {

    private final List<ModelProviderHandler> handlers;
    private final ModelProviderService modelProviderService;

    private Map<ModelProviderType, ModelProviderHandler> handlerMap;
    private final ConcurrentHashMap<Long, ChatModel> chatModelCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        handlerMap = handlers.stream()
                .collect(Collectors.toMap(ModelProviderHandler::getProviderType, h -> h));
        log.info("[ModelFactory] 已注册 {} 个模型处理器: {}", handlerMap.size(), handlerMap.keySet());
    }

    /**
     * 获取 ChatModel（按 providerId 缓存）
     *
     * @param providerId 模型提供商ID
     * @return ChatModel 实例
     */
    public ChatModel getChatModel(Long providerId) {
        return chatModelCache.computeIfAbsent(providerId, id -> {
            ModelProvider provider = modelProviderService.getById(id);
            if (provider == null) {
                throw new IllegalArgumentException("模型提供商不存在: " + id);
            }
            ModelProviderHandler handler = getHandler(provider.getType());
            log.info("[ModelFactory] 创建 ChatModel: providerId={}, type={}", id, provider.getType());
            return handler.createChatModel(provider);
        });
    }

    /**
     * 根据提供商类型构建 ChatOptions
     *
     * @param providerId 模型提供商ID
     * @param config     Agent config JSONB 解析后的 Map
     * @return ChatOptions 实例
     */
    public ChatOptions buildChatOptions(Long providerId, Map<String, Object> config) {
        ModelProvider provider = modelProviderService.getById(providerId);
        if (provider == null) {
            throw new IllegalArgumentException("模型提供商不存在: " + providerId);
        }
        ModelProviderHandler handler = getHandler(provider.getType());
        return handler.buildChatOptions(config);
    }

    /**
     * 获取指定提供商的配置字段定义
     *
     * @param providerId 模型提供商ID
     * @return 配置字段列表
     */
    public List<ConfigField> getConfigFields(Long providerId) {
        ModelProvider provider = modelProviderService.getById(providerId);
        if (provider == null) {
            throw new IllegalArgumentException("模型提供商不存在: " + providerId);
        }
        ModelProviderHandler handler = getHandler(provider.getType());
        return handler.getConfigFields();
    }

    /**
     * 清除指定提供商的 ChatModel 缓存（凭证变更时调用）
     *
     * @param providerId 模型提供商ID
     */
    public void invalidateCache(Long providerId) {
        chatModelCache.remove(providerId);
        log.info("[ModelFactory] 缓存已清除: providerId={}", providerId);
    }

    /**
     * 清除所有 ChatModel 缓存
     */
    public void invalidateAllCache() {
        chatModelCache.clear();
        log.info("[ModelFactory] 所有缓存已清除");
    }

    /**
     * 获取所有可用的 providerId 列表
     *
     * @return providerId 列表
     */
    public List<Long> getAvailableProviderIds() {
        return modelProviderService.list().stream()
                .map(com.lightbot.entity.ModelProvider::getId)
                .collect(Collectors.toList());
    }

    private ModelProviderHandler getHandler(ModelProviderType type) {
        ModelProviderHandler handler = handlerMap.get(type);
        if (handler == null) {
            throw new IllegalArgumentException("不支持的模型提供商类型: " + type);
        }
        return handler;
    }
}
