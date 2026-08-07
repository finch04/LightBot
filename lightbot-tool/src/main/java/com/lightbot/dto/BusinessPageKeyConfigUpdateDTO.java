package com.lightbot.dto;

import lombok.Data;

import java.util.List;

/**
 * API Key 业务页白名单更新
 *
 * @author finch
 * @since 2026-08-04
 */
@Data
public class BusinessPageKeyConfigUpdateDTO {

    /** true/缺省：跟随全部已启用页；false：使用 allowedPageTypes */
    private Boolean inherit;

    /** inherit=false 时生效；空列表表示禁止全部 */
    private List<String> allowedPageTypes;
}
