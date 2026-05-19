package com.lightbot.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 通用状态（模型提供商、知识库、Tool、Skill 共用）
 *
 * @author finch
 * @since 2026-05-19
 */
@Getter
@AllArgsConstructor
public enum CommonStatus {

    ACTIVE("active", "启用"),
    DISABLED("disabled", "禁用");

    @EnumValue
    private final String code;

    @JsonValue
    private final String desc;
}
