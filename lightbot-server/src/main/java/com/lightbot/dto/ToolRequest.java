package com.lightbot.dto;

import com.lightbot.enums.AuthType;
import com.lightbot.enums.ToolType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Tool 请求DTO
 *
 * @author finch
 * @since 2026-05-20
 */
@Data
public class ToolRequest {

    private Long id;

    @NotBlank(message = "工具标识不能为空")
    private String name;

    private String displayName;

    private String description;

    @NotNull(message = "工具类型不能为空")
    private ToolType toolType;

    private String inputSchema;

    private String outputSchema;

    private String config;

    private String endpointUrl;

    private AuthType authType;

    private String authConfig;
}
