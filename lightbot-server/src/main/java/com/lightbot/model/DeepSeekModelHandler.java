package com.lightbot.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.constant.ConfigKeys;
import com.lightbot.entity.ModelProvider;
import com.lightbot.enums.ModelProviderType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * DeepSeek 模型处理器
 * <p>基于 OpenAI 兼容协议接入 DeepSeek 对话模型</p>
 *
 * @author finch
 * @since 2026-06-27
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeepSeekModelHandler implements ModelProviderHandler {

    private final ObjectMapper objectMapper;

    /** DeepSeek 默认 baseUrl */
    private static final String DEFAULT_BASE_URL = "https://api.deepseek.com";

    /**
     * 获取模型提供商类型
     *
     * @return DeepSeek 提供商类型
     */
    @Override
    public ModelProviderType getProviderType() {
        return ModelProviderType.DEEPSEEK;
    }

    /**
     * 创建 DeepSeek ChatModel
     *
     * @param provider 模型提供商配置
     * @return ChatModel 实例
     */
    @Override
    public ChatModel createChatModel(ModelProvider provider) {
        return createChatModel(provider, getCheapestModel());
    }

    /**
     * 创建指定默认模型的 DeepSeek ChatModel
     *
     * @param provider 模型提供商配置
     * @param defaultModelId 默认模型ID
     * @return ChatModel 实例
     */
    @Override
    public ChatModel createChatModel(ModelProvider provider, String defaultModelId) {
        OpenAiApi api = OpenAiApi.builder()
                .apiKey(provider.getApiKey())
                .baseUrl(resolveBaseUrl(provider))
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder().model(defaultModelId).streamUsage(true).build())
                .build();
    }

    /**
     * 构建 DeepSeek 调用参数
     *
     * @param provider 模型提供商配置
     * @param config Agent 模型配置
     * @return ChatOptions 实例
     */
    @Override
    public ChatOptions buildChatOptions(ModelProvider provider, Map<String, Object> config) {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder();

        String modelId = config.containsKey(ConfigKeys.Agent.MODEL_ID)
                ? config.get(ConfigKeys.Agent.MODEL_ID).toString() : getCheapestModel();
        builder.model(modelId);

        if (config.containsKey(ConfigKeys.Agent.TEMPERATURE)) {
            builder.temperature(toDouble(config.get(ConfigKeys.Agent.TEMPERATURE)));
        }
        if (config.containsKey(ConfigKeys.Agent.TOP_P)) {
            builder.topP(toDouble(config.get(ConfigKeys.Agent.TOP_P)));
        }
        if (config.containsKey(ConfigKeys.Agent.MAX_TOKENS)) {
            builder.maxTokens(toInt(config.get(ConfigKeys.Agent.MAX_TOKENS)));
        }
        if (config.containsKey(ConfigKeys.Agent.PRESENCE_PENALTY)) {
            builder.presencePenalty(toDouble(config.get(ConfigKeys.Agent.PRESENCE_PENALTY)));
        }
        if (config.containsKey(ConfigKeys.Agent.FREQUENCY_PENALTY)) {
            builder.frequencyPenalty(toDouble(config.get(ConfigKeys.Agent.FREQUENCY_PENALTY)));
        }

        OpenAiStreamUsageSupport.enableStreamUsage(builder);
        return builder.build();
    }

    /**
     * 获取 DeepSeek 默认低成本模型
     *
     * @return 默认模型ID
     */
    @Override
    public String getCheapestModel() {
        return "deepseek-chat";
    }

    /**
     * 获取 DeepSeek 参数配置字段
     *
     * @return 配置字段列表
     */
    @Override
    public List<ConfigField> getConfigFields() {
        List<ConfigField> fields = new ArrayList<>();
        fields.add(ConfigField.builder()
                .key(ConfigKeys.Agent.MODEL_ID)
                .label("模型")
                .type("select")
                .options(List.of(
                        ConfigField.Option.builder().value("deepseek-chat").label("DeepSeek Chat").build(),
                        ConfigField.Option.builder().value("deepseek-reasoner").label("DeepSeek Reasoner").build()
                ))
                .defaultValue("deepseek-chat")
                .hint("常规对话选 deepseek-chat，需要推理能力选 deepseek-reasoner")
                .build());
        fields.add(ConfigField.builder()
                .key(ConfigKeys.Agent.TEMPERATURE)
                .label("温度")
                .type("slider")
                .min(0.0).max(2.0).step(0.1)
                .defaultValue(0.7)
                .hint("值越高回答越随机创造性，值越低回答越确定")
                .build());
        fields.add(ConfigField.builder()
                .key(ConfigKeys.Agent.TOP_P)
                .label("核采样")
                .type("slider")
                .min(0.0).max(1.0).step(0.05)
                .defaultValue(1.0)
                .hint("控制词汇选择的多样性")
                .build());
        fields.add(ConfigField.builder()
                .key(ConfigKeys.Agent.MAX_TOKENS)
                .label("最大 Token")
                .type("number")
                .min(256.0).max(8192.0).step(256.0)
                .defaultValue(2048)
                .hint("单次回答的最大长度")
                .build());
        fields.add(ConfigField.builder()
                .key(ConfigKeys.Agent.PRESENCE_PENALTY)
                .label("存在惩罚")
                .type("slider")
                .min(-2.0).max(2.0).step(0.1)
                .defaultValue(0.0)
                .hint("正值降低重复话题的概率")
                .build());
        fields.add(ConfigField.builder()
                .key(ConfigKeys.Agent.FREQUENCY_PENALTY)
                .label("频率惩罚")
                .type("slider")
                .min(-2.0).max(2.0).step(0.1)
                .defaultValue(0.0)
                .hint("正值降低重复用词的概率")
                .build());
        return fields;
    }

    /**
     * 获取 DeepSeek 模型能力字段
     *
     * @return 能力字段列表
     */
    @Override
    public List<ConfigField> getModelCapabilities() {
        return List.of(ConfigField.builder()
                .key(ConfigKeys.Agent.ENABLE_REASONING)
                .label("深度思考")
                .type("switch")
                .defaultValue(false)
                .hint("选择 deepseek-reasoner 模型时开启，用于标记该 Agent 使用推理模型")
                .build());
    }

    /**
     * 拉取 DeepSeek 可用模型列表
     *
     * @param provider 模型提供商配置
     * @return 模型列表
     */
    @Override
    public List<FetchedModel> fetchModels(ModelProvider provider) {
        String url = resolveModelsEndpoint(provider);

        try {
            RestClient.Builder clientBuilder = RestClient.builder()
                    .defaultHeader("Authorization", "Bearer " + provider.getApiKey());
            addExtraHeaders(clientBuilder, provider.getHeadersJson());
            RestClient restClient = clientBuilder.build();

            Map<String, Object> response = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(Map.class);

            List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
            if (data == null) {
                return List.of(FetchedModel.of("deepseek-chat"), FetchedModel.of("deepseek-reasoner"));
            }
            return data.stream()
                    .map(m -> FetchedModel.of(m.get("id").toString()))
                    .sorted(Comparator.comparing(FetchedModel::getModelId))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("[DeepSeekHandler] 拉取模型列表失败: url={}, error={}", url, e.getMessage());
            throw new RuntimeException("拉取模型列表失败: " + e.getMessage());
        }
    }

    /**
     * 解析模型列表获取地址
     *
     * @param provider 提供商实体
     * @return 模型列表地址
     */
    private String resolveModelsEndpoint(ModelProvider provider) {
        if (provider.getModelsEndpoint() != null && !provider.getModelsEndpoint().isBlank()) {
            return provider.getModelsEndpoint();
        }
        return resolveBaseUrl(provider) + "/v1/models";
    }

    /**
     * 添加额外请求头
     *
     * @param builder 请求客户端构建器
     * @param headersJson 请求头 JSON
     */
    private void addExtraHeaders(RestClient.Builder builder, String headersJson) {
        if (headersJson == null || headersJson.isBlank()) {
            return;
        }
        try {
            Map<String, String> headers = objectMapper.readValue(headersJson, new TypeReference<>() {});
            headers.forEach(builder::defaultHeader);
        } catch (Exception e) {
            log.warn("[DeepSeekHandler] 解析额外请求头失败: {}", e.getMessage());
        }
    }

    /**
     * 解析 DeepSeek API 地址
     *
     * @param provider 模型提供商配置
     * @return API 地址
     */
    private String resolveBaseUrl(ModelProvider provider) {
        if (provider.getBaseUrl() != null && !provider.getBaseUrl().isBlank()) {
            String url = provider.getBaseUrl().replaceAll("/+$", "");
            if (url.endsWith("/v1")) {
                url = url.substring(0, url.length() - 3);
            }
            return url;
        }
        return DEFAULT_BASE_URL;
    }

    /**
     * 转换为 double 类型
     *
     * @param val 原始值
     * @return double 值
     */
    private double toDouble(Object val) {
        return val instanceof Number ? ((Number) val).doubleValue() : Double.parseDouble(val.toString());
    }

    /**
     * 转换为 int 类型
     *
     * @param val 原始值
     * @return int 值
     */
    private int toInt(Object val) {
        return val instanceof Number ? ((Number) val).intValue() : Integer.parseInt(val.toString());
    }
}
