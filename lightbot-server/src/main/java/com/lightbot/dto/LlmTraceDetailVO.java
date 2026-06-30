package com.lightbot.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * LLM调用链详情VO（spans解析为对象列表）
 *
 * @author finch
 * @since 2026-05-23
 */
@Data
@Schema(description = "LLM调用链详情VO")
public class LlmTraceDetailVO {

    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "请求ID")
    private String requestId;

    @Schema(description = "会话ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long sessionId;

    @Schema(description = "用户ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    @Schema(description = "AgentID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long agentId;

    @Schema(description = "Agent名称")
    private String agentName;

    @Schema(description = "模型标识")
    private String model;

    @Schema(description = "状态: running/completed/failed")
    private String status;

    @Schema(description = "输入Token数")
    private Integer inputTokens;

    @Schema(description = "输出Token数")
    private Integer outputTokens;

    @Schema(description = "总Token数")
    private Integer totalTokens;

    @Schema(description = "工具调用次数")
    private Integer toolCallCount;

    @Schema(description = "总耗时（毫秒）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long totalDurationMs;

    @Schema(description = "调用链Span列表（已解析为对象）")
    private List<LlmTraceSpan> spans;

    @Schema(description = "AI完整回复内容（模型原始输出，含深度思考标签，不做删改）")
    private String replyContent;

    @Schema(description = "最终展示内容（用户对话页可见正文，已剥离思考标签）")
    private String displayContent;

    @Schema(description = "错误信息")
    private String errorMessage;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
