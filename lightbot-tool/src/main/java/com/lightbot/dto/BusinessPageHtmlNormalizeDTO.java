package com.lightbot.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * AI 规范化业务页 HTML（对齐平台样式与交互规范）
 *
 * @author finch
 * @since 2026-08-04
 */
@Data
public class BusinessPageHtmlNormalizeDTO {

    /** 待规范化的完整 HTML */
    @NotBlank(message = "请先填写页面 HTML")
    private String currentHtml;

    private String pageType;

    private String displayName;

    private String description;
}
