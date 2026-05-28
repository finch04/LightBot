package com.lightbot.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
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

    @Schema(description = "文件读取（Tika 解析文档为文本）")
    private Boolean enableFileRead;

    @Schema(description = "是否允许上传附件（文档和/或多模态媒体）")
    private Boolean allowFileUpload;

    @Schema(description = "是否允许上传文档（文件读取）")
    private Boolean allowDocumentUpload;

    @Schema(description = "是否允许上传图片/视频（多模态）")
    private Boolean allowMediaUpload;

    @Schema(description = "允许的文件 MIME 类型列表")
    private java.util.List<String> allowedFileMimeTypes;

    @Schema(description = "图片最大字节数（上传校验用）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long maxImageBytes;

    @Schema(description = "视频最大字节数（上传校验用）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long maxVideoBytes;

    @Schema(description = "图片最大体积展示文案，如 4MB")
    private String maxImageSizeLabel;

    @Schema(description = "视频最大体积展示文案，如 20MB")
    private String maxVideoSizeLabel;

    @Schema(description = "单条消息最多附件数")
    private Integer maxAttachmentsPerMessage;

    @Schema(description = "文档最大字节数")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long maxDocumentBytes;

    @Schema(description = "文档最大体积展示文案")
    private String maxDocumentSizeLabel;

    @Schema(description = "允许的文档扩展名（含点，如 .pdf）")
    private java.util.List<String> allowedDocumentExtensions;
}
