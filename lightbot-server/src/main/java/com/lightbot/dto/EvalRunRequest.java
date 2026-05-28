package com.lightbot.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 运行 RAG 评估请求
 *
 * @author finch
 * @since 2026-05-28
 */
@Data
@Schema(description = "运行 RAG 评估请求")
public class EvalRunRequest {

    @NotNull(message = "基准ID不能为空")
    @Schema(description = "评估基准ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long benchmarkId;

    @Schema(description = "答案生成模型提供商ID（为空则不生成答案）")
    private Long answerProviderId;

    @Schema(description = "答案生成模型ID（为空则使用提供商默认模型）")
    private String answerModelId;

    @Schema(description = "评判模型提供商ID（为空则不进行 LLM Judge）")
    private Long judgeProviderId;

    @Schema(description = "评判模型ID（为空则使用提供商默认模型）")
    private String judgeModelId;
}
