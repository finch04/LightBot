package com.lightbot.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
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
}
