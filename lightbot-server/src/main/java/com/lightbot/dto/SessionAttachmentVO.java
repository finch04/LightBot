package com.lightbot.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 会话附件视图对象（会话级附件列表返回）
 *
 * @author finch
 * @since 2026-06-29
 */
@Data
@Schema(description = "会话附件")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SessionAttachmentVO {

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

    @Schema(description = "来源：user_upload / ai_image / ai_sandbox / ai_deliver")
    private String source;

    @Schema(description = "沙盒相对路径（outputs 或工作区相对路径）")
    private String relativePath;

    @Schema(description = "产生该文件的工具名")
    private String toolName;

    @Schema(description = "关联消息 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long messageId;

    @Schema(description = "加入会话时间")
    private String createdAt;
}
