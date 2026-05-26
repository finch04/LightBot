package com.lightbot.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Agent 类型
 *
 * @author finch
 * @since 2026-05-19
 */
@Getter
@AllArgsConstructor
public enum AgentType {

    CHAT("chat", "对话型"),
    WORKFLOW("workflow", "工作流型");

    /** 已废弃类型，兼容旧数据 */
    private static final String LEGACY_ASSISTANT = "assistant";

    @EnumValue
    private final String code;

    private final String desc;

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static AgentType fromValue(String value) {
        if (value != null && LEGACY_ASSISTANT.equalsIgnoreCase(value)) {
            return CHAT;
        }
        for (AgentType e : values()) {
            if (e.code.equalsIgnoreCase(value) || e.desc.equalsIgnoreCase(value) || e.name().equalsIgnoreCase(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知的Agent类型: " + value);
    }
}
