package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lightbot.enums.UserMemoryStatus;
import com.lightbot.enums.UserMemoryType;
import com.lightbot.handler.JsonbTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户长期记忆表
 *
 * @author finch
 * @since 2026-07-09
 */
@Data
@TableName("user_memory")
@Schema(description = "用户长期记忆表")
public class UserMemory {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("user_id")
    @Schema(description = "平台用户ID（开放 API 外部记忆可为空或虚拟身份）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    @TableField("api_key_id")
    @Schema(description = "企业 API Key ID（开放 API 外部用户记忆）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long apiKeyId;

    @TableField("external_user_id")
    @Schema(description = "上层业务终端用户标识（与 api_key_id 组成记忆命名空间）")
    private String externalUserId;

    @TableField("agent_id")
    @Schema(description = "AgentID，空表示用户全局记忆")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long agentId;

    @TableField("session_id")
    @Schema(description = "来源会话ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long sessionId;

    @TableField("memory_type")
    @Schema(description = "记忆类型")
    private UserMemoryType memoryType;

    @TableField("content")
    @Size(max = 1000, message = "记忆内容不超过1000字")
    @Schema(description = "记忆内容")
    private String content;

    @TableField(value = "keywords", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Size(max = 800, message = "关键词配置不超过800字")
    @Schema(description = "关键词列表")
    private String keywords;

    @TableField("source_message_id")
    @Schema(description = "来源消息ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long sourceMessageId;

    @TableField("confidence")
    @Schema(description = "置信度")
    private BigDecimal confidence;

    @TableField("status")
    @Schema(description = "状态")
    private UserMemoryStatus status;

    @TableField("last_used_at")
    @Schema(description = "最后使用时间")
    private LocalDateTime lastUsedAt;

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
