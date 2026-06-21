package com.lightbot.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 模型提供商类型
 *
 * @author finch
 * @since 2026-05-19
 */
@Getter
@AllArgsConstructor
public enum ModelProviderType implements EnumDisplay {

    OPENAI("OPENAI", "OpenAI"),
    DASHSCOPE("DASHSCOPE", "通义千问"),
    DEEPSEEK("DEEPSEEK", "DeepSeek"),
    OLLAMA("OLLAMA", "Ollama"),
    MIMO("MIMO", "小米MiMo");

    @EnumValue
    private final String code;

    private final String desc;

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static ModelProviderType fromValue(String value) {
        for (ModelProviderType type : values()) {
            if (type.code.equalsIgnoreCase(value) || type.desc.equalsIgnoreCase(value)
                    || type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的模型提供商类型: " + value);
    }
}
