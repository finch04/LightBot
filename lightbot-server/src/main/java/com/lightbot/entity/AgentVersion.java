package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lightbot.enums.AgentVersionStatus;
import com.lightbot.handler.JsonbTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.LocalDateTime;

/**
 * Agent 版本配置表
 */
@Data
@TableName("agent_version")
@Schema(description = "Agent版本表")
public class AgentVersion {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("agent_id")
    @Schema(description = "AgentID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long agentId;

    @TableField("user_id")
    @Schema(description = "用户ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    @TableField("version")
    @Schema(description = "版本号（发布递增，草稿为0）")
    private Integer version;

    @TableField("status")
    @Schema(description = "版本状态")
    private AgentVersionStatus status;

    @TableField(value = "config", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Schema(description = "版本配置快照")
    private String config;

    @TableField("node_count")
    @Schema(description = "节点数（工作流）")
    private Integer nodeCount;

    @TableField("edge_count")
    @Schema(description = "连线数（工作流）")
    private Integer edgeCount;

    @TableField("description")
    @Schema(description = "版本说明")
    private String description;

    @TableField("publish_time")
    @Schema(description = "发布时间")
    private LocalDateTime publishTime;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @TableField("deleted")
    @TableLogic
    @Schema(description = "逻辑删除")
    private Integer deleted;
}
