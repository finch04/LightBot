package com.lightbot.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 配置字段描述，用于前端动态渲染表单
 *
 * @author finch
 * @since 2026-05-19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "配置字段描述")
public class ConfigField {

    @Schema(description = "字段key（config JSONB中的key）")
    private String key;

    @Schema(description = "前端显示标签")
    private String label;

    @Schema(description = "控件类型：slider / number / select / text")
    private String type;

    @Schema(description = "最小值（slider/number）")
    private Double min;

    @Schema(description = "最大值（slider/number）")
    private Double max;

    @Schema(description = "步长（slider/number）")
    private Double step;

    @Schema(description = "下拉选项（select类型）")
    private List<Option> options;

    @Schema(description = "默认值")
    private Object defaultValue;

    @Schema(description = "提示说明")
    private String hint;

    /**
     * 下拉选项
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Option {
        @Schema(description = "选项值")
        private String value;

        @Schema(description = "显示标签")
        private String label;
    }
}
