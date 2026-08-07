package com.lightbot.dto.automation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 新建/更新自动化定时任务
 *
 * @author finch
 * @since 2026-07-26
 */
@Data
public class AutomationJobSaveDTO {

    @NotBlank(message = "任务名称不能为空")
    @Size(max = 64, message = "任务名称不能超过64字")
    private String name;

    @NotNull(message = "智能体不能为空")
    private Long agentId;

    @NotBlank(message = "文字指令不能为空")
    @Size(max = 2000, message = "文字指令不能超过2000字")
    private String instruction;

    /** once / daily / weekly / monthly / cron */
    @NotBlank(message = "触发方式不能为空")
    private String scheduleType;

    /** HH:mm，daily/weekly/monthly 使用 */
    private String time;

    /** once：yyyy-MM-dd HH:mm */
    private String onceAt;

    /** weekly：ISO 星期 1=周一 … 7=周日 */
    private List<Integer> weekdays;

    /** monthly：1-31 */
    private Integer monthDay;

    /** 标准 5 段 Cron：分 时 日 月 周 */
    private String cron;

    private Boolean enabled;
}
