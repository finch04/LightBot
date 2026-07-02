package com.lightbot.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工作流测试运行列表项
 */
@Data
@Schema(description = "工作流测试运行列表项")
public class WorkflowTestRunVO {

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

    @Schema(description = "输入摘要")
    private String userInputSummary;

    @Schema(description = "输出摘要")
    private String outputSummary;

    @Schema(description = "耗时毫秒")
    private Long durationMs;

    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    @Schema(description = "结束时间")
    private LocalDateTime endTime;
}
