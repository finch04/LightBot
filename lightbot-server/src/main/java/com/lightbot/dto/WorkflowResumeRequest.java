package com.lightbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 工作流人工确认恢复请求
 */
@Data
@Schema(description = "工作流人工确认恢复请求")
public class WorkflowResumeRequest {

    @NotBlank(message = "runId 不能为空")
    @Schema(description = "挂起的运行 ID")
    private String runId;

    @Schema(description = "确认表单数据（key 与 confirm 节点 formFields 对齐）")
    private Map<String, Object> formData;
}
