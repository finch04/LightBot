package com.lightbot.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * MCP Server 安装方式
 *
 * @author finch
 * @since 2026-05-20
 */
@Getter
@AllArgsConstructor
public enum McpInstallType {

    NPX("npx", "NPX (Node.js)"),
    UVX("uvx", "UVX (Python)"),
    SSE("sse", "SSE (远程服务)");

    @EnumValue
    private final String code;

    private final String desc;

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static McpInstallType fromValue(String value) {
        for (McpInstallType type : values()) {
            if (type.code.equalsIgnoreCase(value) || type.desc.equalsIgnoreCase(value)
                    || type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的MCP安装类型: " + value);
    }
}
