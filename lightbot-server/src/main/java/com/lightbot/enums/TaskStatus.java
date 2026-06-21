package com.lightbot.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 任务状态
 *
 * @author finch
 * @since 2026-05-21
 */
@Getter
@AllArgsConstructor
public enum TaskStatus implements EnumDisplay {

    PENDING("pending", "等待中"),
    RUNNING("running", "执行中"),
    SUCCESS("success", "成功"),
    FAILED("failed", "失败"),
    CANCELLED("cancelled", "已取消");

    @EnumValue
    private final String code;

    private final String desc;

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static TaskStatus fromValue(String value) {
        for (TaskStatus e : values()) {
            if (e.code.equalsIgnoreCase(value) || e.name().equalsIgnoreCase(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知的任务状态: " + value);
    }
}
