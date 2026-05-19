package com.lightbot.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户状态
 *
 * @author finch
 * @since 2026-05-19
 */
@Getter
@AllArgsConstructor
public enum UserStatus {

    ACTIVE("active", "正常"),
    DISABLED("disabled", "禁用");

    @EnumValue
    private final String code;

    @JsonValue
    private final String desc;

    @JsonCreator
    public static UserStatus fromValue(String value) {
        for (UserStatus e : values()) {
            if (e.code.equalsIgnoreCase(value) || e.desc.equalsIgnoreCase(value) || e.name().equalsIgnoreCase(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知的用户状态: " + value);
    }
}
