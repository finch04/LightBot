package com.lightbot.dto.askdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 问数指标定义（存于 ask_dataset.metrics）
 *
 * @author finch
 * @since 2026-07-30
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AskMetricDef {

    /** 指标编码（IR 引用） */
    private String code;
    private String name;
    private String description;
    /**
     * count | count_distinct | sum | avg | min | max
     */
    private String op = "count";
    /** 聚合字段；count 可不填 */
    private String field;
    /** 固化过滤（支持多算子；兼容旧 Map 等值格式） */
    @JsonDeserialize(using = AskFilterListDeserializer.class)
    private List<AskFilterDef> filters = new ArrayList<>();
    /** integer | currency | percent | number */
    private String format = "number";
    private List<String> synonyms = new ArrayList<>();
}
