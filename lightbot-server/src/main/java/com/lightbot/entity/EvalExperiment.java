package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lightbot.enums.ExperimentStatus;
import com.lightbot.handler.JsonbTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.LocalDateTime;

/**
 * 评估实验表
 *
 * @author finch
 * @since 2026-05-27
 */
@Data
@TableName("eval_experiment")
@Schema(description = "评估实验表")
public class EvalExperiment {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("name")
    @Schema(description = "实验名称")
    private String name;

    @TableField("description")
    @Schema(description = "实验描述")
    private String description;

    @TableField("dataset_id")
    @Schema(description = "数据集ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long datasetId;

    @TableField("dataset_version_id")
    @Schema(description = "数据集版本ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long datasetVersionId;

    @TableField("dataset_version")
    @Schema(description = "数据集版本号")
    private String datasetVersion;

    @TableField(value = "evaluation_object_config", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Schema(description = "评估对象配置")
    private String evaluationObjectConfig;

    @TableField(value = "evaluator_config", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Schema(description = "评估器配置")
    private String evaluatorConfig;

    @TableField("status")
    @Schema(description = "实验状态")
    private ExperimentStatus status;

    @TableField("progress")
    @Schema(description = "进度")
    private Integer progress;

    @TableField("complete_time")
    @Schema(description = "完成时间")
    private LocalDateTime completeTime;

    @TableField("user_id")
    @Schema(description = "创建者ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    @TableField("task_id")
    @Schema(description = "任务ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;

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
