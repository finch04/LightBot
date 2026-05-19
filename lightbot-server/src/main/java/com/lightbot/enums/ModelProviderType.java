package com.lightbot.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
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
public enum ModelProviderType {

    OPENAI("openai", "OpenAI"),
    DASHSCOPE("dashscope", "通义千问"),
    DEEPSEEK("deepseek", "DeepSeek"),
    OLLAMA("ollama", "Ollama");

    @EnumValue
    private final String code;

    @JsonValue
    private final String desc;
}
