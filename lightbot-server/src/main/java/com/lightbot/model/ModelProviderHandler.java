package com.lightbot.model;

import com.lightbot.entity.ModelProvider;
import com.lightbot.enums.ModelProviderType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;

import java.util.List;
import java.util.Map;

/**
 * 模型提供商处理器接口
 * <p>每个提供商实现此接口，负责创建 ChatModel、构建 ChatOptions、定义配置字段</p>
 *
 * @author finch
 * @since 2026-05-19
 */
public interface ModelProviderHandler {

    /**
     * 获取处理器对应的提供商类型
     *
     * @return 提供商类型枚举
     */
    ModelProviderType getProviderType();

    /**
     * 根据提供商凭证创建 ChatModel 实例
     *
     * @param provider 提供商实体（含 apiKey / baseUrl 等凭证）
     * @return ChatModel 实例
     */
    ChatModel createChatModel(ModelProvider provider);

    /**
     * 根据 Agent config 构建 ChatOptions
     *
     * @param config Agent config JSONB 解析后的 Map
     * @return ChatOptions 实例
     */
    ChatOptions buildChatOptions(Map<String, Object> config);

    /**
     * 获取该提供商的配置字段定义，用于前端动态渲染表单
     *
     * @return 配置字段列表
     */
    List<ConfigField> getConfigFields();

    /**
     * 联网拉取提供商下可用的模型列表
     *
     * @param provider 提供商实体（含 apiKey / baseUrl）
     * @return 模型信息列表（含类型推断）
     */
    default List<FetchedModel> fetchModels(ModelProvider provider) {
        return List.of();
    }
}
