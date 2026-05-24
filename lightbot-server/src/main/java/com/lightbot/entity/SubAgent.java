package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 子智能体配置表
 *
 * @author finch
 * @since 2026-05-24
 */
@Data
@TableName("subagent")
@Schema(description = "子智能体配置")
public class SubAgent {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("name")
    @Schema(description = "唯一标识（英文）")
    private String name;

    @TableField("display_name")
    @Schema(description = "显示名称（中文）")
    private String displayName;

    @TableField("description")
    @Schema(description = "子智能体描述")
    private String description;

    @TableField("system_prompt")
    @Schema(description = "系统提示词")
    private String systemPrompt;

    @TableField(value = "tools", typeHandler = com.lightbot.handler.JsonbTypeHandler.class)
    @Schema(description = "工具名称列表")
    private String tools;

    @TableField("model_id")
    @Schema(description = "可选的模型覆盖")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long modelId;

    @TableField("enabled")
    @Schema(description = "是否启用")
    private Integer enabled;

    @TableField("is_builtin")
    @Schema(description = "是否内置")
    private Integer isBuiltin;

    @TableField("user_id")
    @Schema(description = "创建者ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private java.time.LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private java.time.LocalDateTime updateTime;

    @TableField("deleted")
    @TableLogic
    @Schema(description = "逻辑删除标记")
    private Integer deleted;
}