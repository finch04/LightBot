package com.lightbot.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 自动化任务执行状态
 *
 * @author finch
 * @since 2026-07-26
 */
@Getter
@AllArgsConstructor
public enum AutomationRunStatus implements EnumDisplay {

    RUNNING("running", "执行中"),
    SUCCESS("success", "成功"),
    FAILED("failed", "失败"),
    SKIPPED("skipped", "已跳过");

    @EnumValue
    private final String code;

    private final String desc;

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static AutomationRunStatus fromValue(String value) {
        for (AutomationRunStatus e : values()) {
            if (e.code.equalsIgnoreCase(value) || e.name().equalsIgnoreCase(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知的执行状态: " + value);
    }
}
