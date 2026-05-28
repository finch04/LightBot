package com.lightbot.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.entity.ModelProvider;
import com.lightbot.enums.ModelProviderType;
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
 * OpenAI 模型处理器
 * <p>同时兼容 DeepSeek 等 OpenAI 兼容 API</p>
 *
 * @author finch
 * @since 2026-05-19
 */
@Slf4j
@Component
public class OpenAIModelHandler implements ModelProviderHandler {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public ModelProviderType getProviderType() {
        return ModelProviderType.OPENAI;
    }

    @Override
    public ChatModel createChatModel(ModelProvider provider) {
        OpenAiApi.Builder apiBuilder = OpenAiApi.builder()
                .apiKey(provider.getApiKey());
        if (provider.getBaseUrl() != null && !provider.getBaseUrl().isBlank()) {
            apiBuilder.baseUrl(provider.getBaseUrl());
        }
        OpenAiApi api = apiBuilder.build();
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder().streamUsage(true).build())
                .build();
    }

    @Override
    public ChatOptions buildChatOptions(ModelProvider provider, Map<String, Object> config) {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder();

        // modelId 未配置时使用默认模型
        String modelId = config.containsKey("modelId") ? config.get("modelId").toString() : getCheapestModel();
        builder.model(modelId);

        if (config.containsKey("temperature")) {
            builder.temperature(toDouble(config.get("temperature")));
        }
        if (config.containsKey("topP")) {
            builder.topP(toDouble(config.get("topP")));
        }
        if (config.containsKey("maxTokens")) {
            builder.maxTokens(toInt(config.get("maxTokens")));
        }
        if (config.containsKey("presencePenalty")) {
            builder.presencePenalty(toDouble(config.get("presencePenalty")));
        }
        if (config.containsKey("frequencyPenalty")) {
            builder.frequencyPenalty(toDouble(config.get("frequencyPenalty")));
        }

        OpenAiStreamUsageSupport.enableStreamUsage(builder);

        return builder.build();
    }

    @Override
    public String getCheapestModel() {
        return "gpt-4o-mini";
    }

    @Override
    public List<ConfigField> getConfigFields() {
        List<ConfigField> fields = new ArrayList<>();
        fields.add(ConfigField.builder()
                .key("modelId")
                .label("模型")
                .type("select")
                .options(List.of(
                        ConfigField.Option.builder().value("gpt-4o-mini").label("GPT-4o Mini（视觉）").build(),
                        ConfigField.Option.builder().value("gpt-4o").label("GPT-4o（视觉）").build(),
                        ConfigField.Option.builder().value("gpt-4-turbo").label("GPT-4 Turbo（视觉）").build(),
                        ConfigField.Option.builder().value("gpt-4-vision-preview").label("GPT-4 Vision Preview").build(),
                        ConfigField.Option.builder().value("gpt-4").label("GPT-4").build(),
                        ConfigField.Option.builder().value("gpt-3.5-turbo").label("GPT-3.5 Turbo").build()
                ))
                .defaultValue("gpt-4o-mini")
                .hint("多模态请选用 gpt-4o / gpt-4o-mini / gpt-4-turbo 等视觉模型")
                .build());
        fields.add(ConfigField.builder()
                .key("temperature")
                .label("温度")
                .type("slider")
                .min(0.0).max(2.0).step(0.1)
                .defaultValue(0.7)
                .hint("值越高回答越随机创造性，值越低回答越确定")
                .build());
        fields.add(ConfigField.builder()
                .key("topP")
                .label("核采样")
                .type("slider")
                .min(0.0).max(1.0).step(0.05)
                .defaultValue(1.0)
                .hint("控制词汇选择的多样性")
                .build());
        fields.add(ConfigField.builder()
                .key("maxTokens")
                .label("最大 Token")
                .type("number")
                .min(256.0).max(8192.0).step(256.0)
                .defaultValue(2048)
                .hint("单次回答的最大长度")
                .build());
        fields.add(ConfigField.builder()
                .key("presencePenalty")
                .label("存在惩罚")
                .type("slider")
                .min(-2.0).max(2.0).step(0.1)
                .defaultValue(0.0)
                .hint("正值降低重复话题的概率")
                .build());
        fields.add(ConfigField.builder()
                .key("frequencyPenalty")
                .label("频率惩罚")
                .type("slider")
                .min(-2.0).max(2.0).step(0.1)
                .defaultValue(0.0)
                .hint("正值降低重复用词的概率")
                .build());
        return fields;
    }

    @Override
    public List<ConfigField> getModelCapabilities() {
        return AgentCapabilityConfigFields.openAiFields();
    }

    @Override
    public List<FetchedModel> fetchModels(ModelProvider provider) {
        // 1. 优先使用 modelsEndpoint，否则使用默认地址
        String url = resolveModelsEndpoint(provider);

        try {
            // 2. 构建 RestClient，添加额外请求头
            RestClient.Builder clientBuilder = RestClient.builder()
                    .defaultHeader("Authorization", "Bearer " + provider.getApiKey());
            addExtraHeaders(clientBuilder, provider.getHeadersJson());
            RestClient restClient = clientBuilder.build();

            Map<String, Object> response = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(Map.class);

            // 3. 解析响应，提取模型ID
            List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
            if (data == null) return List.of();

            return data.stream()
                    .map(m -> FetchedModel.of(m.get("id").toString()))
                    .sorted(Comparator.comparing(FetchedModel::getModelId))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("[OpenAIHandler] 拉取模型列表失败: url={}, error={}", url, e.getMessage());
            throw new RuntimeException("拉取模型列表失败: " + e.getMessage());
        }
    }

    /**
     * 解析模型列表获取地址
     * <p>优先使用 modelsEndpoint，否则基于 baseUrl 构建默认地址</p>
     */
    private String resolveModelsEndpoint(ModelProvider provider) {
        if (provider.getModelsEndpoint() != null && !provider.getModelsEndpoint().isBlank()) {
            return provider.getModelsEndpoint();
        }
        return resolveBaseUrl(provider) + "/v1/models";
    }

    /**
     * 添加额外请求头
     */
    private void addExtraHeaders(RestClient.Builder builder, String headersJson) {
        if (headersJson == null || headersJson.isBlank()) {
            return;
        }
        try {
            Map<String, String> headers = OBJECT_MAPPER.readValue(headersJson, new TypeReference<>() {});
            headers.forEach(builder::defaultHeader);
        } catch (Exception e) {
            log.warn("[OpenAIHandler] 解析额外请求头失败: {}", e.getMessage());
        }
    }

    private String resolveBaseUrl(ModelProvider provider) {
        if (provider.getBaseUrl() != null && !provider.getBaseUrl().isBlank()) {
            String url = provider.getBaseUrl().replaceAll("/+$", "");
            // 如果已包含 /v1 结尾，去掉避免重复
            if (url.endsWith("/v1")) {
                url = url.substring(0, url.length() - 3);
            }
            return url;
        }
        return "https://api.openai.com";
    }

    private double toDouble(Object val) {
        return val instanceof Number ? ((Number) val).doubleValue() : Double.parseDouble(val.toString());
    }

    private int toInt(Object val) {
        return val instanceof Number ? ((Number) val).intValue() : Integer.parseInt(val.toString());
    }
}
