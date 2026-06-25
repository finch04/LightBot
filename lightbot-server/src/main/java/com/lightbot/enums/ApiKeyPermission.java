package com.lightbot.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * API Key 权限枚举
 *
 * @author finch
 * @since 2026-06-25
 */
@Getter
@AllArgsConstructor
public enum ApiKeyPermission {

    CHAT("chat", "仅对话"),
    FULL("full", "完全访问");

    @EnumValue
    private final String code;

    @JsonValue
    private final String desc;
}
