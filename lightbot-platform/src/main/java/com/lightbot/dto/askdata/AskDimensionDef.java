package com.lightbot.dto.askdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 问数维度定义（存于 ask_dataset.dimensions）
 *
 * @author finch
 * @since 2026-07-30
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AskDimensionDef {

    /** 字段 key（与 data_model schema 对齐，系统字段可用 createTime） */
    private String fieldKey;
    /** 显示名 */
    private String name;
    /** categorical | time | geo */
    private String type = "categorical";
    private List<String> synonyms = new ArrayList<>();
    /** day | week | month（type=time 时有效） */
    private String timeGrain;
    /** 高基数：禁止无过滤 groupBy */
    private Boolean highCardinality = false;
}
