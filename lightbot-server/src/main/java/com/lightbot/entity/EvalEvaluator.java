package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评估器表
 *
 * @author finch
 * @since 2026-05-27
 */
@Data
@TableName("eval_evaluator")
@Schema(description = "评估器表")
public class EvalEvaluator {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("name")
    @Size(max = 50, message = "评估器名称不超过50字")
    @Schema(description = "评估器名称")
    private String name;

    @TableField("description")
    @Size(max = 200, message = "评估器描述不超过200字")
    @Schema(description = "评估器描述")
    private String description;

    @TableField("tags")
    @Size(max = 200, message = "标签不超过200字")
    @Schema(description = "标签，逗号分隔")
    private String tags;

    @TableField("user_id")
    @Schema(description = "创建者ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

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
