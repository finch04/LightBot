package com.lightbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * API Key 创建请求
 *
 * @author finch
 * @since 2026-06-27
 */
@Data
@Schema(description = "API Key 创建请求")
public class ApiKeyCreateRequest {

    @NotBlank(message = "API Key 名称不能为空")
    @Size(max = 64, message = "名称最长 64 字符")
    @Schema(description = "API Key 名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "权限配置")
    private String permissions;

    @Schema(description = "过期时间")
    private String expiresAt;

    @Schema(description = "绑定的 Agent ID 列表")
    private List<String> agentIds;

    @Schema(description = "每分钟请求限制")
    private Integer rateLimit;

    @Schema(description = "每日 Token 配额")
    private Integer dailyQuota;
}
