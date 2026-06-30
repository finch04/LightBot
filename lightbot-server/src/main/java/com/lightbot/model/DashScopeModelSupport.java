package com.lightbot.model;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Map;

/**
 * DashScope 模型路由辅助：识别需走 multimodal-generation 端点的模型，并构建原生 ChatOptions。
 * <p>Qwen3.5 / Qwen3.6 及 VL 系列在 DashScope 原生 SDK 下必须使用 MultiModalConversation 接口，
 * 否则会报 {@code url error, please check url}。</p>
 */
public final class DashScopeModelSupport {

    private static final String COMPATIBLE_MODE_MARKER = "compatible-mode";

    private DashScopeModelSupport() {
    }

    /**
     * 是否使用 OpenAI 兼容模式（走 compatible-mode/v1，无需 multiModel 路由）
     *
     * @param baseUrl 提供商 baseUrl
     * @return true 表示兼容模式
     */
    public static boolean isCompatibleMode(String baseUrl) {
        return baseUrl != null && baseUrl.contains(COMPATIBLE_MODE_MARKER);
    }

    /**
     * 模型是否需走 DashScope multimodal-generation 端点（含纯文本场景的 Qwen3.5/3.6）
     *
     * @param modelId 模型 ID
     * @return true 需设置 multiModel=true
     */
    public static boolean requiresMultimodalApi(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return false;
        }
        String m = modelId.toLowerCase();
        // Qwen3.5 / Qwen3.6 全系列（含 flash / plus 快照）均走 MultiModalConversation
        if (m.startsWith("qwen3.5") || m.startsWith("qwen3.6")) {
            return true;
        }
        // 视觉 / 多模态 VL 系列
        if (m.startsWith("qwen-vl") || m.startsWith("qwen2-vl") || m.startsWith("qwen2.5-vl")
                || m.startsWith("qwen3-vl") || m.startsWith("qvq-")) {
            return true;
        }
        if (m.contains("-vl-") || m.endsWith("-vl")) {
            return true;
        }
        return false;
    }

    /**
     * 为 DashScope 原生 ChatOptions 设置 multimodal 路由与流式 incrementalOutput
     *
     * @param builder DashScope 选项构建器
     * @param modelId 模型 ID
     */
    public static void applyMultimodalRouting(DashScopeChatOptions.DashScopeChatOptionsBuilder builder, String modelId) {
        if (!requiresMultimodalApi(modelId)) {
            return;
        }
        builder.withMultiModel(true);
        // Qwen3.5/3.6 流式输出要求 incremental_output=true
        builder.withIncrementalOutput(true);
    }

    /**
     * 构建 DashScope 原生 ChatOptions（含工具回调），供对话 / SubAgent 流式调用
     *
     * @param modelId       模型 ID
     * @param configMap     Agent 模型参数
     * @param toolCallbacks 工具回调
     * @param toolContext   工具上下文
     * @return ToolCallingChatOptions（实际为 DashScopeChatOptions）
     */
    public static ToolCallingChatOptions buildNativeChatOptions(String modelId,
                                                                 Map<String, Object> configMap,
                                                                 List<ToolCallback> toolCallbacks,
                                                                 Map<String, Object> toolContext) {
        DashScopeChatOptions.DashScopeChatOptionsBuilder builder = DashScopeChatOptions.builder();
        if (modelId != null && !modelId.isBlank()) {
            builder.withModel(modelId);
        }
        applyConfigParams(builder, configMap);
        applyMultimodalRouting(builder, modelId);
        if (toolCallbacks != null && !toolCallbacks.isEmpty()) {
            builder.toolCallbacks(toolCallbacks);
            if (toolContext != null && !toolContext.isEmpty()) {
                builder.toolContext(toolContext);
            }
        }
        ToolCallingChatOptions options = builder.build();
        options.setInternalToolExecutionEnabled(false);
        return options;
    }

    private static void applyConfigParams(DashScopeChatOptions.DashScopeChatOptionsBuilder builder,
                                          Map<String, Object> configMap) {
        if (configMap == null) {
            return;
        }
        if (configMap.containsKey("temperature")) {
            builder.withTemperature(toDouble(configMap.get("temperature")));
        }
        if (configMap.containsKey("topP")) {
            builder.withTopP(toDouble(configMap.get("topP")));
        }
        if (configMap.containsKey("maxTokens")) {
            builder.withMaxToken(toInt(configMap.get("maxTokens")));
        }
        if (configMap.containsKey("repetitionPenalty")) {
            builder.withRepetitionPenalty(toDouble(configMap.get("repetitionPenalty")));
        }
    }

    private static double toDouble(Object val) {
        return val instanceof Number ? ((Number) val).doubleValue() : Double.parseDouble(val.toString());
    }

    private static int toInt(Object val) {
        return val instanceof Number ? ((Number) val).intValue() : Integer.parseInt(val.toString());
    }
}
