package com.lightbot.model;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.lightbot.entity.ModelProvider;
import com.lightbot.enums.ModelProviderType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
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
 *
 * @author finch
 * @since 2026-05-19
 */
@Slf4j
@Component
public class DashScopeModelHandler implements ModelProviderHandler {

    @Override
    public ModelProviderType getProviderType() {
        return ModelProviderType.DASHSCOPE;
    }

    @Override
    public ChatModel createChatModel(ModelProvider provider) {
        DashScopeApi api = DashScopeApi.builder()
                .apiKey(provider.getApiKey())
                .build();
        return DashScopeChatModel.builder()
                .dashScopeApi(api)
                .build();
    }

    @Override
    public ChatOptions buildChatOptions(Map<String, Object> config) {
        DashScopeChatOptions.DashScopeChatOptionsBuilder builder = DashScopeChatOptions.builder();

        if (config.containsKey("modelId")) {
            builder.withModel(config.get("modelId").toString());
        }
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

    @Override
    public List<ConfigField> getConfigFields() {
        return List.of(
                ConfigField.builder()
                        .key("modelId")
                        .label("模型")
                        .type("select")
                        .options(List.of(
                                ConfigField.Option.builder().value("qwen-turbo").label("通义千问 Turbo").build(),
                                ConfigField.Option.builder().value("qwen-plus").label("通义千问 Plus").build(),
                                ConfigField.Option.builder().value("qwen-max").label("通义千问 Max").build()
                        ))
                        .defaultValue("qwen-plus")
                        .build(),
                ConfigField.builder()
                        .key("temperature")
                        .label("温度 (Temperature)")
                        .type("slider")
                        .min(0.0).max(2.0).step(0.1)
                        .defaultValue(0.7)
                        .hint("值越高回答越随机创造性，值越低回答越确定")
                        .build(),
                ConfigField.builder()
                        .key("topP")
                        .label("核采样 (Top P)")
                        .type("slider")
                        .min(0.0).max(1.0).step(0.05)
                        .defaultValue(0.9)
                        .hint("控制词汇选择的多样性，建议与温度二选一调整")
                        .build(),
                ConfigField.builder()
                        .key("maxTokens")
                        .label("最大 Token")
                        .type("number")
                        .min(256.0).max(8192.0).step(256.0)
                        .defaultValue(2048)
                        .hint("单次回答的最大长度")
                        .build(),
                ConfigField.builder()
                        .key("repetitionPenalty")
                        .label("重复惩罚 (Repetition Penalty)")
                        .type("slider")
                        .min(0.0).max(2.0).step(0.1)
                        .defaultValue(1.0)
                        .hint("值越高越不容易重复")
                        .build()
        );
    }

    @Override
    public List<FetchedModel> fetchModels(ModelProvider provider) {
        // 1. 调用 DashScope compatible-mode API 拉取在线模型
        String url = "https://dashscope.aliyuncs.com/compatible-mode/v1/models";
        List<FetchedModel> result = new ArrayList<>();

        try {
            RestClient restClient = RestClient.builder()
                    .defaultHeader("Authorization", "Bearer " + provider.getApiKey())
                    .build();

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
            log.warn("[DashScopeHandler] compatible-mode 拉取失败: error={}", e.getMessage());
        }

        // 2. 补充 DashScope 常用模型（compatible-mode 可能不返回 embedding/rerank 等）
        addWellKnownModels(result);

        return result.stream()
                .sorted(Comparator.comparing(FetchedModel::getModelId))
                .collect(Collectors.toList());
    }

    /**
     * 补充 DashScope 常用模型（API 可能不返回 embedding/rerank 等类型）
     */
    private void addWellKnownModels(List<FetchedModel> list) {
        Set<String> existing = list.stream()
                .map(FetchedModel::getModelId).collect(Collectors.toSet());

        // 对话模型
        for (String id : List.of("qwen-turbo", "qwen-plus", "qwen-max", "qwen-long",
                "qwen-turbo-latest", "qwen-plus-latest", "qwen-max-latest")) {
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
