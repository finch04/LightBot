package com.lightbot.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.constant.ConfigKeys;
import com.lightbot.entity.ModelProvider;
import com.lightbot.enums.ModelProviderType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 小米 MiMo 模型处理器
 * <p>基于 OpenAI 兼容协议，通过小米 MiMo 开放平台接入</p>
 * <p>同时发送 Authorization: Bearer 和 api-key 两种认证头，确保兼容性</p>
 *
 * @author finch
 * @since 2026-05-26
 */
@Slf4j
@Component
public class MimoModelHandler implements ModelProviderHandler {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 小米 MiMo 开放平台默认 base_url */
    private static final String DEFAULT_BASE_URL = "https://api.xiaomimimo.com/v1";

    @Override
    public ModelProviderType getProviderType() {
        return ModelProviderType.MIMO;
    }

    @Override
    public ChatModel createChatModel(ModelProvider provider) {
        String baseUrl = (provider.getBaseUrl() != null && !provider.getBaseUrl().isBlank())
                ? provider.getBaseUrl() : DEFAULT_BASE_URL;

        // MiMo 同时支持两种认证方式：
        // 1. Authorization: Bearer <key>（标准 OpenAI 格式）
        // 2. api-key: <key>（MiMo 原生格式）
        // 两种都发送，确保兼容性
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        headers.add("api-key", provider.getApiKey());

        OpenAiApi api = OpenAiApi.builder()
                .apiKey(provider.getApiKey())
                .baseUrl(baseUrl)
                .headers(headers)
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder().streamUsage(true).build())
                .build();
    }

    @Override
    public ChatOptions buildChatOptions(ModelProvider provider, Map<String, Object> config) {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder();

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

        if (Boolean.TRUE.equals(config.get(ConfigKeys.Agent.ENABLE_REASONING))) {
            builder.metadata(java.util.Map.of("mimoThinking", "enabled"));
        }

        OpenAiStreamUsageSupport.enableStreamUsage(builder);

        return builder.build();
    }

    @Override
    public String getCheapestModel() {
        return "mimo-v2.5";
    }

    @Override
    public List<ConfigField> getConfigFields() {
        List<ConfigField> fields = new ArrayList<>();
        fields.add(ConfigField.builder()
                .key("modelId")
                .label("模型")
                .type("select")
                .options(List.of(
                        ConfigField.Option.builder().value("mimo-v2.5-pro").label("MiMo v2.5 Pro").build(),
                        ConfigField.Option.builder().value("mimo-v2.5").label("MiMo v2.5").build(),
                        ConfigField.Option.builder().value("mimo-v2-omni").label("MiMo v2 Omni（多模态）").build(),
                        ConfigField.Option.builder().value("MiMo-7B-RL").label("MiMo-7B-RL").build(),
                        ConfigField.Option.builder().value("MiMo-7B").label("MiMo-7B").build()
                ))
                .defaultValue("mimo-v2.5-pro")
                .hint("多模态建议选用 mimo-v2.5 或 mimo-v2-omni")
                .build());
        fields.addAll(AgentCapabilityConfigFields.mimoFields());
        fields.add(ConfigField.builder()
                .key("temperature")
                .label("温度")
                .type("slider")
                .min(0.0).max(2.0).step(0.1)
                .defaultValue(1.0)
                .hint("值越高回答越随机创造性，值越低回答越确定")
                .build());
        fields.add(ConfigField.builder()
                .key("topP")
                .label("核采样")
                .type("slider")
                .min(0.0).max(1.0).step(0.05)
                .defaultValue(0.95)
                .hint("控制词汇选择的多样性")
                .build());
        fields.add(ConfigField.builder()
                .key("maxTokens")
                .label("最大 Token")
                .type("number")
                .min(256.0).max(32768.0).step(256.0)
                .defaultValue(4096)
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
            if (data == null) return List.of();

            return data.stream()
                    .map(m -> FetchedModel.of(m.get("id").toString()))
                    .sorted(Comparator.comparing(FetchedModel::getModelId))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("[MimoHandler] 拉取模型列表失败: url={}, error={}", url, e.getMessage());
            throw new RuntimeException("拉取模型列表失败: " + e.getMessage());
        }
    }

    private String resolveModelsEndpoint(ModelProvider provider) {
        if (provider.getModelsEndpoint() != null && !provider.getModelsEndpoint().isBlank()) {
            return provider.getModelsEndpoint();
        }
        return resolveBaseUrl(provider) + "/v1/models";
    }

    private void addExtraHeaders(RestClient.Builder builder, String headersJson) {
        if (headersJson == null || headersJson.isBlank()) {
            return;
        }
        try {
            Map<String, String> headers = OBJECT_MAPPER.readValue(headersJson, new TypeReference<>() {});
            headers.forEach(builder::defaultHeader);
        } catch (Exception e) {
            log.warn("[MimoHandler] 解析额外请求头失败: {}", e.getMessage());
        }
    }

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

    private double toDouble(Object val) {
        return val instanceof Number ? ((Number) val).doubleValue() : Double.parseDouble(val.toString());
    }

    private int toInt(Object val) {
        return val instanceof Number ? ((Number) val).intValue() : Integer.parseInt(val.toString());
    }
}
