package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lightbot.enums.ApiKeyPermission;
import com.lightbot.handler.JsonbTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
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
@TableName(value = "api_key", autoResultMap = true)
@Schema(description = "API Key表")
public class ApiKey {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("user_id")
    @Schema(description = "创建人用户ID（审计字段，不作为运行时身份）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    @TableField("name")
    @Size(max = 64, message = "Key名称不超过64字")
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

    @TableField(value = "memory_config", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Schema(description = "长期记忆策略覆盖，null 表示跟随企业默认")
    private String memoryConfig;

    @TableField(value = "business_page_config", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Schema(description = "业务办理页白名单，null/inherit 表示全部已启用页")
    private String businessPageConfig;

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
