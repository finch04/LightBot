package com.lightbot.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 自动化任务调度类型
 *
 * @author finch
 * @since 2026-07-26
 */
@Getter
@AllArgsConstructor
public enum AutomationScheduleType implements EnumDisplay {

    ONCE("once", "一次性"),
    DAILY("daily", "每天"),
    WEEKLY("weekly", "每周"),
    MONTHLY("monthly", "每月"),
    CRON("cron", "Cron");

    @EnumValue
    private final String code;

    private final String desc;

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static AutomationScheduleType fromValue(String value) {
        for (AutomationScheduleType e : values()) {
            if (e.code.equalsIgnoreCase(value) || e.name().equalsIgnoreCase(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知的调度类型: " + value);
    }
}
