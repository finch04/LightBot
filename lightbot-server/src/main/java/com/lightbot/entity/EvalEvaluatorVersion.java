package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lightbot.enums.EvalVersionStatus;
import com.lightbot.handler.JsonbTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.LocalDateTime;

/**
 * 评估器版本表
 *
 * @author finch
 * @since 2026-05-27
 */
@Data
@TableName("eval_evaluator_version")
@Schema(description = "评估器版本表")
public class EvalEvaluatorVersion {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("evaluator_id")
    @Schema(description = "评估器ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long evaluatorId;

    @TableField("version")
    @Schema(description = "版本号")
    private String version;

    @TableField(value = "model_config", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Schema(description = "模型配置")
    private String modelConfig;

    @TableField("prompt")
    @Schema(description = "评估提示词")
    private String prompt;

    @TableField(value = "variables", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Schema(description = "变量定义")
    private String variables;

    @TableField("status")
    @Schema(description = "版本状态")
    private EvalVersionStatus status;

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
