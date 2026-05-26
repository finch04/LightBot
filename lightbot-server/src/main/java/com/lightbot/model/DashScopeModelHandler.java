package com.lightbot.model;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
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
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 通义千问（DashScope）模型处理器
 * <p>支持两种调用模式：</p>
 * <ul>
 *   <li>原生模式（DashScope SDK）：baseUrl 不配置或为空</li>
 *   <li>兼容模式（OpenAI SDK）：baseUrl 包含 "compatible-mode"</li>
 * </ul>
 *
 * @author finch
 * @since 2026-05-19
 */
@Slf4j
@Component
public class DashScopeModelHandler implements ModelProviderHandler {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 兼容模式标识：baseUrl 中包含此字符串时使用 OpenAI SDK */
    private static final String COMPATIBLE_MODE_MARKER = "compatible-mode";

    /** 兼容模式默认 baseUrl */
    private static final String COMPATIBLE_MODE_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1";

    @Override
    public ModelProviderType getProviderType() {
        return ModelProviderType.DASHSCOPE;
    }

    @Override
    public ChatModel createChatModel(ModelProvider provider) {
        String baseUrl = provider.getBaseUrl();

        // 判断是否使用兼容模式
        boolean useCompatibleMode = baseUrl != null && baseUrl.contains(COMPATIBLE_MODE_MARKER);

        if (useCompatibleMode) {
            // 使用 OpenAI SDK（兼容模式），支持百炼平台新模型
            log.info("[DashScopeHandler] 使用 OpenAI 兼容模式: baseUrl={}", baseUrl);
            OpenAiApi openAiApi = OpenAiApi.builder()
                    .baseUrl(baseUrl)
                    .apiKey(provider.getApiKey())
                    .build();
            return OpenAiChatModel.builder()
                    .openAiApi(openAiApi)
                    .defaultOptions(OpenAiChatOptions.builder().streamUsage(true).build())
                    .build();
        } else {
            // 使用 DashScope SDK（原生模式）
            log.info("[DashScopeHandler] 使用 DashScope 原生模式");
            DashScopeApi.Builder apiBuilder = DashScopeApi.builder()
                    .apiKey(provider.getApiKey());
            if (baseUrl != null && !baseUrl.isBlank()) {
                apiBuilder.baseUrl(baseUrl);
            }
            DashScopeApi api = apiBuilder.build();
            return DashScopeChatModel.builder()
                    .dashScopeApi(api)
                    .build();
        }
    }

    @Override
    public ChatOptions buildChatOptions(ModelProvider provider, Map<String, Object> config) {
        String modelId = config.containsKey("modelId") ? config.get("modelId").toString() : getCheapestModel();

        // 判断是否使用兼容模式（通过 provider.baseUrl 判断）
        String baseUrl = provider.getBaseUrl();
        boolean useCompatibleMode = baseUrl != null && baseUrl.contains(COMPATIBLE_MODE_MARKER);

        if (useCompatibleMode) {
            // 使用 OpenAI ChatOptions
            OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder();
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
            OpenAiStreamUsageSupport.enableStreamUsage(builder);
            return builder.build();
        } else {
            // 使用 DashScope ChatOptions
            DashScopeChatOptions.DashScopeChatOptionsBuilder builder = DashScopeChatOptions.builder();
            builder.withModel(modelId);

            if (config.containsKey("temperature")) {
                builder.withTemperature(toDouble(config.get("temperature")));
            }
            if (config.containsKey("topP")) {
                builder.withTopP(toDouble(config.get("topP")));
            }
            if (config.containsKey("maxTokens")) {
                builder.withMaxToken(toInt(config.get("maxTokens")));
            }
            if (config.containsKey("repetitionPenalty")) {
                builder.withRepetitionPenalty(toDouble(config.get("repetitionPenalty")));
            }
            return builder.build();
        }
    }

    @Override
    public String getCheapestModel() {
        return "qwen-turbo";
    }

