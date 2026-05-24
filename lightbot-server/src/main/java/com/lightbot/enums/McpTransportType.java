package com.lightbot.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * MCP 传输类型
 *
 * @author finch
 * @since 2026-05-23
 */
@Getter
@AllArgsConstructor
public enum McpTransportType {

    SSE("sse", "SSE (Server-Sent Events)"),
    STDIO("stdio", "stdio (标准输入输出)"),
    STREAMABLE_HTTP("streamable_http", "Streamable HTTP");

    @EnumValue
    private final String code;

    private final String desc;

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static McpTransportType fromValue(String value) {
        for (McpTransportType type : values()) {
            if (type.code.equalsIgnoreCase(value) || type.desc.equalsIgnoreCase(value)
                    || type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的MCP传输类型: " + value);
    }
}
