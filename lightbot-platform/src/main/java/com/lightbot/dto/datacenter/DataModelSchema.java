package com.lightbot.dto.datacenter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 数据模型 schema_json 结构（与前端表单设计器对齐）
 *
 * @author finch
 * @since 2026-07-26
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DataModelSchema {

    private List<FieldDef> fields = new ArrayList<>();
    private List<String> fuzzySearchFields = new ArrayList<>();
    private List<String> searchConditions = new ArrayList<>();
    private List<ConstraintRule> uniqueKeys = new ArrayList<>();
    private List<ConstraintRule> indexes = new ArrayList<>();

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FieldDef {
        /** 字段标识（API/前端 key） */
        private String key;
        /** 字段中文名（展示名） */
        private String label;
        /**
         * 字段描述（供大模型分析；DDL 列备注格式为「字段名：描述」）
         */
        private String description;
        /** input/textarea/number/date/datetime/select/radio/checkbox/upload */
        private String type;
        private Boolean required;
        /** 系统字段标记（如 createTime / updateTime），不落物理自定义列 */
        private Boolean system;
        private Map<String, Object> props;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ConstraintRule {
        private String id;
        private List<String> fields = new ArrayList<>();
    }
}
