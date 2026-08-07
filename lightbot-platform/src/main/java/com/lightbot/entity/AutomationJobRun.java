package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lightbot.enums.AutomationRunStatus;
import com.lightbot.handler.JsonbTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.LocalDateTime;

/**
 * 自动化定时任务执行记录
 *
 * @author finch
 * @since 2026-07-26
 */
@Data
@TableName(value = "automation_job_run", autoResultMap = true)
@Schema(description = "自动化任务执行记录")
public class AutomationJobRun {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("job_id")
    @Schema(description = "任务ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long jobId;

    @TableField("user_id")
    @Schema(description = "所属用户ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    @TableField("agent_id")
    @Schema(description = "智能体ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long agentId;

    @TableField("session_id")
    @Schema(description = "对话会话ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long sessionId;

    @TableField("job_name")
    @Schema(description = "任务名称快照")
    private String jobName;

    @TableField("agent_name")
    @Schema(description = "智能体名称快照")
    private String agentName;

    @TableField("instruction")
    @Schema(description = "指令快照")
    private String instruction;

    @TableField("trigger_type")
    @Schema(description = "触发类型 schedule/manual")
    private String triggerType;

    @TableField("trigger_time")
    @Schema(description = "触发时间")
    private LocalDateTime triggerTime;

    @TableField("status")
    @Schema(description = "执行状态")
    private AutomationRunStatus status;

    @TableField("lease_expire_at")
    @Schema(description = "running 租约到期时间")
    private LocalDateTime leaseExpireAt;

    @TableField("summary")
    @Schema(description = "结果摘要")
    private String summary;

    @TableField(value = "detail_json", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Schema(description = "执行详情快照 JSON（与对话消息同构）")
    private String detailJson;

    @TableField("error")
    @Schema(description = "错误信息")
    private String error;

    @TableField("duration_ms")
    @Schema(description = "耗时毫秒")
    private Long durationMs;

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
