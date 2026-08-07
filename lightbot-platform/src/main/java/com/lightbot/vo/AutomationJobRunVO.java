package com.lightbot.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 自动化任务执行记录 VO
 *
 * @author finch
 * @since 2026-07-26
 */
@Data
public class AutomationJobRunVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long jobId;

    private String jobName;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long agentId;

    private String agentName;

    private String instruction;

    private String triggerType;

    private LocalDateTime triggerTime;

    private String status;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long sessionId;

    private String summary;

    /**
     * 执行详情快照（与对话消息同构：content / metadata / toolEvents），
     * 供任务详情展示思考过程、工具调用、工作流节点等
     */
    private Object detail;

    private String error;

    private Long durationMs;

    /** 前端展示用耗时文案 */
    private String duration;
}
