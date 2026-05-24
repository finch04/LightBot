package com.lightbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * MCP 工具 VO（运行时获取）
 *
 * @author finch
 * @since 2026-05-24
 */
@Data
@Schema(description = "MCP工具VO")
public class McpToolVO {

    @Schema(description = "工具名称")
    private String name;

    @Schema(description = "工具描述")
    private String description;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "输入参数Schema（JSON格式）")
    private String inputSchema;
}