package com.lightbot.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 认证类型
 *
 * @author finch
 * @since 2026-05-19
 */
@Getter
@AllArgsConstructor
public enum AuthType {

    NONE("none", "无认证"),
    API_KEY("api_key", "API Key"),
    OAUTH("oauth", "OAuth"),
    BEARER("bearer", "Bearer Token");

    @EnumValue
    private final String code;

    @JsonValue
    private final String desc;

    @JsonCreator
    public static AuthType fromValue(String value) {
        for (AuthType e : values()) {
            if (e.code.equalsIgnoreCase(value) || e.desc.equalsIgnoreCase(value) || e.name().equalsIgnoreCase(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知的认证类型: " + value);
    }
}
