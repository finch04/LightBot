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
 * Ollama 模型处理器
 * <p>通过 Ollama OpenAI 兼容接口接入本地模型，支持配置 localhost 地址</p>
 *
 * @author finch
 * @since 2026-06-27
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OllamaModelHandler implements ModelProviderHandler {

    private final ObjectMapper objectMapper;

    /** Ollama 默认本地服务地址 */
    private static final String DEFAULT_BASE_URL = "http://localhost:11434";

    /** Ollama OpenAI 兼容接口占位密钥 */
    private static final String DEFAULT_API_KEY = "ollama";

    /**
     * 获取模型提供商类型
     *
     * @return Ollama 提供商类型
     */
    @Override
    public ModelProviderType getProviderType() {
        return ModelProviderType.OLLAMA;
    }

    /**
     * 创建 Ollama ChatModel
     *
     * @param provider 模型提供商配置
     * @return ChatModel 实例
     */
    @Override
    public ChatModel createChatModel(ModelProvider provider) {
        return createChatModel(provider, getCheapestModel());
    }

    /**
     * 创建指定默认模型的 Ollama ChatModel
     *
     * @param provider 模型提供商配置
     * @param defaultModelId 默认模型ID
     * @return ChatModel 实例
     */
    @Override
    public ChatModel createChatModel(ModelProvider provider, String defaultModelId) {
        OpenAiApi api = OpenAiApi.builder()
                .apiKey(resolveApiKey(provider))
                .baseUrl(resolveBaseUrl(provider))
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder().model(defaultModelId).build())
                .build();
    }

    /**
     * 构建 Ollama 调用参数
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

        return builder.build();
    }

    /**
     * 获取 Ollama 默认低成本模型
     *
     * @return 默认模型ID
     */
    @Override
    public String getCheapestModel() {
        return "deepseek-r1:1.5b";
    }

    /**
     * 获取 Ollama 参数配置字段
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
                        ConfigField.Option.builder().value("deepseek-r1:1.5b").label("Qwen2.5 7B").build(),
                        ConfigField.Option.builder().value("qwen2.5:14b").label("Qwen2.5 14B").build(),
                        ConfigField.Option.builder().value("llama3.1:8b").label("Llama 3.1 8B").build(),
                        ConfigField.Option.builder().value("deepseek-r1:7b").label("DeepSeek R1 7B").build(),
                        ConfigField.Option.builder().value("gemma3:4b").label("Gemma 3 4B").build()
                ))
                .defaultValue("deepseek-r1:1.5b")
                .hint("需先在本地执行 ollama pull 对应模型；也可在模型管理中手动添加其他模型ID")
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
                .defaultValue(0.9)
                .hint("控制词汇选择的多样性")
                .build());
        fields.add(ConfigField.builder()
                .key(ConfigKeys.Agent.MAX_TOKENS)
                .label("最大 Token")
                .type("number")
                .min(128.0).max(8192.0).step(128.0)
                .defaultValue(2048)
                .hint("单次回答的最大长度，本地模型实际输出还受模型上下文窗口限制")
                .build());
        return fields;
    }

    /**
     * 获取 Ollama 模型能力字段
     *
     * @return 能力字段列表
     */
    @Override
    public List<ConfigField> getModelCapabilities() {
        return List.of(ConfigField.builder()
                .key(ConfigKeys.Agent.ENABLE_AUDIO_INPUT)
                .label("音频输入")
                .type("switch")
                .defaultValue(false)
                .hint("对话页显示语音输入按钮，使用浏览器语音识别将语音转为文字后发送")
                .build());
    }

    /**
     * 拉取本地 Ollama 模型列表
     *
     * @param provider 模型提供商配置
     * @return 模型列表
     */
    @Override
    public List<FetchedModel> fetchModels(ModelProvider provider) {
        String url = resolveModelsEndpoint(provider);

        try {
            RestClient.Builder clientBuilder = RestClient.builder();
            addExtraHeaders(clientBuilder, provider.getHeadersJson());
            RestClient restClient = clientBuilder.build();

            Map<String, Object> response = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(Map.class);

            List<Map<String, Object>> models = (List<Map<String, Object>>) response.get("models");
            if (models == null) {
                return List.of();
            }
            return models.stream()
                    .map(m -> FetchedModel.of(m.get("name").toString()))
                    .sorted(Comparator.comparing(FetchedModel::getModelId))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("[OllamaHandler] 拉取模型列表失败: url={}, error={}", url, e.getMessage());
            throw new RuntimeException("拉取模型列表失败，请确认 Ollama 服务已启动且地址可访问: " + e.getMessage());
        }
    }

    /**
     * 解析模型列表获取地址
     *
     * @param provider 提供商实体
     * @return Ollama 模型列表地址
     */
    private String resolveModelsEndpoint(ModelProvider provider) {
        if (provider.getModelsEndpoint() != null && !provider.getModelsEndpoint().isBlank()) {
            return normalizeBaseUrl(provider.getModelsEndpoint());
        }
        return resolveBaseUrl(provider) + "/api/tags";
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
            log.warn("[OllamaHandler] 解析额外请求头失败: {}", e.getMessage());
        }
    }

    /**
     * 解析 Ollama API Key
     *
     * @param provider 模型提供商配置
     * @return API Key
     */
    private String resolveApiKey(ModelProvider provider) {
        if (provider.getApiKey() != null && !provider.getApiKey().isBlank()) {
            return provider.getApiKey();
        }
        return DEFAULT_API_KEY;
    }

    /**
     * 解析 Ollama API 地址
     *
     * @param provider 模型提供商配置
     * @return API 地址
     */
    private String resolveBaseUrl(ModelProvider provider) {
        if (provider.getBaseUrl() != null && !provider.getBaseUrl().isBlank()) {
            String url = normalizeBaseUrl(provider.getBaseUrl()).replaceAll("/+$", "");
            if (url.endsWith("/v1")) {
                url = url.substring(0, url.length() - 3);
            }
            return url;
        }
        return DEFAULT_BASE_URL;
    }

    /**
     * 规范化 URL 地址
     *
     * @param url 原始地址
     * @return 带协议的地址
     */
    private String normalizeBaseUrl(String url) {
        String value = url.trim();
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            return "http://" + value;
        }
        return value;
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
