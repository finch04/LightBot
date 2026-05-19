package com.lightbot.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Tool 类型
 *
 * @author finch
 * @since 2026-05-19
 */
@Getter
@AllArgsConstructor
public enum ToolType {

    BUILTIN("builtin", "内置"),
    CUSTOM("custom", "自定义"),
    API("api", "API调用"),
    MCP("mcp", "MCP协议");

    @EnumValue
    private final String code;

    @JsonValue
    private final String desc;
}
