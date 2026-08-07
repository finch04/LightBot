package com.lightbot.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 业务办理页创建/更新
 *
 * @author finch
 * @since 2026-08-04
 */
@Data
public class BusinessPageUpsertDTO {

    @NotBlank(message = "pageType 不能为空")
    private String pageType;

    @NotBlank(message = "展示名称不能为空")
    private String displayName;

    private String description;

    private String defaultTitle;

    /** 开发者直接登记的 H5 HTML（主路径，iframe srcdoc） */
    /** 内嵌 HTML（必填） */
    private String pageHtml;

    /** 已废弃：仅保留字段兼容，保存时忽略并清空 */
    private String pageUrl;

    private List<String> allowedModes;

    private List<String> allowedActions;

    private List<String> allowedPropKeys;

    private List<String> allowedOptionKeys;

    private Map<String, Object> defaultProps;

    /** 默认 options（身份透传 injectIdentityHeaders / contextHeaders 等） */
    private Map<String, Object> defaultOptions;

    /** @deprecated 已废弃，服务端忽略并清空 */
    @Deprecated
    private Map<String, Object> formSchema;

    private Boolean enabled;
}
