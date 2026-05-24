package com.lightbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 确认保存已预览的 URL 网页内容
 */
@Data
@Schema(description = "URL网页内容保存请求")
public class UrlSaveRequest {

    @NotBlank(message = "URL 不能为空")
    @Schema(description = "来源 URL")
    private String url;

    @NotBlank(message = "正文内容不能为空")
    @Schema(description = "正文内容")
    private String content;

    @Schema(description = "网页标题")
    private String title;
}
