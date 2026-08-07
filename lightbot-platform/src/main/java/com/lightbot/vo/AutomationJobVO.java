package com.lightbot.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 自动化定时任务 VO
 *
 * @author finch
 * @since 2026-07-26
 */
@Data
public class AutomationJobVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String name;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long agentId;

    private String agentName;

    private String instruction;

    private String scheduleType;

    private String time;

    private String onceAt;

    private List<Integer> weekdays;

    private Integer monthDay;

    private String cron;

    private Boolean enabled;

    private LocalDateTime nextRunAt;

    private LocalDateTime lastRunAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
