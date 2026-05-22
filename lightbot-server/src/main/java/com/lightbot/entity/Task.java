package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lightbot.enums.TaskStatus;
import com.lightbot.enums.TaskType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务队列表
 *
 * @author finch
 * @since 2026-05-21
 */
@Data
@TableName("task")
@Schema(description = "任务队列表")
public class Task {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("name")
    @Schema(description = "任务名称")
    private String name;

    @TableField("type")
    @Schema(description = "任务类型")
    private TaskType type;

    @TableField("status")
    @Schema(description = "任务状态")
    private TaskStatus status;

    @TableField("progress")
    @Schema(description = "进度(0-100)")
    private Integer progress;

    @TableField("message")
    @Schema(description = "状态消息")
    private String message;

    @TableField("payload")
    @Schema(description = "任务参数(JSON)")
    private String payload;

    @TableField("result")
    @Schema(description = "任务结果(JSON)")
    private String result;

    @TableField("error")
    @Schema(description = "错误信息")
    private String error;

    @TableField("cancel_requested")
    @Schema(description = "是否请求取消(0/1)")
    private Integer cancelRequested;

    @TableField("user_id")
    @Schema(description = "用户ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    @TableField("ref_id")
    @Schema(description = "关联业务ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long refId;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @TableField("started_at")
    @Schema(description = "开始执行时间")
    private LocalDateTime startedAt;

    @TableField("completed_at")
    @Schema(description = "完成时间")
    private LocalDateTime completedAt;

    @TableField("deleted")
    @TableLogic
    @Schema(description = "逻辑删除标记")
    private Integer deleted;
}
