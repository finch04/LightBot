package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lightbot.enums.CommonStatus;
import com.lightbot.handler.JsonbTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.LocalDateTime;

/**
 * Skill 表
 * <p>对标 Yuxi：Skill 是「编排指令包」，绑定一个或多个 Tool/MCP，
 * 通过 prompt_template 指导主 Agent 何时、如何使用这些能力。</p>
 *
 * @author finch
 * @since 2026-05-19
 */
@Data
@TableName("skill")
@Schema(description = "Skill 表")
public class Skill {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("slug")
    @Schema(description = "全局唯一标识（英文-小写-短横线）")
    private String slug;

    @TableField("agent_id")
    @Schema(description = "AgentID（兼容旧数据，全局 Skill 为空）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long agentId;

    @TableField("tool_id")
    @Schema(description = "（已废弃）单 Tool ID，仅做兼容；新建 Skill 请使用 toolIds")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long toolId;

    @TableField(value = "tool_ids", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Schema(description = "依赖的 Tool ID 列表（JSON 字符串数组）")
    private String toolIds;

    @TableField(value = "mcp_server_ids", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Schema(description = "依赖的 MCP Server ID 列表（JSON 字符串数组）")
    private String mcpServerIds;

    @TableField("model_id")
    @Schema(description = "可选的模型覆盖（保留字段）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long modelId;

    @TableField("name")
    @Schema(description = "技能名称（英文短名，对模型可读）")
    private String name;

    @TableField("display_name")
    @Schema(description = "显示名称（中文）")
    private String displayName;

    @TableField("description")
    @Schema(description = "技能描述（给模型看，用于决定是否使用）")
    private String description;

    @TableField("prompt_template")
    @Schema(description = "提示词模板（注入主 Agent 系统提示）")
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

    @TableField("scope")
    @Schema(description = "作用域：global=全局可复用；agent=按 Agent 私有")
    private String scope;

    @TableField("is_builtin")
    @Schema(description = "是否内置 Skill")
    private Integer isBuiltin;

    @TableField("content_hash")
    @Schema(description = "内置 Skill 内容 hash")
    private String contentHash;

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
