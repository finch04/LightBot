package com.lightbot.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 会话文件内容响应。
 * <ul>
 *   <li>文本/Markdown：返回 {@code content}，前端直接渲染</li>
 *   <li>图片/PDF/二进制：返回 {@code previewUrl}（预签名）+ {@code previewType}</li>
 * </ul>
 *
 * @author finch
 * @since 2026-06-30
 */
@Data
@Schema(description = "会话文件内容响应")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SessionFileContentVO {

    @Schema(description = "相对会话根路径")
    private String path;

    @Schema(description = "MinIO objectKey")
    private String objectKey;

    @Schema(description = "MIME 类型")
    private String mimeType;

    @Schema(description = "文件大小（字节）")
    private Long size;

    @Schema(description = "预览类型：text / markdown / image / pdf / unsupported")
    private String previewType;

    @Schema(description = "是否支持预览")
    private boolean supported;

    @Schema(description = "文本内容（previewType 为 text/markdown 时返回）")
    private String content;

    @Schema(description = "预签名 URL（previewType 为 image/pdf 时返回）")
    private String previewUrl;

    @Schema(description = "错误或提示信息")
    private String message;
}
