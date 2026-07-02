package com.lightbot.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 工作流测试运行详情
 */
@Data
@Schema(description = "工作流测试运行详情")
public class WorkflowTestRunDetailVO {

    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "运行ID")
    private String runId;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "测试模式")
    private String testMode;

    @Schema(description = "是否使用草稿")
    private Boolean usedDraft;

    @Schema(description = "测试输入")
    private String userInput;

    @Schema(description = "输出内容")
    private String output;

    @Schema(description = "节点执行轨迹")
    private List<Map<String, Object>> nodeEvents;

    @Schema(description = "变量快照")
    private Map<String, Object> variables;

    @Schema(description = "工作流图快照")
    private Map<String, Object> workflowGraph;

    @Schema(description = "错误信息")
    private String errorInfo;

    @Schema(description = "耗时毫秒")
    private Long durationMs;

    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    @Schema(description = "结束时间")
    private LocalDateTime endTime;
}
