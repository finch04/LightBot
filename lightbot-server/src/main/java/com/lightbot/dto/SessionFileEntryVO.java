package com.lightbot.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 会话文件树条目（扁平结构，配合懒加载）。
 * <p>参考 Yuxi viewer filesystem entries。</p>
 *
 * @author finch
 * @since 2026-06-30
 */
@Data
@Schema(description = "会话文件树条目")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SessionFileEntryVO {

    @Schema(description = "名称")
    private String name;

    @Schema(description = "相对会话根的路径，如 outputs/files/report.pdf")
    private String path;

    @Schema(description = "是否为目录")
    private Boolean directory;

    @Schema(description = "文件大小（字节），目录为 null")
    private Long size;

    @Schema(description = "MIME 类型")
    private String mimeType;

    @Schema(description = "最后修改时间（ISO）")
    private String modifiedAt;

    @Schema(description = "附件来源：user_upload / ai_image / ai_sandbox / ai_deliver / unknown")
    private String source;

    @Schema(description = "MinIO objectKey，目录为 null")
    private String objectKey;

    @Schema(description = "预签名预览 URL（短期有效）")
    private String previewUrl;

    @Schema(description = "原始文件名（来自 attachments 索引，可选）")
    private String fileName;
}
