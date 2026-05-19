package com.lightbot.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 对话会话状态
 *
 * @author finch
 * @since 2026-05-19
 */
@Getter
@AllArgsConstructor
public enum SessionStatus {

    ACTIVE("active", "活跃"),
    ARCHIVED("archived", "已归档");

    @EnumValue
    private final String code;

    private final String desc;

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static SessionStatus fromValue(String value) {
        for (SessionStatus e : values()) {
            if (e.code.equalsIgnoreCase(value) || e.desc.equalsIgnoreCase(value) || e.name().equalsIgnoreCase(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知的会话状态: " + value);
    }
}
