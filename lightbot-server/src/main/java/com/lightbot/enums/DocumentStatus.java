package com.lightbot.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 文档处理状态
 *
 * @author finch
 * @since 2026-05-19
 */
@Getter
@AllArgsConstructor
public enum DocumentStatus {

    PENDING("pending", "待处理"),
    PROCESSING("processing", "处理中"),
    COMPLETED("completed", "已完成"),
    FAILED("failed", "失败");

    @EnumValue
    private final String code;

    private final String desc;

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static DocumentStatus fromValue(String value) {
        for (DocumentStatus e : values()) {
            if (e.code.equalsIgnoreCase(value) || e.desc.equalsIgnoreCase(value) || e.name().equalsIgnoreCase(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知的文档状态: " + value);
    }
}
