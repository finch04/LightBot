package com.lightbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 工作流调试运行请求
 */
@Data
@Schema(description = "工作流调试运行请求")
public class WorkflowTestRequest {

    @NotBlank(message = "测试输入不能为空")
    @Schema(description = "测试输入内容")
    private String input;

    @Schema(description = "是否使用草稿配置（默认 true，当 graph 为空时生效）")
    private Boolean useDraft = true;

    @Schema(description = "当前画布工作流（优先使用，测试时无需先暂存）")
    private WorkflowGraphDTO graph;
}
