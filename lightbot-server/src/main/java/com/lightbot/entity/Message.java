package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lightbot.enums.ContentType;
import com.lightbot.enums.MessageRole;
import com.lightbot.enums.MessageType;
import com.lightbot.handler.JsonbTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.LocalDateTime;

/**
 * 消息表
 *
 * @author finch
 * @since 2026-05-19
 */
@Data
@TableName("message")
@Schema(description = "消息表")
public class Message {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("session_id")
    @Schema(description = "会话ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long sessionId;

    @TableField("role")
    @Schema(description = "角色")
    private MessageRole role;

    @TableField("content")
    @Schema(description = "消息内容")
    private String content;

    @TableField("content_type")
    @Schema(description = "内容类型")
    private ContentType contentType;

    @TableField("message_type")
    @Schema(description = "消息类型")
    private MessageType messageType;

    @TableField("token_count")
    @Schema(description = "Token数量")
    private Integer tokenCount;

    @TableField(value = "metadata", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Schema(description = "元数据")
    private String metadata;

    @TableField("parent_id")
    @Schema(description = "父消息ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentId;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
