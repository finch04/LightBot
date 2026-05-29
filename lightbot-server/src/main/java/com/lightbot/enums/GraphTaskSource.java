package com.lightbot.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 图谱数据来源
 *
 * @author finch
 * @since 2026-05-29
 */
@Getter
@AllArgsConstructor
public enum GraphTaskSource {

    AUTO("auto", "自动抽取"),
    IMPORT("import", "批量导入");

    @EnumValue
    private final String code;

    private final String desc;

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static GraphTaskSource fromValue(String value) {
        for (GraphTaskSource e : values()) {
            if (e.code.equalsIgnoreCase(value) || e.name().equalsIgnoreCase(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知的图谱数据来源: " + value);
    }
}
