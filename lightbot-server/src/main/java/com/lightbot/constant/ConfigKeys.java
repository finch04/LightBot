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
        /** 上下文摘要提示词（含 {messages} 占位符） */
        public static final String SUMMARY_PROMPT = "summaryPrompt";
        /** 摘要后保留最近 N 条消息不被压缩 */
        public static final String SUMMARY_KEEP_MESSAGES = "summaryKeepMessages";
        /** 摘要时工具结果预览 Token 上限，超出截断 */
        public static final String SUMMARY_TOOL_RESULT_TOKEN_LIMIT = "summaryToolResultTokenLimit";
        /** 最大执行步数（工具调用递归深度上限） */
        public static final String MAX_EXECUTION_STEPS = "maxExecutionSteps";
        /** 模型调用失败重试次数 */
        public static final String MODEL_RETRY_TIMES = "modelRetryTimes";
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

        /** 多模态总开关（开启后对话页可上传媒体/语音输入，需模型支持） */
        public static final String MULTIMODAL_ENABLED = "multimodalEnabled";
        /** 图像输入 */
        public static final String ENABLE_IMAGE_INPUT = "enableImageInput";
        /** 视频输入 */
        public static final String ENABLE_VIDEO_INPUT = "enableVideoInput";
        /** 音频输入（浏览器语音转文字） */
        public static final String ENABLE_AUDIO_INPUT = "enableAudioInput";
        /** 文件读取（Tika 解析文档为文本注入对话，与多模态图片/视频独立） */
        public static final String ENABLE_FILE_READ = "enableFileRead";
        /** 联网搜索（MiMo web_search） */
        public static final String ENABLE_WEB_SEARCH = "enableWebSearch";
        /** 联网搜索：强制搜索 */
        public static final String WEB_SEARCH_FORCE = "webSearchForceSearch";
        /** 联网搜索：最大关键词数 */
        public static final String WEB_SEARCH_MAX_KEYWORD = "webSearchMaxKeyword";
        /** 语音合成（回复 TTS，预留） */
        public static final String ENABLE_TTS = "enableTts";
        /** 深度思考 */
        public static final String ENABLE_REASONING = "enableReasoning";
        /** 对话附件内容安全扫描（prompt 注入 + 敏感词） */
        public static final String ENABLE_CONTENT_SECURITY_SCAN = "enableContentSecurityScan";
    }

    /**
     * Knowledge.config JSONB 字段的 key
     */
    public static final class Knowledge {
        public static final String RAG_TOP_K = "ragTopK";
        public static final String RAG_THRESHOLD = "ragThreshold";
    }

    /**
     * Users.config JSONB 字段的 key
     */
    public static final class User {
        public static final String AVATAR_FRAME = "avatarFrame";
        public static final String LEVEL = "level";
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
        /** 默认重排模型配置 */
        public static final String DEFAULT_RERANK_MODEL = "default_rerank_model";
    }
}
