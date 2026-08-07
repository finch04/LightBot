package com.lightbot.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * AI 辅助生成业务页 HTML
 *
 * @author finch
 * @since 2026-08-04
 */
@Data
public class BusinessPageHtmlGenerateDTO {

    /** 用户需求描述 */
    @NotBlank(message = "请填写生成需求")
    private String requirement;

    private String pageType;

    private String displayName;

    private String description;

    /** 当前 HTML；非空且 basedOnCurrent=true 时基于此修改 */
    private String currentHtml;

    /** 是否基于当前代码修改（默认 false=全新生成） */
    private Boolean basedOnCurrent;
}
