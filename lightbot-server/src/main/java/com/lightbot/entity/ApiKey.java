package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lightbot.enums.ApiKeyPermission;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * API Key 表
 *
 * @author finch
 * @since 2026-06-25
 */
@Data
@TableName("api_key")
@Schema(description = "API Key表")
public class ApiKey {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("user_id")
    @Schema(description = "所属用户ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    @TableField("name")
    @Schema(description = "Key名称")
    private String name;

    @TableField("key_prefix")
    @Schema(description = "Key前缀（用于展示）")
    private String keyPrefix;

    @TableField("key_hash")
    @Schema(description = "Key的SHA-256哈希值")
    private String keyHash;

    @TableField("permissions")
    @Schema(description = "权限范围")
    private ApiKeyPermission permissions;

    @TableField(value = "agent_ids", typeHandler = JacksonTypeHandler.class)
    @Schema(description = "绑定的Agent ID列表，null表示全部")
    private List<String> agentIds;

    @TableField("rate_limit")
    @Schema(description = "每分钟调用上限，默认60")
    private Integer rateLimit;

    @TableField("daily_quota")
    @Schema(description = "每日Token配额，默认100000")
    private Integer dailyQuota;

    @TableField("used_tokens")
    @Schema(description = "当日已用Token数")
    private Long usedTokens;

    @TableField("quota_reset_at")
    @Schema(description = "配额重置日期")
    private LocalDate quotaResetAt;

    @TableField("is_enabled")
    @Schema(description = "是否启用")
    private Integer isEnabled;

    @TableField("last_used_at")
    @Schema(description = "最近使用时间")
    private LocalDateTime lastUsedAt;

    @TableField("expires_at")
    @Schema(description = "过期时间")
    private LocalDateTime expiresAt;

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
