package com.lightbot.dto;

import com.lightbot.enums.AuthType;
import com.lightbot.enums.CommonStatus;
import com.lightbot.enums.ToolType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
    @Size(max = 30, message = "工具标识不超过30字")
    private String name;

    @Size(max = 30, message = "显示名称不超过30字")
    private String displayName;

    @Size(max = 50, message = "工具描述不超过50字")
    private String description;

    @NotNull(message = "工具类型不能为空")
    private ToolType toolType;

    private String inputSchema;

    private String outputSchema;

    private String outputExample;

    private String config;

    private String endpointUrl;

    private AuthType authType;

    private String authConfig;

    /** 工具标签（JSON数组字符串） */
    private String tags;

    private CommonStatus status;
}
