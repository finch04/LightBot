package com.lightbot.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 知识库类型
 *
 * @author finch
 * @since 2026-06-15
 */
@Getter
@AllArgsConstructor
public enum KnowledgeType {

    PG("pg", "PostgreSQL"),
    MILVUS("milvus", "Milvus");

    @EnumValue
    private final String code;

    private final String desc;

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static KnowledgeType fromValue(String value) {
        for (KnowledgeType e : values()) {
            if (e.code.equalsIgnoreCase(value) || e.desc.equalsIgnoreCase(value) || e.name().equalsIgnoreCase(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知的知识库类型: " + value);
    }
}
