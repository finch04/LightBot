package com.lightbot.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 提示词版本状态
 *
 * @author finch
 * @since 2026-05-27
 */
@Getter
@AllArgsConstructor
public enum PromptVersionStatus {

    PRE("pre", "预发布"),
    RELEASE("release", "正式版本");

    @EnumValue
    private final String code;

    private final String desc;

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static PromptVersionStatus fromValue(String value) {
        for (PromptVersionStatus e : values()) {
            if (e.code.equalsIgnoreCase(value) || e.desc.equalsIgnoreCase(value) || e.name().equalsIgnoreCase(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知的提示词版本状态: " + value);
    }
}
