package com.lightbot.dto.askdata;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 问数增强预览：用当前表单配置试跑，不落库
 *
 * @author finch
 * @since 2026-07-31
 */
@Data
public class AskDatasetPreviewDTO {

    /**
     * default_filters — 按默认过滤查明细样例
     * metric — 按业务指标做聚合试算
     */
    @NotBlank(message = "mode 不能为空")
    @Pattern(regexp = "default_filters|metric", message = "mode 须为 default_filters 或 metric")
    private String mode;

    /** 覆盖用的默认过滤（可与表单一致，未保存也可测） */
    @Valid
    private List<AskFilterDef> defaultFilters = new ArrayList<>();

    /** metric 模式：待测指标定义 */
    @Valid
    private AskMetricDef metric;

    /** 明细预览条数 / 聚合结果上限 */
    private Integer limit;
}
