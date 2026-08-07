package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 智能问数跨数据集关联
 *
 * @author finch
 * @since 2026-07-30
 */
@Data
@TableName("ask_relation")
@Schema(description = "智能问数跨数据集关联")
public class AskRelation {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("name")
    @Schema(description = "关联名称")
    private String name;

    @TableField("from_dataset_id")
    @Schema(description = "源数据集ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long fromDatasetId;

    @TableField("from_field")
    @Schema(description = "源字段 key")
    private String fromField;

    @TableField("to_dataset_id")
    @Schema(description = "目标数据集ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long toDatasetId;

    @TableField("to_field")
    @Schema(description = "目标字段 key")
    private String toField;

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
