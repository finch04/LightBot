package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.lightbot.enums.WorkflowStatus;
import com.lightbot.handler.JsonbTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.LocalDateTime;

/**
 * Workflow表
 *
 * @author finch
 * @since 2026-05-19
 */
@Data
@TableName("workflow")
@Schema(description = "Workflow表")
public class Workflow {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键ID")
    private Long id;

    @TableField("agent_id")
    @Schema(description = "AgentID")
    private Long agentId;

    @TableField("user_id")
    @Schema(description = "创建者ID")
    private Long userId;

    @TableField("name")
    @Schema(description = "工作流名称")
    private String name;

    @TableField("description")
    @Schema(description = "工作流描述")
    private String description;

    @TableField(value = "graph_data", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Schema(description = "图数据")
    private String graphData;

    @TableField(value = "config", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Schema(description = "扩展配置")
    private String config;

    @TableField("status")
    @Schema(description = "状态")
    private WorkflowStatus status;

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
