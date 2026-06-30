package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
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
    @Size(max = 50, message = "标识名称不超过50字")
    @Schema(description = "唯一标识（英文）")
    private String name;

    @TableField("display_name")
    @Size(max = 50, message = "显示名称不超过50字")
    @Schema(description = "显示名称（中文）")
    private String displayName;

    @TableField("description")
    @Size(max = 200, message = "子智能体描述不超过200字")
    @Schema(description = "子智能体描述")
    private String description;

    @TableField("system_prompt")
    @Schema(description = "系统提示词")
    private String systemPrompt;

    @TableField(value = "tool_ids", typeHandler = com.lightbot.handler.JsonbTypeHandler.class)
    @Schema(description = "绑定工具ID列表（JSON数组）")
    private String toolIds;

    @TableField("model_id")
    @Schema(description = "可选的 Provider ID 覆盖，null 表示继承主 Agent")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long modelId;

    @TableField("llm_model")
    @Schema(description = "可选的模型名称覆盖（如 gpt-4o），与 model_id 配合")
    private String llmModel;

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