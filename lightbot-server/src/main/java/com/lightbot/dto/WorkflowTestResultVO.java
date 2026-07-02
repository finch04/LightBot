package com.lightbot.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 工作流调试运行结果
 */
@Data
@Builder
@Schema(description = "工作流调试运行结果")
public class WorkflowTestResultVO {

    @Schema(description = "输出内容")
    private String output;

    @Schema(description = "节点执行轨迹")
    private List<Map<String, Object>> nodeEvents;

    @Schema(description = "是否使用草稿")
    private Boolean usedDraft;

    @Schema(description = "执行完成后的变量快照")
    private Map<String, Object> variables;

    @Schema(description = "是否因人工确认节点挂起")
    private Boolean suspended;

    @Schema(description = "挂起运行 ID（恢复时使用）")
    private String runId;

    @Schema(description = "人工确认表单（message + formFields）")
    private Map<String, Object> confirmForm;

    @Schema(description = "测试记录 DB 主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long testRunId;
}
