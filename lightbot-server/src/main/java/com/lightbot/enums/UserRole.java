package com.lightbot.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户角色
 *
 * @author finch
 * @since 2026-05-19
 */
@Getter
@AllArgsConstructor
public enum UserRole {

    ADMIN("admin", "管理员"),
    USER("user", "普通用户");

    @EnumValue
    private final String code;

    @JsonValue
    private final String desc;
}
