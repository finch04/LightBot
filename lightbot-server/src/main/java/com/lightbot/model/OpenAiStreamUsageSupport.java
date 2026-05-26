package com.lightbot.model;

import org.springframework.ai.openai.OpenAiChatOptions;

/**
 * OpenAI 兼容流式调用：开启 usage 统计（stream_options.include_usage）
 * <p>MiMo / DeepSeek / 百炼兼容模式等需在流式请求中显式开启，否则 Trace 无法拿到 Token</p>
 */
public final class OpenAiStreamUsageSupport {

    private OpenAiStreamUsageSupport() {
    }

    /**
     * 为 OpenAiChatOptions 开启流式 Token 统计
     *
     * @param builder OpenAi 选项构建器
     */
    public static void enableStreamUsage(OpenAiChatOptions.Builder builder) {
        builder.streamUsage(true);
    }
}
