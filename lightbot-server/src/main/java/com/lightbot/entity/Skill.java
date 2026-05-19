package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.lightbot.enums.CommonStatus;
import com.lightbot.handler.JsonbTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.LocalDateTime;

/**
 * Skill表
 *
 * @author finch
 * @since 2026-05-19
 */
@Data
@TableName("skill")
@Schema(description = "Skill表")
public class Skill {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键ID")
    private Long id;

    @TableField("agent_id")
    @Schema(description = "AgentID")
    private Long agentId;

    @TableField("tool_id")
    @Schema(description = "ToolID")
    private Long toolId;

    @TableField("name")
    @Schema(description = "技能名称")
    private String name;

    @TableField("description")
    @Schema(description = "技能描述")
    private String description;

    @TableField("prompt_template")
    @Schema(description = "提示词模板")
    private String promptTemplate;

    @TableField(value = "config", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Schema(description = "扩展配置")
    private String config;

    @TableField("sort_order")
    @Schema(description = "排序序号")
    private Integer sortOrder;

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
