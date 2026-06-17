package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 提示词表
 *
 * @author finch
 * @since 2026-05-27
 */
@Data
@TableName("prompt")
@Schema(description = "提示词表")
public class Prompt {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("prompt_key")
    @Size(max = 100, message = "Prompt Key不超过100字")
    @Schema(description = "提示词唯一标识")
    private String promptKey;

    @TableField("description")
    @Size(max = 200, message = "描述不超过200字")
    @Schema(description = "提示词描述")
    private String description;

    @TableField("latest_version")
    @Schema(description = "最新版本号")
    private String latestVersion;

    @TableField("tags")
    @Schema(description = "标签")
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
