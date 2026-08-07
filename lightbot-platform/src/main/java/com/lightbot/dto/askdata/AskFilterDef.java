package com.lightbot.dto.askdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 问数过滤条件（默认过滤 / 指标固化过滤）
 * <p>
 * 算子：eq / ne / gt / gte / lt / lte / like / not_like / starts_with / not_starts_with /
 * in / not_in / between / is_null / is_not_null / in_last
 *
 * @author finch
 * @since 2026-07-31
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AskFilterDef {

    private String field;

    /** 默认等值 */
    private String op = "eq";

    private Object value;
}
