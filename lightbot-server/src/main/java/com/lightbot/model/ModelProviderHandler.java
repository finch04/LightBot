package com.lightbot.model;

import com.lightbot.entity.ModelProvider;
import com.lightbot.enums.ModelProviderType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

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
     * 创建 ChatModel 并设置默认 modelId（避免 unknown-model）
     *
     * @param provider 提供商实体
     * @param defaultModelId 默认模型ID
     * @return ChatModel 实例
     */
    default ChatModel createChatModel(ModelProvider provider, String defaultModelId) {
        return createChatModel(provider);
    }

    /**
     * 根据 Agent config 构建 ChatOptions
     *
     * @param provider 提供商实体（含 baseUrl 用于判断模式）
     * @param config   Agent config JSONB 解析后的 Map
     * @return ChatOptions 实例
     */
    ChatOptions buildChatOptions(ModelProvider provider, Map<String, Object> config);

    /**
     * 获取该提供商的模型调参字段定义（temperature、topP 等），用于前端动态渲染表单
     * <p>不包含模型能力字段（多模态、联网搜索等），能力字段通过 {@link #getModelCapabilities()} 获取</p>
     *
     * @return 配置字段列表
     */
    List<ConfigField> getConfigFields();

    /**
     * 获取该提供商的模型能力字段定义（多模态、联网搜索、深度思考等）
     * <p>与 {@link #getConfigFields()} 分离，能力字段由提供商决定，调参字段通用</p>
     *
     * @return 能力字段列表
     */
    List<ConfigField> getModelCapabilities();

    /**
     * 联网拉取提供商下可用的模型列表
     *
     * @param provider 提供商实体（含 apiKey / baseUrl）
     * @return 模型信息列表（含类型推断）
     */
    default List<FetchedModel> fetchModels(ModelProvider provider) {
        return List.of();
    }

    /**
     * 获取最便宜的模型ID，用于连通性检查
     *
     * @return 模型ID
     */
    String getCheapestModel();

    /**
     * 当前 Provider + 模型是否支持向 API 传递 tools 并执行标准 tool_calls。
     * <p>默认 true；Ollama 等按模型 capabilities 判断。</p>
     */
    default boolean supportsApiToolCalling(ModelProvider provider, Map<String, Object> config) {
        return true;
    }

    /**
     * 按提供商/模型能力调整 ToolCallingChatOptions，使其符合底层 API 约束。
     * <p>默认原样返回；各 Handler 可覆写（如 Ollama 剔除 tools 参数避免 400）。</p>
     *
     * @param provider 提供商实体
     * @param config   Agent config
     * @param options  已组装的工具调用选项
     * @return 可安全发往模型 API 的选项
     */
    default ToolCallingChatOptions adaptToolCallingOptions(ModelProvider provider,
                                                           Map<String, Object> config,
                                                           ToolCallingChatOptions options) {
        return options;
    }
}
