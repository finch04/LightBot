package com.lightbot.dto;

import com.lightbot.enums.McpInstallType;
import com.lightbot.enums.McpTransportType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
    @Size(max = 30, message = "服务名称不超过30字")
    private String name;

    @Size(max = 50, message = "服务描述不超过50字")
    private String description;

    @NotNull(message = "安装类型不能为空")
    private McpInstallType installType;

    private String deployConfig;

    private String detailConfig;

    private String host;

    @NotNull(message = "传输类型不能为空")
    private McpTransportType transport;

    private String headers;

    private String disabledTools;
}
