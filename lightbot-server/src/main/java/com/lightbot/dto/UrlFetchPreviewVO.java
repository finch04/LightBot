package com.lightbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * URL 网页抓取预览 VO（不入库）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "URL网页抓取预览")
public class UrlFetchPreviewVO {

    @Schema(description = "来源 URL")
    private String url;

    @Schema(description = "网页标题")
    private String title;

    @Schema(description = "提取的正文（纯文本/Markdown 风格，用于入库）")
    private String content;

    @Schema(description = "正文 HTML 预览（已清洗，用于前端展示）")
    private String previewHtml;

    @Schema(description = "建议文件名")
    private String suggestedFileName;

    @Schema(description = "正文字符数")
    private Integer contentLength;

    @Schema(description = "摘要/描述")
    private String description;
}
