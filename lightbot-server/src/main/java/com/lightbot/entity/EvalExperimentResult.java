package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 评估实验结果表
 *
 * @author finch
 * @since 2026-05-27
 */
@Data
@TableName("eval_experiment_result")
@Schema(description = "评估实验结果表")
public class EvalExperimentResult {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("experiment_id")
    @Schema(description = "实验ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long experimentId;

    @TableField("input")
    @Schema(description = "输入")
    private String input;

    @TableField("actual_output")
    @Schema(description = "实际输出")
    private String actualOutput;

    @TableField("reference_output")
    @Schema(description = "参考输出")
    private String referenceOutput;

    @TableField("score")
    @Schema(description = "评分")
    private BigDecimal score;

    @TableField("reason")
    @Schema(description = "评分原因")
    private String reason;

    @TableField("evaluator_version_id")
    @Schema(description = "评估器版本ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long evaluatorVersionId;

    @TableField("evaluator_name")
    @Schema(description = "评估器名称")
    private String evaluatorName;

    @TableField("evaluation_time")
    @Schema(description = "评估时间")
    private LocalDateTime evaluationTime;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @TableField("deleted")
    @TableLogic
    @Schema(description = "逻辑删除标记")
    private Integer deleted;
}
