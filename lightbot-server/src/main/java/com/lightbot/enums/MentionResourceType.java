package com.lightbot.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Mention 资源类型
 *
 * @author finch
 * @since 2026-06-29
 */
@Getter
@AllArgsConstructor
public enum MentionResourceType implements EnumDisplay {

    KNOWLEDGE("knowledge", "知识库"),
    SUBAGENT("subagent", "子智能体"),
    SKILL("skill", "Skill"),
    TOOL("tool", "工具"),
    MCP("mcp", "MCP");

    @EnumValue
    private final String code;

    private final String desc;

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static MentionResourceType fromValue(String value) {
        for (MentionResourceType e : values()) {
            if (e.code.equalsIgnoreCase(value) || e.desc.equalsIgnoreCase(value) || e.name().equalsIgnoreCase(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知的Mention资源类型: " + value);
    }
}
