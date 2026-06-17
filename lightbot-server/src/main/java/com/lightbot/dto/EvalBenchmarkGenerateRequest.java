package com.lightbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * AI 生成评估基准请求
 *
 * @author finch
 * @since 2026-05-28
 */
@Data
@Schema(description = "AI 生成评估基准请求")
public class EvalBenchmarkGenerateRequest {

    @NotBlank(message = "基准名称不能为空")
    @Size(max = 50, message = "基准名称不超过50字")
    @Schema(description = "基准名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Size(max = 200, message = "基准描述不超过200字")
    @Schema(description = "基准描述")
    private String description;

    @Min(value = 1, message = "生成数量至少为1")
    @Max(value = 100, message = "生成数量最多100")
    @Schema(description = "生成题目数量", defaultValue = "10")
    private Integer count = 10;

    @Schema(description = "模型提供商ID（为空则使用系统默认）")
    private Long providerId;

    @Schema(description = "模型ID（为空则使用系统默认）")
    private String modelId;

    @Min(value = 1, message = "相似chunks数量至少为1")
    @Max(value = 10, message = "相似chunks数量最多10")
    @Schema(description = "生成时参考的相邻片段数量", defaultValue = "3")
    private Integer neighborCount = 3;
}
