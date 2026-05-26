package com.lightbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 对话附件（上传后引用）
 */
@Data
@Schema(description = "对话附件")
public class ChatAttachmentDTO {

    @Schema(description = "附件ID")
    private String id;

    @Schema(description = "类型：image / video / audio")
    private String type;

    @Schema(description = "MIME 类型")
    private String mimeType;

    @Schema(description = "MinIO 对象路径")
    private String objectKey;

    @Schema(description = "预览 URL（短期签名）")
    private String previewUrl;

    @Schema(description = "原始文件名")
    private String fileName;
}
