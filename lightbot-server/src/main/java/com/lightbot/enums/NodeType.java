package com.lightbot.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Workflow 节点类型
 *
 * @author finch
 * @since 2026-05-19
 */
@Getter
@AllArgsConstructor
public enum NodeType {

    START("start", "开始节点"),
    END("end", "结束节点"),
    LLM("llm", "LLM节点"),
    CONDITION("condition", "条件分支"),
    RETRIEVAL("retrieval", "知识检索"),
    TOOL("tool", "工具调用"),
    SCRIPT("script", "脚本节点"),
    CODE("code", "代码节点");

    @EnumValue
    private final String code;

    private final String desc;

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static NodeType fromValue(String value) {
        for (NodeType e : values()) {
            if (e.code.equalsIgnoreCase(value) || e.desc.equalsIgnoreCase(value) || e.name().equalsIgnoreCase(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知的节点类型: " + value);
    }
}
