package com.lightbot.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 分块向量化状态枚举
 *
 * @author finch
 * @since 2026-05-20
 */
@Getter
@AllArgsConstructor
public enum ChunkStatus {

    CHUNKED("chunked", "已分块"),
    VECTORIZING("vectorizing", "向量化中"),
    VECTORIZED("vectorized", "已向量化"),
    FAILED("failed", "向量化失败");

    @EnumValue
    @JsonValue
    private final String code;
    private final String desc;
}
