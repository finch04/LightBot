package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lightbot.enums.AuthType;
import com.lightbot.enums.CommonStatus;
import com.lightbot.enums.ToolType;
import com.lightbot.handler.JsonbTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.LocalDateTime;

/**
 * Tool表
 *
 * @author finch
 * @since 2026-05-19
 */
@Data
@TableName("tool")
@Schema(description = "Tool表")
public class Tool {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("user_id")
    @Schema(description = "创建者ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    @TableField("name")
    @Size(max = 50, message = "工具标识不超过50字")
    @Schema(description = "Tool唯一标识")
    private String name;

    @TableField("display_name")
    @Size(max = 50, message = "显示名称不超过50字")
    @Schema(description = "显示名称")
    private String displayName;

    @TableField("description")
    @Size(max = 200, message = "工具描述不超过200字")
    @Schema(description = "Tool描述")
    private String description;

    @TableField("tool_type")
    @Schema(description = "类型")
    private ToolType toolType;

    @TableField(value = "input_schema", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Schema(description = "输入参数Schema")
    private String inputSchema;

    @TableField(value = "output_schema", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Schema(description = "输出参数Schema")
    private String outputSchema;

    @TableField(value = "config", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Schema(description = "扩展配置")
    private String config;

    @TableField("endpoint_url")
    @Schema(description = "API端点地址")
    private String endpointUrl;

    @TableField("auth_type")
    @Schema(description = "认证类型")
    private AuthType authType;

    @TableField(value = "auth_config", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Schema(description = "认证配置")
    private String authConfig;

    @TableField(value = "tags", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Schema(description = "工具标签列表")
    private String tags;

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
