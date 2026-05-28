package com.lightbot.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 实验结果 VO
 *
 * @author finch
 * @since 2026-05-27
 */
@Data
@Schema(description = "实验结果详情")
public class EvalExperimentResultVO {

    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "输入内容")
    private String input;

    @Schema(description = "实际输出")
    private String actualOutput;

    @Schema(description = "参考输出")
    private String referenceOutput;

    @Schema(description = "评分")
    private BigDecimal score;

    @Schema(description = "评分理由")
    private String reason;

    @Schema(description = "评估器版本ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long evaluatorVersionId;

    @Schema(description = "评估时间")
    private LocalDateTime evaluationTime;
}
