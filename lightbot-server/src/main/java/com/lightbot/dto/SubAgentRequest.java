package com.lightbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * SubAgent 请求 DTO
 *
 * @author finch
 * @since 2026-05-24
 */
@Data
@Schema(description = "SubAgent请求")
public class SubAgentRequest {

    @Schema(description = "ID（更新时必填）")
    private Long id;

    @NotBlank(message = "名称不能为空")
    @Size(max = 50, message = "标识名称不超过50字")
    @Schema(description = "唯一标识（英文）")
    private String name;

    @NotBlank(message = "显示名称不能为空")
    @Size(max = 50, message = "显示名称不超过50字")
    @Schema(description = "显示名称（中文）")
    private String displayName;

    @NotBlank(message = "描述不能为空")
    @Size(max = 50, message = "子智能体描述不超过50字")
    @Schema(description = "子智能体描述")
    private String description;

    @NotBlank(message = "系统提示词不能为空")
    @Size(max = 2000, message = "系统提示词不超过2000字")
    @Schema(description = "系统提示词")
    private String systemPrompt;

    @Schema(description = "工具名称列表")
    private List<String> tools;

    @Schema(description = "可选的模型ID")
    private Long modelId;

    @Schema(description = "是否启用")
    private Boolean enabled;
}