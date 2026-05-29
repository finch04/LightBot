package com.lightbot.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 问答对状态枚举
 *
 * @author finch
 * @since 2026-05-29
 */
@Getter
@AllArgsConstructor
public enum QaPairStatus {

    PENDING("pending", "待向量化"),
    VECTORIZING("vectorizing", "向量化中"),
    ACTIVE("active", "生效"),
    FAILED("failed", "失败");

    @EnumValue
    private final String code;

    @JsonValue
    private final String desc;

    public static QaPairStatus fromCode(String code) {
        for (QaPairStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return PENDING;
    }
}
