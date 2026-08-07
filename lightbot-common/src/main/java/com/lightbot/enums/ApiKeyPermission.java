package com.lightbot.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
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
public enum ApiKeyPermission implements EnumDisplay {

    CHAT("chat", "仅对话"),
    FULL("full", "完全访问");

    @EnumValue
    private final String code;

    private final String desc;

    /** 对外 JSON 使用 code（chat/full），避免前端拿中文 desc 做相等判断失败 */
    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static ApiKeyPermission fromValue(String value) {
        if (value == null || value.isBlank()) {
            return CHAT;
        }
        for (ApiKeyPermission e : values()) {
            if (e.code.equalsIgnoreCase(value) || e.desc.equalsIgnoreCase(value) || e.name().equalsIgnoreCase(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知的 API Key 权限: " + value);
    }
}
