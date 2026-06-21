package com.lightbot.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 模型类型
 *
 * @author finch
 * @since 2026-05-20
 */
@Getter
@AllArgsConstructor
public enum ModelType implements EnumDisplay {

    LLM("llm", "对话模型"),
    EMBEDDING("embedding", "嵌入模型"),
    RERANK("rerank", "重排模型"),
    TTS("tts", "语音合成"),
    STT("stt", "语音识别");

    @EnumValue
    private final String code;

    private final String desc;

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static ModelType fromValue(String value) {
        for (ModelType type : values()) {
            if (type.code.equalsIgnoreCase(value) || type.desc.equalsIgnoreCase(value)
                    || type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的模型类型: " + value);
    }
}