    @Override
    public List<ConfigField> getConfigFields() {
        List<ConfigField> fields = new ArrayList<>();
        fields.add(ConfigField.builder()
                .key("modelId")
                .label("模型")
                .type("select")
                .options(List.of(
                        ConfigField.Option.builder().value("qwen-plus").label("通义千问 Plus").build(),
                        ConfigField.Option.builder().value("qwen-max").label("通义千问 Max").build(),
                        ConfigField.Option.builder().value("qwen-turbo").label("通义千问 Turbo").build(),
                        ConfigField.Option.builder().value("qwen-vl-max").label("通义千问 VL Max（视觉）").build(),
                        ConfigField.Option.builder().value("qwen-vl-plus").label("通义千问 VL Plus（视觉）").build(),
                        ConfigField.Option.builder().value("qwen2-vl-72b-instruct").label("Qwen2-VL 72B（视觉）").build(),
                        ConfigField.Option.builder().value("qwen2.5-vl-72b-instruct").label("Qwen2.5-VL 72B（视觉）").build()
                ))
                .defaultValue("qwen-plus")
                .hint("多模态请选用 qwen-vl-max / qwen-vl-plus / qwen2-vl 等视觉模型")
                .build());
        fields.addAll(AgentCapabilityConfigFields.dashScopeFields());
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
                .defaultValue(0.9)
                .hint("控制词汇选择的多样性，建议与温度二选一调整")
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
                .key("repetitionPenalty")
                .label("重复惩罚")
                .type("slider")
                .min(0.0).max(2.0).step(0.1)
                .defaultValue(1.0)
                .hint("值越高越不容易重复")
                .build());
        return fields;
    }

    @Override
    public List<FetchedModel> fetchModels(ModelProvider provider) {
        // 1. 优先使用 modelsEndpoint，否则使用默认地址
        String url = resolveModelsEndpoint(provider);
        List<FetchedModel> result = new ArrayList<>();

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

            List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
            if (data != null) {
                result.addAll(data.stream()
                        .map(m -> FetchedModel.of(m.get("id").toString()))
                        .collect(Collectors.toList()));
            }
        } catch (Exception e) {
            log.warn("[DashScopeHandler] 模型列表拉取失败: url={}, error={}", url, e.getMessage());
        }

        // 3. 补充 DashScope 常用模型（API 可能不返回 embedding/rerank 等类型）
        addWellKnownModels(result);

        return result.stream()
                .sorted(Comparator.comparing(FetchedModel::getModelId))
                .collect(Collectors.toList());
    }

    /**
     * 解析模型列表获取地址
     * <p>优先使用 modelsEndpoint，否则使用默认地址</p>
     */
    private String resolveModelsEndpoint(ModelProvider provider) {
        if (provider.getModelsEndpoint() != null && !provider.getModelsEndpoint().isBlank()) {
            return provider.getModelsEndpoint();
        }
        return "https://dashscope.aliyuncs.com/compatible-mode/v1/models";
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
            log.warn("[DashScopeHandler] 解析额外请求头失败: {}", e.getMessage());
        }
    }

    /**
     * 补充 DashScope 常用模型（API 可能不返回 embedding/rerank 等类型）
     */
    private void addWellKnownModels(List<FetchedModel> list) {
        Set<String> existing = list.stream()
                .map(FetchedModel::getModelId).collect(Collectors.toSet());

        // 对话模型
        for (String id : List.of("qwen-turbo", "qwen-plus", "qwen-max", "qwen-long",
                "qwen-turbo-latest", "qwen-plus-latest", "qwen-max-latest",
                "qwen-vl-max", "qwen-vl-plus", "qwen2-vl-72b-instruct", "qwen2.5-vl-72b-instruct")) {
            if (!existing.contains(id)) list.add(FetchedModel.of(id));
        }
        // 嵌入模型
        for (String id : List.of("text-embedding-v1", "text-embedding-v2", "text-embedding-v3",
                "text-embedding-async-v1", "text-embedding-async-v2")) {
            if (!existing.contains(id)) list.add(FetchedModel.of(id));
        }
        // 重排模型
        for (String id : List.of("gte-rerank", "gte-rerank-v2")) {
            if (!existing.contains(id)) list.add(FetchedModel.of(id));
        }
    }

    private double toDouble(Object val) {
        return val instanceof Number ? ((Number) val).doubleValue() : Double.parseDouble(val.toString());
    }

    private int toInt(Object val) {
        return val instanceof Number ? ((Number) val).intValue() : Integer.parseInt(val.toString());
    }
}
