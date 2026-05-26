package com.lightbot.model;

import com.lightbot.constant.ConfigKeys;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 模型能力相关 ConfigField（多提供商复用，避免各 Handler 重复定义）
 */
public final class AgentCapabilityConfigFields {

    private AgentCapabilityConfigFields() {
    }

    /**
     * MiMo：多模态 + 联网 + 深度思考等完整能力
     */
    public static List<ConfigField> mimoFields() {
        List<ConfigField> fields = new ArrayList<>(visionAndAudioFields(
                "开启后对话页可上传图片/视频；需同时开启下方对应输入能力",
                "允许用户上传 JPEG/PNG/WebP 等图片，以 Base64 形式调用 MiMo 视觉理解",
                "允许用户上传 MP4/WebM 等短视频（≤20MB），由 MiMo 视频理解接口处理"));
        fields.addAll(webSearchFields());
        fields.add(ConfigField.builder()
                .key(ConfigKeys.Agent.ENABLE_TTS)
                .label("语音合成")
                .type("switch")
                .defaultValue(false)
                .hint("预留：将 AI 回复合成为语音（需配合 MiMo TTS 接口）")
                .build());
        fields.add(ConfigField.builder()
                .key(ConfigKeys.Agent.ENABLE_REASONING)
                .label("深度思考")
                .type("switch")
                .defaultValue(false)
                .hint("开启后输出 reasoning 思考过程（MiMo thinking 参数）")
                .build());
        return fields;
    }

    /**
     * OpenAI 兼容：视觉模型支持图片输入；语音输入走浏览器 STT
     */
    public static List<ConfigField> openAiFields() {
        return visionAndAudioFields(
                "开启后对话页可上传图片；请选用 gpt-4o / gpt-4o-mini / gpt-4-turbo 等视觉模型",
                "允许用户上传 JPEG/PNG/WebP/GIF，通过 OpenAI 视觉接口（image_url）发送",
                null);
    }

    /**
     * 通义千问 DashScope：VL 系列支持图片；语音输入走浏览器 STT
     */
    public static List<ConfigField> dashScopeFields() {
        return visionAndAudioFields(
                "开启后对话页可上传图片；建议选用 qwen-vl-max / qwen-vl-plus / qwen2-vl 等视觉模型",
                "允许用户上传 JPEG/PNG/WebP/GIF，通过通义千问多模态接口发送",
                null);
    }

    private static List<ConfigField> visionAndAudioFields(String multimodalHint, String imageHint, String videoHint) {
        List<ConfigField> fields = new ArrayList<>();
        fields.add(ConfigField.builder()
                .key(ConfigKeys.Agent.MULTIMODAL_ENABLED)
                .label("多模态")
                .type("switch")
                .defaultValue(false)
                .hint(multimodalHint)
                .build());
        fields.add(ConfigField.builder()
                .key(ConfigKeys.Agent.ENABLE_IMAGE_INPUT)
                .label("图像输入")
                .type("switch")
                .defaultValue(false)
                .hint(imageHint)
                .build());
        if (videoHint != null) {
            fields.add(ConfigField.builder()
                    .key(ConfigKeys.Agent.ENABLE_VIDEO_INPUT)
                    .label("视频输入")
                    .type("switch")
                    .defaultValue(false)
                    .hint(videoHint)
                    .build());
        }
        fields.add(ConfigField.builder()
                .key(ConfigKeys.Agent.ENABLE_AUDIO_INPUT)
                .label("音频输入")
                .type("switch")
                .defaultValue(false)
                .hint("对话页显示语音输入按钮，使用浏览器语音识别将语音转为文字后发送")
                .build());
        return fields;
    }

    private static List<ConfigField> webSearchFields() {
        return List.of(
                ConfigField.builder()
                        .key(ConfigKeys.Agent.ENABLE_WEB_SEARCH)
                        .label("联网搜索")
                        .type("switch")
                        .defaultValue(false)
                        .hint("调用 MiMo web_search 工具获取实时网络信息；开启后本轮不叠加 Agent 自定义工具")
                        .build(),
                ConfigField.builder()
                        .key(ConfigKeys.Agent.WEB_SEARCH_FORCE)
                        .label("强制联网")
                        .type("switch")
                        .defaultValue(false)
                        .hint("为 true 时尽量每次都执行联网搜索，而非由模型自行判断")
                        .build(),
                ConfigField.builder()
                        .key(ConfigKeys.Agent.WEB_SEARCH_MAX_KEYWORD)
                        .label("搜索关键词数")
                        .type("number")
                        .min(1.0).max(10.0).step(1.0)
                        .defaultValue(3)
                        .hint("联网搜索时使用的最大关键词数量（1-10）")
                        .build()
        );
    }
}
