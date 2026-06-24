package com.lightbot.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 消息类型
 *
 * @author finch
 * @since 2026-06-24
 */
@Getter
@AllArgsConstructor
public enum MessageType {

    TEXT("text", "文本"),
    MULTIMODAL_IMAGE("multimodal_image", "多模态图片");

    @EnumValue
    private final String code;

    private final String desc;

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static MessageType fromValue(String value) {
        for (MessageType e : values()) {
            if (e.code.equalsIgnoreCase(value) || e.desc.equalsIgnoreCase(value) || e.name().equalsIgnoreCase(value)) {
                return e;
            }
        }
        return TEXT;
    }
}
