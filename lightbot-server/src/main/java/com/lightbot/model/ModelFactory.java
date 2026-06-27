package com.lightbot.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.BizException;
import com.lightbot.constant.ConfigKeys;
import com.lightbot.entity.ModelProvider;
import com.lightbot.enums.ErrorCode;
import com.lightbot.enums.ModelProviderType;
import com.lightbot.service.ModelProviderService;
import com.lightbot.service.SystemConfigService;
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
    private final SystemConfigService systemConfigService;
    private final ObjectMapper objectMapper;

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
        Long actualId = resolveProviderIdOrDefault(providerId);
        return chatModelCache.computeIfAbsent(actualId, id -> {
            ModelProvider provider = resolveProvider(id);
            ModelProviderHandler handler = getHandler(provider.getType());
            String defaultModelId = resolveModelId(provider, handler);
            log.info("[ModelFactory] 创建 ChatModel: providerId={}, type={}, defaultModel={}", id, provider.getType(), defaultModelId);
            return handler.createChatModel(provider, defaultModelId);
        });
    }

    /**
     * 获取 ChatModel 并构建指定模型的 ChatOptions
     *
     * @param providerId 模型提供商ID
     * @param modelId    指定模型ID（为空时使用 provider 默认模型）
     * @return ChatModel 和 ChatOptions 的封装
     */
    public ChatModelContext getChatModelWithContext(Long providerId, String modelId) {
        return getChatModelWithContext(providerId, modelId, null);
    }

    /**
     * 获取 ChatModel 并构建指定模型 + 自定义参数的 ChatOptions
     *
     * @param providerId  模型提供商ID
     * @param modelId     指定模型ID（为空时使用 provider 默认模型）
     * @param modelParams 模型参数（如 temperature、maxTokens），可为 null
     * @return ChatModel 和 ChatOptions 的封装
     */
    public ChatModelContext getChatModelWithContext(Long providerId, String modelId, Map<String, Object> modelParams) {
        ChatModel chatModel = getChatModel(providerId);
        Long actualId = resolveProviderIdOrDefault(providerId);
        Map<String, Object> config = new HashMap<>();
        if (modelId != null && !modelId.isBlank()) {
            config.put("modelId", modelId);
        }
        if (modelParams != null && !modelParams.isEmpty()) {
            config.putAll(modelParams);
        }
        ChatOptions options = config.isEmpty() ? null : buildChatOptions(actualId, config);
        return new ChatModelContext(chatModel, options);
    }

    /**
     * ChatModel + 可选的 ChatOptions 封装
     */
    public record ChatModelContext(ChatModel chatModel, ChatOptions options) {

        /**
         * 发起调用，有 options 时使用指定模型，否则使用默认模型
         */
        public ChatResponse call(List<org.springframework.ai.chat.messages.Message> messages) {
            Prompt prompt = options != null ? new Prompt(messages, options) : new Prompt(messages);
            return chatModel.call(prompt);
        }
    }

    /**
     * 从 provider config 中解析 modelId，未配置时使用 handler 的最便宜模型
     */
    private String resolveModelId(ModelProvider provider, ModelProviderHandler handler) {
        String config = provider.getConfig();
        if (config != null && !config.isBlank()) {
            try {
                var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(config);
                if (node.has("modelId")) {
                    String modelId = node.get("modelId").asText("");
                    if (!modelId.isBlank()) {
                        return modelId;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return handler.getCheapestModel();
    }

    /**
     * 解析 providerId：null 或 0 时自动使用系统默认模型提供商
     */
    private Long resolveProviderIdOrDefault(Long providerId) {
        if (providerId != null && providerId > 0) {
            return providerId;
        }
        // 1. 优先使用系统默认AI配置的 providerId
        Long defaultId = systemConfigService.getDefaultAiConfig().getProviderId();
        if (defaultId != null && defaultId > 0) {
            log.debug("[ModelFactory] providerId 为空，使用系统默认: {}", defaultId);
            return defaultId;
        }
        // 2. fallback 到第一个可用提供商
        List<Long> available = getAvailableProviderIds();
        if (!available.isEmpty()) {
            log.debug("[ModelFactory] 系统默认未配置，使用第一个可用提供商: {}", available.get(0));
            return available.get(0);
        }
        throw new BizException(ErrorCode.MODEL_PROVIDER_NOT_FOUND);
    }

    /**
     * 根据提供商类型构建 ChatOptions
     *
     * @param providerId 模型提供商ID
     * @param config     Agent config JSONB 解析后的 Map
     * @return ChatOptions 实例
     */
    public ChatOptions buildChatOptions(Long providerId, Map<String, Object> config) {
        Long actualId = resolveProviderIdOrDefault(providerId);
        ModelProvider provider = resolveProvider(actualId);
        ModelProviderHandler handler = getHandler(provider.getType());
        Map<String, Object> effectiveConfig = new HashMap<>(config);
        Object modelId = effectiveConfig.get("modelId");
        if (modelId == null || modelId.toString().isBlank()) {
            effectiveConfig.put("modelId", resolveModelId(provider, handler));
        }
        return handler.buildChatOptions(provider, effectiveConfig);
    }

    /**
     * 获取指定提供商的配置字段定义（模型调参：temperature、topP 等）
     *
     * @param providerId 模型提供商ID
     * @return 配置字段列表
     */
    public List<ConfigField> getConfigFields(Long providerId) {
        Long actualId = resolveProviderIdOrDefault(providerId);
        ModelProvider provider = resolveProvider(actualId);
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
        Long actualId = resolveProviderIdOrDefault(providerId);
        ModelProvider provider = resolveProvider(actualId);
        ModelProviderHandler handler = getHandler(provider.getType());
        return handler.getModelCapabilities();
    }

    /**
     * 获取指定提供商类型的代码默认模型
     *
     * @param type 提供商类型
     * @return 默认模型ID
     */
    public String getDefaultModelId(ModelProviderType type) {
        return getHandler(type).getCheapestModel();
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
        return doCheckConnectivity(provider, null);
    }

    /**
     * 检查模型提供商连通性（通过表单实时数据，不依赖数据库）
     *
     * @param type    提供商类型
     * @param apiKey  API密钥
     * @param baseUrl 基础地址
     * @param modelId 默认模型ID
     * @param completionsPath Chat Completions 请求路径
     * @return 检查结果消息
     */
    public String checkConnectivityByForm(ModelProviderType type, String apiKey, String baseUrl, String modelId, String completionsPath) {
        // 1. 构建临时提供商对象
        ModelProvider provider = new ModelProvider();
        provider.setType(type);
        provider.setApiKey(apiKey);
        provider.setBaseUrl(baseUrl);
        if (completionsPath != null && !completionsPath.isBlank()) {
            provider.setConfig(buildConnectivityConfig(completionsPath));
        }

        // 2. 使用表单数据测试连通性
        return doCheckConnectivity(provider, modelId);
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

    private String doCheckConnectivity(ModelProvider provider, String modelId) {
        try {
            ModelProviderHandler handler = getHandler(provider.getType());
            String checkModelId = resolveCheckModelId(provider, handler, modelId);
            ChatModel chatModel = handler.createChatModel(provider, checkModelId);
            ChatOptions options = handler.buildChatOptions(provider, Map.of("modelId", checkModelId));
            ChatResponse response = LlmTraceContext.callWithoutTrace(() ->
                    chatModel.call(new Prompt(new UserMessage(CONNECTIVITY_CHECK_PROMPT), options)));
            log.info("[ModelFactory] 连通性检查通过: type={}, model={}", provider.getType(), checkModelId);
            return "连接成功，API Key 有效";
        } catch (Exception e) {
            log.warn("[ModelFactory] 连通性检查失败: type={}, error={}", provider.getType(), e.getMessage());
            throw new BizException(ErrorCode.MODEL_PROVIDER_CHECK_FAILED, e.getMessage());
        }
    }

    /**
     * 解析连通性检查模型ID
     *
     * @param provider 提供商实体
     * @param handler 模型处理器
     * @param modelId 表单传入模型ID
     * @return 模型ID
     */
    private String resolveCheckModelId(ModelProvider provider, ModelProviderHandler handler, String modelId) {
        if (modelId != null && !modelId.isBlank()) {
            return modelId.trim();
        }
        return resolveModelId(provider, handler);
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
            log.debug("[ModelFactory] 缓存未命中，从数据库加载提供商: id={}", providerId);
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
        ModelProviderHandler handler = getHandler(provider.getType());
        config.put("modelId", resolveModelId(provider, handler));
    }

    /**
     * 构建连通性检查临时配置
     *
     * @param completionsPath Chat Completions 请求路径
     * @return 配置 JSON
     */
    private String buildConnectivityConfig(String completionsPath) {
        try {
            return objectMapper.writeValueAsString(Map.of(ConfigKeys.Agent.COMPLETIONS_PATH, completionsPath.trim()));
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, e);
        }
    }

    private ModelProviderHandler getHandler(ModelProviderType type) {
        ModelProviderHandler handler = handlerMap.get(type);
        if (handler == null) {
            throw new IllegalArgumentException("不支持的模型提供商类型: " + type);
        }
        return handler;
    }
}
