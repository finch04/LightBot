package com.lightbot.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lightbot.dto.askdata.AskDimensionDef;
import com.lightbot.dto.askdata.AskFilterDef;
import com.lightbot.dto.askdata.AskMetricDef;
import com.lightbot.dto.askdata.AskTenantDimensionDef;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 问数数据集 VO
 *
 * @author finch
 * @since 2026-07-30
 */
@Data
@Schema(description = "问数数据集")
public class AskDatasetVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long dataModelId;

    private String dataModelName;
    private String tableName;
    private String code;
    private String name;
    private String description;
    private String defaultTimeField;
    private List<AskFilterDef> defaultFilters = new ArrayList<>();
    private List<String> sensitiveFields = new ArrayList<>();
    private List<AskDimensionDef> dimensions = new ArrayList<>();
    private List<AskMetricDef> metrics = new ArrayList<>();
    /** 来自数据模型表单的字段语义（label/description），供问数目录使用 */
    private List<Map<String, Object>> modelFields = new ArrayList<>();
    private Map<String, Object> profile = new LinkedHashMap<>();
    /** 租户维度映射：callerContext 键 → { field, match } */
    private Map<String, AskTenantDimensionDef> tenantDimensions = new LinkedHashMap<>();
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
