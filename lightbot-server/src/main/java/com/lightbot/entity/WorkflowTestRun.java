package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lightbot.handler.JsonbTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.LocalDateTime;

/**
 * 工作流编排页测试运行记录
 */
@Data
@TableName("workflow_test_run")
@Schema(description = "工作流测试运行记录")
public class WorkflowTestRun {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("run_id")
    @Schema(description = "运行ID（挂起恢复与记录唯一标识）")
    private String runId;

    @TableField("agent_id")
    @Schema(description = "Agent ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long agentId;

    @TableField("user_id")
    @Schema(description = "执行用户ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    @TableField("test_mode")
    @Schema(description = "测试模式：generation | conversation")
    private String testMode;

    @TableField("used_draft")
    @Schema(description = "是否使用草稿配置")
    private Integer usedDraft;

    @TableField("status")
    @Schema(description = "状态：running | suspended | completed | failed")
    private String status;

    @TableField("user_input")
    @Schema(description = "测试输入")
    private String userInput;

    @TableField("output")
    @Schema(description = "输出内容")
    private String output;

    @TableField(value = "node_events", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Schema(description = "节点执行轨迹 JSON")
    private String nodeEvents;

    @TableField(value = "variables", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Schema(description = "变量快照 JSON")
    private String variables;

    @TableField(value = "workflow_graph", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Schema(description = "工作流图快照 JSON")
    private String workflowGraph;

    @TableField("error_info")
    @Schema(description = "错误信息")
    private String errorInfo;

    @TableField("duration_ms")
    @Schema(description = "耗时毫秒")
    private Long durationMs;

    @TableField("start_time")
    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    @TableField("end_time")
    @Schema(description = "结束时间")
    private LocalDateTime endTime;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @TableField("deleted")
    @TableLogic
    @Schema(description = "逻辑删除标记")
    private Integer deleted;
}
