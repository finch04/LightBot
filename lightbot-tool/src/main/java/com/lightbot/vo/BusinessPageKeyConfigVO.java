package com.lightbot.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.List;

/**
 * API Key 业务页白名单视图
 *
 * @author finch
 * @since 2026-08-04
 */
@Data
public class BusinessPageKeyConfigVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long apiKeyId;

    private Boolean inherit;

    private List<String> allowedPageTypes;

    /** 生效后的 pageType 列表 */
    private List<String> effectivePageTypes;
}
