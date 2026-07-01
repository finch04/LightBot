package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lightbot.enums.SessionStatus;
import com.lightbot.handler.JsonbTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.LocalDateTime;

/**
 * 对话会话表
 *
 * @author finch
 * @since 2026-05-19
 */
@Data
@TableName("chat_session")
@Schema(description = "对话会话表")
public class ChatSession {

    /** 默认会话标题 */
    public static final String DEFAULT_TITLE = "新对话";

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("agent_id")
    @Schema(description = "AgentID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long agentId;

    @TableField("agent_version_id")
    @Schema(description = "最近使用的Agent版本快照ID（agent_version.id），null=未指定")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long agentVersionId;

    @TableField("user_id")
    @Schema(description = "用户ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    @TableField("title")
    @Schema(description = "会话标题")
    private String title;

    @TableField("status")
    @Schema(description = "状态")
    private SessionStatus status;

    @TableField(value = "context", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Schema(description = "会话上下文")
    private String context;

    @TableField(value = "attachments", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Schema(description = "会话附件索引 JSON 数组")
    private String attachments;

    @TableField("message_count")
    @Schema(description = "消息数量")
    private Integer messageCount;

    @TableField("total_tokens")
    @Schema(description = "总Token消耗")
    private Long totalTokens;

    @TableField("last_message_at")
    @Schema(description = "最后消息时间")
    private LocalDateTime lastMessageAt;

    @TableField("pinned")
    @Schema(description = "是否置顶")
    private Boolean pinned;

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
