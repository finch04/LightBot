package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 子代理运行记录表
 *
 * @author finch
 * @since 2026-06-25
 */
@Data
@TableName("subagent_run")
@Schema(description = "子代理运行记录")
public class SubAgentRun {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("thread_id")
    @Schema(description = "子代理线程ID")
    private String threadId;

    @TableField("parent_thread_id")
    @Schema(description = "父Agent线程ID")
    private String parentThreadId;

    @TableField("subagent_name")
    @Schema(description = "子代理名称")
    private String subagentName;

    @TableField("task")
    @Schema(description = "任务描述")
    private String task;

    @TableField("status")
    @Schema(description = "运行状态: pending/running/completed/failed")
    private String status;

    @TableField("request_id")
    @Schema(description = "幂等键")
    private String requestId;

    @TableField("reply")
    @Schema(description = "最终回复")
    private String reply;

    @TableField("tool_call_count")
    @Schema(description = "工具调用次数")
    private Integer toolCallCount;

    @TableField("start_time")
    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    @TableField("end_time")
    @Schema(description = "结束时间")
    private LocalDateTime endTime;

    @TableField("error_message")
    @Schema(description = "错误信息")
    private String errorMessage;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
