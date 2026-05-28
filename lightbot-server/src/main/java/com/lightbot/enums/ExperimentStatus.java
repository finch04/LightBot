package com.lightbot.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 实验状态
 *
 * @author finch
 * @since 2026-05-27
 */
@Getter
@AllArgsConstructor
public enum ExperimentStatus {

    DRAFT("draft", "草稿"),
    RUNNING("running", "运行中"),
    COMPLETED("completed", "已完成"),
    FAILED("failed", "失败"),
    STOPPED("stopped", "已停止");

    @EnumValue
    private final String code;

    private final String desc;

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static ExperimentStatus fromValue(String value) {
        for (ExperimentStatus e : values()) {
            if (e.code.equalsIgnoreCase(value) || e.desc.equalsIgnoreCase(value) || e.name().equalsIgnoreCase(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知的实验状态: " + value);
    }
}
