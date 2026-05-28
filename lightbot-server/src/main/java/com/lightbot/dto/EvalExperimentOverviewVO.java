package com.lightbot.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 实验概览 VO（按评估器分组的统计）
 *
 * @author finch
 * @since 2026-05-27
 */
@Data
@Schema(description = "实验概览")
public class EvalExperimentOverviewVO {

    @Schema(description = "评估器版本ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long evaluatorVersionId;

    @Schema(description = "评估器名称")
    private String evaluatorName;

    @Schema(description = "评估器版本号")
    private String evaluatorVersion;

    @Schema(description = "平均得分")
    private BigDecimal avgScore;

    @Schema(description = "已评测条数")
    private Integer evaluatedCount;

    @Schema(description = "总条数")
    private Integer totalCount;
}
