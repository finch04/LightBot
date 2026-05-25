package com.lightbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 工作流单节点调试请求
 */
@Data
@Schema(description = "工作流单节点调试请求")
public class WorkflowNodeTestRequest {

    @NotBlank(message = "节点 ID 不能为空")
    @Schema(description = "待测试节点 ID")
    private String nodeId;

    @Schema(description = "当前画布工作流（优先使用）")
    private WorkflowGraphDTO graph;

    @Schema(description = "节点输入变量（key -> 值）")
    private Map<String, String> inputParams;
}
