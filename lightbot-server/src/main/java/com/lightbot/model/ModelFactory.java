package com.lightbot.model;

import com.lightbot.common.BizException;
import com.lightbot.entity.ModelProvider;
import com.lightbot.enums.ErrorCode;
import com.lightbot.enums.ModelProviderType;
import com.lightbot.service.ModelProviderService;
import com.lightbot.util.LlmTraceContext;
import com.lightbot.util.ModelProviderCacheUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.HashMap;
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
    private final ModelProviderCacheUtil cacheUtil;

    private static final String CONNECTIVITY_CHECK_PROMPT = "你好，请回复OK";

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
            ModelProvider provider = resolveProvider(id);
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
        ModelProvider provider = resolveProvider(providerId);
        ModelProviderHandler handler = getHandler(provider.getType());
        return handler.buildChatOptions(provider, config);
    }

    /**
     * 获取指定提供商的配置字段定义（模型调参：temperature、topP 等）
     *
     * @param providerId 模型提供商ID
     * @return 配置字段列表
     */
    public List<ConfigField> getConfigFields(Long providerId) {
        ModelProvider provider = resolveProvider(providerId);
        ModelProviderHandler handler = getHandler(provider.getType());
        return handler.getConfigFields();
    }

    /**
     * 获取指定提供商的模型能力字段定义（多模态、联网搜索等）
     *
     * @param providerId 模型提供商ID
     * @return 能力字段列表
     */
    public List<ConfigField> getModelCapabilities(Long providerId) {
        ModelProvider provider = resolveProvider(providerId);
        ModelProviderHandler handler = getHandler(provider.getType());
        return handler.getModelCapabilities();
    }

    /**
     * 清除指定提供商的 ChatModel 缓存（凭证变更时调用）
     *
     * @param providerId 模型提供商ID
     */
    public void invalidateCache(Long providerId) {
        chatModelCache.remove(providerId);
        cacheUtil.evictProvider(providerId);
        log.info("[ModelFactory] 缓存已清除: providerId={}", providerId);
    }

    /**
     * 清除所有 ChatModel 缓存
     */
    public void invalidateAllCache() {
        chatModelCache.clear();
        cacheUtil.evictAll();
        log.info("[ModelFactory] 所有缓存已清除");
    }

    /**
     * 检查模型提供商连通性（通过已保存的提供商ID）
     *
     * @param providerId 模型提供商ID
     * @return 检查结果消息
     */
    public String checkConnectivity(Long providerId) {
        // 1. 校验提供商存在性（缓存 → 数据库）
        ModelProvider provider = resolveProvider(providerId);

        // 2. 清除缓存后重新创建ChatModel（确保使用最新凭证）
        invalidateCache(providerId);

        // 3. 发送简单请求测试连通性
        return doCheckConnectivity(provider);
    }

    /**
     * 检查模型提供商连通性（通过表单实时数据，不依赖数据库）
     *
     * @param type    提供商类型
     * @param apiKey  API密钥
     * @param baseUrl 基础地址
     * @return 检查结果消息
     */
    public String checkConnectivityByForm(ModelProviderType type, String apiKey, String baseUrl) {
        // 1. 构建临时提供商对象
        ModelProvider provider = new ModelProvider();
        provider.setType(type);
        provider.setApiKey(apiKey);
        provider.setBaseUrl(baseUrl);

        // 2. 使用表单数据测试连通性
        return doCheckConnectivity(provider);
    }

    /**
     * 联网拉取提供商下可用的模型列表
     *
     * @param providerId 模型提供商ID
     * @return 模型信息列表（含类型推断）
     */
    public List<FetchedModel> fetchModels(Long providerId) {
        ModelProvider provider = resolveProvider(providerId);
        ModelProviderHandler handler = getHandler(provider.getType());
        // 按 modelId 去重，保留首次出现的
        return handler.fetchModels(provider).stream()
                .filter(distinctByKey(FetchedModel::getModelId))
                .collect(Collectors.toList());
    }

    private static <T> java.util.function.Predicate<T> distinctByKey(java.util.function.Function<? super T, ?> keyExtractor) {
        java.util.concurrent.ConcurrentHashMap<Object, Boolean> seen = new java.util.concurrent.ConcurrentHashMap<>();
        return t -> seen.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE) == null;
    }

    private String doCheckConnectivity(ModelProvider provider) {
        try {
            ModelProviderHandler handler = getHandler(provider.getType());
            ChatModel chatModel = handler.createChatModel(provider);
            // 使用最便宜的模型进行连通性检查，避免消耗高价值配额
            ChatOptions options = handler.buildChatOptions(provider, Map.of("modelId", handler.getCheapestModel()));
            ChatResponse response = LlmTraceContext.callWithoutTrace(() ->
                    chatModel.call(new Prompt(new UserMessage(CONNECTIVITY_CHECK_PROMPT), options)));
            log.info("[ModelFactory] 连通性检查通过: type={}, model={}", provider.getType(), handler.getCheapestModel());
            return "连接成功，API Key 有效";
        } catch (Exception e) {
            log.warn("[ModelFactory] 连通性检查失败: type={}, error={}", provider.getType(), e.getMessage());
            throw new BizException(ErrorCode.MODEL_PROVIDER_CHECK_FAILED, e.getMessage());
        }
    }

    /**
     * 获取所有可用的 providerId 列表（优先Redis缓存，未命中回源数据库）
     *
     * @return providerId 列表
     */
    public List<Long> getAvailableProviderIds() {
        // 1. 优先从Redis缓存获取
        List<ModelProvider> cached = cacheUtil.getAllProviders();
        if (!cached.isEmpty()) {
            return cached.stream().map(ModelProvider::getId).collect(Collectors.toList());
        }
        // 2. 缓存未命中，回源数据库并刷新缓存
        List<ModelProvider> providers = modelProviderService.list();
        if (!providers.isEmpty()) {
            cacheUtil.cacheAllProviders(providers);
        }
        return providers.stream().map(ModelProvider::getId).collect(Collectors.toList());
    }

    /**
     * 解析提供商（缓存优先，未命中回源数据库并回填缓存）
     *
     * @param providerId 提供商ID
     * @return 提供商实体
     */
    private ModelProvider resolveProvider(Long providerId) {
        // 1. 优先从缓存获取
        ModelProvider provider = cacheUtil.getProvider(providerId);
        // 2. 缓存未命中时回源数据库
        if (provider == null) {
            provider = modelProviderService.getById(providerId);
            if (provider == null) {
                throw new BizException(ErrorCode.MODEL_PROVIDER_NOT_FOUND);
            }
            cacheUtil.cacheProvider(provider);
            log.info("[ModelFactory] 缓存未命中，从数据库加载提供商: id={}", providerId);
        }
        return provider;
    }

    /**
     * 确保 config 含有效 modelId（标题生成等旁路调用避免 unknown-model）
     */
    public void ensureModelIdInConfig(Long providerId, Map<String, Object> config) {
        if (config == null || providerId == null) {
            return;
        }
        Object modelId = config.get("modelId");
        if (modelId != null && !modelId.toString().isBlank()
                && !"unknown-model".equalsIgnoreCase(modelId.toString().trim())) {
            return;
        }
        ModelProvider provider = resolveProvider(providerId);
        config.put("modelId", getHandler(provider.getType()).getCheapestModel());
    }

    private ModelProviderHandler getHandler(ModelProviderType type) {
        ModelProviderHandler handler = handlerMap.get(type);
        if (handler == null) {
            throw new IllegalArgumentException("不支持的模型提供商类型: " + type);
        }
        return handler;
    }
}
