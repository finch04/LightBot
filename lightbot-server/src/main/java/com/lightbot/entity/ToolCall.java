package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lightbot.handler.JsonbTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.LocalDateTime;

/**
 * 工具调用记录表
 * <p>记录每次工具调用的输入、输出和状态</p>
 *
 * @author finch
 * @since 2026-05-22
 */
@Data
@TableName("tool_calls")
@Schema(description = "工具调用记录")
public class ToolCall {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("message_id")
    @Schema(description = "关联消息ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long messageId;

    @TableField("tool_call_id")
    @Schema(description = "工具调用ID（用于关联）")
    private String toolCallId;

    @TableField("tool_name")
    @Schema(description = "工具名称")
    private String toolName;

    @TableField(value = "tool_input", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Schema(description = "工具输入参数")
    private String toolInput;

    @TableField("tool_output")
    @Schema(description = "工具执行结果")
    private String toolOutput;

    @TableField("status")
    @Schema(description = "状态: pending/success/error")
    private String status;

    @TableField("error_message")
    @Schema(description = "错误信息")
    private String errorMessage;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
