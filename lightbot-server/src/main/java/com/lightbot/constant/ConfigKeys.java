package com.lightbot.constant;

/**
 * JSONB config 字段的 key 常量
 * 避免在业务代码中使用魔法值
 *
 * @author finch
 * @since 2026-05-19
 */
public final class ConfigKeys {

    private ConfigKeys() {}

    /**
     * Agent.config JSONB 字段的 key
     */
    public static final class Agent {
        public static final String PROVIDER_ID = "providerId";
        public static final String MODEL_ID = "modelId";
        public static final String TEMPERATURE = "temperature";
        public static final String TOP_P = "topP";
        public static final String MAX_TOKENS = "maxTokens";
        // DashScope
        public static final String REPETITION_PENALTY = "repetitionPenalty";
        // OpenAI
        public static final String PRESENCE_PENALTY = "presencePenalty";
        public static final String FREQUENCY_PENALTY = "frequencyPenalty";
        // 上下文摘要
        public static final String ENABLE_SUMMARY = "enableSummary";
        public static final String SUMMARY_THRESHOLD_KB = "summaryThresholdKb";
        /** 提示词自定义变量列表 [{key,label,defaultValue,description}] */
        public static final String PROMPT_VARIABLES = "promptVariables";
        /** 用户输入敏感词（命中即拦截，不调用模型） */
        public static final String USER_SENSITIVE_FILTER_ENABLED = "userSensitiveFilterEnabled";
        public static final String USER_SENSITIVE_WORDS = "userSensitiveWords";
        /** AI 输出敏感词（替换或拦截） */
        public static final String SENSITIVE_FILTER_ENABLED = "sensitiveFilterEnabled";
        public static final String SENSITIVE_FILTER_STRATEGY = "sensitiveFilterStrategy";
        public static final String SENSITIVE_FILTER_REPLACE_TEXT = "sensitiveFilterReplaceText";
        public static final String SENSITIVE_WORDS = "sensitiveWords";
        /** 是否流式输出模型回复，默认 true */
        public static final String STREAM_OUTPUT = "streamOutput";
    }

    /**
     * Knowledge.config JSONB 字段的 key
     */
    public static final class Knowledge {
        public static final String RAG_TOP_K = "ragTopK";
        public static final String RAG_THRESHOLD = "ragThreshold";
    }

    /**
     * SystemConfig 系统配置的 key
     */
    public static final class System {
        /** 默认对话模型配置 */
        public static final String DEFAULT_CHAT_MODEL = "default_chat_model";
        /** 默认向量模型配置 */
        public static final String DEFAULT_EMBEDDING_MODEL = "default_embedding_model";
        /** 默认TTS模型配置 */
        public static final String DEFAULT_TTS_MODEL = "default_tts_model";
    }
}
