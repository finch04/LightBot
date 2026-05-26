package com.lightbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Agent 对话能力配置（从 agent.config 解析，供对话页控制上传/语音等 UI）
 */
@Data
@Schema(description = "Agent对话能力配置")
public class AgentChatCapabilitiesDTO {

    @Schema(description = "多模态总开关")
    private Boolean multimodalEnabled;

    @Schema(description = "允许图像输入")
    private Boolean enableImageInput;

    @Schema(description = "允许视频输入")
    private Boolean enableVideoInput;

    @Schema(description = "允许音频输入（浏览器语音转文字）")
    private Boolean enableAudioInput;

    @Schema(description = "联网搜索")
    private Boolean enableWebSearch;

    @Schema(description = "语音合成")
    private Boolean enableTts;

    @Schema(description = "深度思考")
    private Boolean enableReasoning;

    @Schema(description = "是否允许上传文件（图像或视频）")
    private Boolean allowFileUpload;

    @Schema(description = "允许的文件 MIME 类型列表")
    private java.util.List<String> allowedFileMimeTypes;

    @Schema(description = "图片最大字节数（上传校验用）")
    private Long maxImageBytes;

    @Schema(description = "视频最大字节数（上传校验用）")
    private Long maxVideoBytes;

    @Schema(description = "图片最大体积展示文案，如 4MB")
    private String maxImageSizeLabel;

    @Schema(description = "视频最大体积展示文案，如 20MB")
    private String maxVideoSizeLabel;
}
