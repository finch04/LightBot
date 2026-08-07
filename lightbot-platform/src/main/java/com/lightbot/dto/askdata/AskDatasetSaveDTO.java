package com.lightbot.dto.askdata;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 创建/更新问数数据集
 *
 * @author finch
 * @since 2026-07-30
 */
@Data
public class AskDatasetSaveDTO {

    @NotNull(message = "数据模型ID不能为空")
    private Long dataModelId;

    @NotBlank(message = "编码不能为空")
    @Size(max = 64)
    @Pattern(regexp = "^[a-z][a-z0-9_]{0,63}$", message = "编码须小写字母开头，仅含小写字母数字下划线")
    private String code;

    @NotBlank(message = "名称不能为空")
    @Size(max = 128)
    private String name;

    @Size(max = 512)
    private String description;

    private String defaultTimeField;

    private List<AskFilterDef> defaultFilters = new ArrayList<>();

    private List<String> sensitiveFields = new ArrayList<>();

    private List<AskDimensionDef> dimensions = new ArrayList<>();

    private List<AskMetricDef> metrics = new ArrayList<>();

    /**
     * 租户维度映射：callerContext 键 → {@link AskTenantDimensionDef}
     * <p>兼容旧格式：值为字段名字符串，等价于 {@code {"field":"...","match":"eq"}}</p>
     */
    private Map<String, Object> tenantDimensions = new LinkedHashMap<>();
}
