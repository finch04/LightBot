package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lightbot.enums.AutomationScheduleType;
import com.lightbot.handler.JsonbTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.LocalDateTime;

/**
 * 自动化定时任务配置
 *
 * @author finch
 * @since 2026-07-26
 */
@Data
@TableName(value = "automation_job", autoResultMap = true)
@Schema(description = "自动化定时任务")
public class AutomationJob {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("user_id")
    @Schema(description = "所属用户ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    @TableField("name")
    @Schema(description = "任务名称")
    private String name;

    @TableField("agent_id")
    @Schema(description = "智能体ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long agentId;

    @TableField("agent_name")
    @Schema(description = "智能体名称（冗余）")
    private String agentName;

    @TableField("instruction")
    @Schema(description = "文字指令")
    private String instruction;

    @TableField("schedule_type")
    @Schema(description = "调度类型")
    private AutomationScheduleType scheduleType;

    @TableField(value = "schedule_config", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Schema(description = "调度参数 JSON")
    private String scheduleConfig;

    @TableField("enabled")
    @Schema(description = "是否启用 0/1")
    private Integer enabled;

    @TableField("next_run_at")
    @Schema(description = "下次触发时间")
    private LocalDateTime nextRunAt;

    @TableField("last_run_at")
    @Schema(description = "最近触发时间")
    private LocalDateTime lastRunAt;

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
