package com.lightbot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 对话附件（上传后引用）
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "对话附件")
public class ChatAttachmentDTO {

    @Schema(description = "附件ID")
    private String id;

    @Schema(description = "类型：image / video / document")
    private String type;

    @Schema(description = "MIME 类型")
    private String mimeType;

    @Schema(description = "MinIO 对象路径")
    private String objectKey;

    @Schema(description = "预览 URL（短期签名）")
    private String previewUrl;

    @Schema(description = "原始文件名")
    private String fileName;

    @Schema(description = "文档类附件：Tika 解析后的文本（仅 type=document）")
    private String parsedText;

    @Schema(description = "解析文本是否因超长被截断")
    private Boolean parsedTextTruncated;
}
