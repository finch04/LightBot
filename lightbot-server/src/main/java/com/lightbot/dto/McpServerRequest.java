package com.lightbot.dto;

import com.lightbot.enums.McpInstallType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * MCP Server 请求DTO
 *
 * @author finch
 * @since 2026-05-20
 */
@Data
public class McpServerRequest {

    private Long id;

    @NotNull(message = "名称不能为空")
    private String name;

    private String description;

    @NotNull(message = "安装类型不能为空")
    private McpInstallType installType;

    private String deployConfig;

    private String detailConfig;

    private String host;
}
