package com.lightbot.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
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
    @JsonSerialize(using = ToStringSerializer.class)
    private Long startTime;

    @Schema(description = "耗时（毫秒）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long durationMs;

    @Schema(description = "状态: OK/ERROR")
    private String status;

    @Schema(description = "Span属性（模型、Token、工具参数等）")
    private Map<String, Object> attributes;

    /**
     * 构建 LlmTraceSpan
     *
     * @param spanId       Span唯一标识
     * @param parentSpanId 父SpanID
     * @param name         Span名称
     * @param startTime    开始时间（毫秒时间戳）
     * @param durationMs   耗时（毫秒）
     * @param status       状态: OK/ERROR
     * @param attributes   Span属性
     * @return LlmTraceSpan 实例
     */
    public static LlmTraceSpan of(String spanId, String parentSpanId, String name,
                                   long startTime, long durationMs, String status,
                                   Map<String, Object> attributes) {
        LlmTraceSpan span = new LlmTraceSpan();
        span.setSpanId(spanId);
        span.setParentSpanId(parentSpanId);
        span.setName(name);
        span.setStartTime(startTime);
        span.setDurationMs(durationMs);
        span.setStatus(status);
        span.setAttributes(attributes != null ? attributes : Map.of());
        return span;
    }
}
