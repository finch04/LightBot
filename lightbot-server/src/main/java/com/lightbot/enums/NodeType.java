package com.lightbot.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
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

    START("start", "开始"),
    END("end", "结束"),
    LLM("llm", "大模型"),
    TOOL("tool", "工具"),
    CONDITION("condition", "条件"),
    CODE("code", "代码");

    @EnumValue
    private final String code;

    @JsonValue
    private final String desc;
}
