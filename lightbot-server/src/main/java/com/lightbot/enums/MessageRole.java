package com.lightbot.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 消息角色
 *
 * @author finch
 * @since 2026-05-19
 */
@Getter
@AllArgsConstructor
public enum MessageRole {

    USER("user", "用户"),
    ASSISTANT("assistant", "助手"),
    SYSTEM("system", "系统"),
    TOOL("tool", "工具");

    @EnumValue
    private final String code;

    private final String desc;

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static MessageRole fromValue(String value) {
        for (MessageRole e : values()) {
            if (e.code.equalsIgnoreCase(value) || e.desc.equalsIgnoreCase(value) || e.name().equalsIgnoreCase(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知的消息角色: " + value);
    }
}
