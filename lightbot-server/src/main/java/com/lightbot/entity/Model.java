package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lightbot.enums.CommonStatus;
import com.lightbot.enums.ModelType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模型表
 *
 * @author finch
 * @since 2026-05-20
 */
@Data
@TableName("model")
@Schema(description = "模型表")
public class Model {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("provider_id")
    @Schema(description = "所属提供商ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long providerId;

    @TableField("model_id")
    @Schema(description = "模型标识(如qwen-max、gpt-4)")
    private String modelId;

    @TableField("name")
    @Schema(description = "模型显示名称")
    private String name;

    @TableField("type")
    @Schema(description = "模型类型")
    private ModelType type;

    @TableField("status")
    @Schema(description = "状态")
    private CommonStatus status;

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
