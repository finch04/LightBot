package com.lightbot.dto.askdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 问数租户维度映射：callerContext 键 → 表字段 + 匹配策略
 *
 * <p>match：
 * <ul>
 *   <li>{@code eq} — 精确匹配（默认；企业）</li>
 *   <li>{@code subtree} — 本级及下级（查地区库展开后 IN；地区推荐）</li>
 *   <li>{@code prefix} — 字符串前缀匹配（自定义路径编码）</li>
 *   <li>{@code in} — 多值包含（callerContext 值为逗号分隔或数组，由上游展开）</li>
 * </ul>
 * <p>问数角色：有 enterpriseId 为企业用户（仅企业过滤）；否则为行业用户（仅地区过滤）。</p>
 *
 * @author finch
 * @since 2026-08-05
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AskTenantDimensionDef {

    /** 数据表字段 key */
    private String field;

    /**
     * 匹配策略：eq / prefix / in
     */
    private String match = "eq";

    public AskTenantDimensionDef() {
    }

    public AskTenantDimensionDef(String field, String match) {
        this.field = field;
        this.match = match;
    }
}
