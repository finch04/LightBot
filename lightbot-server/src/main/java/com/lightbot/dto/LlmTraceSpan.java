package com.lightbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

/**
 * LLM调用链Span数据
 *
 * @author finch
 * @since 2026-05-23
 */
@Data
@Schema(description = "LLM调用链Span")
public class LlmTraceSpan {

    @Schema(description = "Span唯一标识")
    private String spanId;

    @Schema(description = "父SpanID（用于构建调用树）")
    private String parentSpanId;

    @Schema(description = "Span名称（如 session_resolve、llm_call、tool_execute）")
    private String name;

    @Schema(description = "开始时间（毫秒时间戳）")
    private Long startTime;

    @Schema(description = "耗时（毫秒）")
    private Long durationMs;

    @Schema(description = "状态: OK/ERROR")
    private String status;

    @Schema(description = "Span属性（模型、Token、工具参数等）")
    private Map<String, Object> attributes;
}
