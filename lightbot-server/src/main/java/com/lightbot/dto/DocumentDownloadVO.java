package com.lightbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档下载信息VO
 *
 * @author finch
 * @since 2026-05-20
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "文档下载信息")
public class DocumentDownloadVO {

    @Schema(description = "预签名下载URL")
    private String url;

    @Schema(description = "文件类型（扩展名）")
    private String fileType;

    @Schema(description = "文件名")
    private String fileName;

    @Schema(description = "文件MIME类型")
    private String contentType;
}
