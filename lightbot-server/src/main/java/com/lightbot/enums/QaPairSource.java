package com.lightbot.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 问答对来源枚举
 *
 * @author finch
 * @since 2026-05-29
 */
@Getter
@AllArgsConstructor
public enum QaPairSource {

    MANUAL("manual", "手动创建"),
    IMPORT("import", "批量导入"),
    AI("ai", "AI生成");

    @EnumValue
    private final String code;

    @JsonValue
    private final String desc;

    public static QaPairSource fromCode(String code) {
        for (QaPairSource source : values()) {
            if (source.code.equals(code)) {
                return source;
            }
        }
        return MANUAL;
    }
}
