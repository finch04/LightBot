package com.lightbot.dto.askdata;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 轻量问数增强：业务说明 / 默认时间 / 敏感字段 / 默认过滤 / 自定义业务指标 / 租户维度
 *
 * @author finch
 * @since 2026-07-31
 */
@Data
public class AskDatasetEnhanceDTO {

    @Size(max = 512)
    private String description;

    private String defaultTimeField;

    private List<String> sensitiveFields = new ArrayList<>();

    /** 数据集级默认过滤（字段 + 算子 + 值） */
    @Valid
    private List<AskFilterDef> defaultFilters = new ArrayList<>();

    /**
     * 自定义业务指标（不含系统自动的 cnt / sum_* / avg_*）
     * 保存时与自动指标合并
     */
    @Valid
    private List<AskMetricDef> customMetrics = new ArrayList<>();

    /**
     * 租户维度映射：callerContext 键 → {@link AskTenantDimensionDef}
     * <p>兼容旧格式：值为字段名字符串，等价于 {@code {"field":"...","match":"eq"}}</p>
     */
    private Map<String, Object> tenantDimensions = new LinkedHashMap<>();
}
