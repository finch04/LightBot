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
    }

    /**
     * Knowledge.config JSONB 字段的 key
     */
    public static final class Knowledge {
        public static final String RAG_TOP_K = "ragTopK";
        public static final String RAG_THRESHOLD = "ragThreshold";
    }
}
