package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.lightbot.enums.AgentStatus;
import com.lightbot.enums.AgentType;
import com.lightbot.handler.JsonbTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.LocalDateTime;

/**
 * Agent表
 *
 * @author finch
 * @since 2026-05-19
 */
@Data
@TableName("agent")
@Schema(description = "Agent表")
public class Agent {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键ID")
    private Long id;

    @TableField("user_id")
    @Schema(description = "创建者ID")
    private Long userId;

    @TableField("name")
    @Schema(description = "Agent名称")
    private String name;

    @TableField("description")
    @Schema(description = "Agent描述")
    private String description;

    @TableField("system_prompt")
    @Schema(description = "系统提示词")
    private String systemPrompt;

    @TableField("avatar")
    @Schema(description = "头像URL")
    private String avatar;

    @TableField("agent_type")
    @Schema(description = "类型")
    private AgentType agentType;

    @TableField(value = "config", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Schema(description = "扩展配置")
    private String config;

    @TableField("status")
    @Schema(description = "状态")
    private AgentStatus status;

    @TableField("publish_time")
    @Schema(description = "发布时间")
    private LocalDateTime publishTime;

    @TableField("version")
    @Schema(description = "版本号")
    private Integer version;

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
