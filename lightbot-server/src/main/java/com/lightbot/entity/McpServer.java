package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lightbot.enums.CommonStatus;
import com.lightbot.enums.McpInstallType;
import com.lightbot.enums.McpTransportType;
import com.lightbot.handler.JsonbTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.LocalDateTime;

/**
 * MCP Server表
 *
 * @author finch
 * @since 2026-05-20
 */
@Data
@TableName("mcp_server")
@Schema(description = "MCP Server表")
public class McpServer {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("name")
    @Schema(description = "服务名称")
    private String name;

    @TableField("description")
    @Schema(description = "服务描述")
    private String description;

    @TableField("install_type")
    @Schema(description = "安装方式")
    private McpInstallType installType;

    @TableField(value = "deploy_config", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Schema(description = "部署配置(JSONB)")
    private String deployConfig;

    @TableField(value = "detail_config", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Schema(description = "详细配置/工具列表(JSONB)")
    private String detailConfig;

    @TableField("host")
    @Schema(description = "服务地址")
    private String host;

    @TableField("transport")
    @Schema(description = "传输类型: sse, stdio, streamable_http")
    private McpTransportType transport;

    @TableField(value = "headers", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Schema(description = "HTTP请求头(JSONB)")
    private String headers;

    @TableField(value = "disabled_tools", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Schema(description = "禁用的工具名列表(JSONB数组)")
    private String disabledTools;

    @TableField("status")
    @Schema(description = "状态")
    private CommonStatus status;

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
