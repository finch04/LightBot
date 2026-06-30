package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lightbot.handler.JsonbTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.LocalDateTime;

/**
 * LLM调用链追踪表
 *
 * @author finch
 * @since 2026-05-23
 */
@Data
@TableName("llm_trace")
@Schema(description = "LLM调用链追踪表")
public class LlmTrace {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("request_id")
    @Schema(description = "请求ID（唯一标识一次AI对话）")
    private String requestId;

    @TableField("session_id")
    @Schema(description = "会话ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long sessionId;

    @TableField("user_id")
    @Schema(description = "用户ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    @TableField("agent_id")
    @Schema(description = "AgentID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long agentId;

    @TableField("agent_name")
    @Schema(description = "Agent名称")
    private String agentName;

    @TableField("model")
    @Schema(description = "模型标识")
    private String model;

    @TableField("trace_source")
    @Schema(description = "来源：chat=用户对话，其它辅助能力不写入")
    private String traceSource;

    @TableField("status")
    @Schema(description = "状态: running/completed/failed")
    private String status;

    @TableField("input_tokens")
    @Schema(description = "输入Token数")
    private Integer inputTokens;

    @TableField("output_tokens")
    @Schema(description = "输出Token数")
    private Integer outputTokens;

    @TableField("total_tokens")
    @Schema(description = "总Token数")
    private Integer totalTokens;

    @TableField("tool_call_count")
    @Schema(description = "工具调用次数")
    private Integer toolCallCount;

    @TableField("total_duration_ms")
    @Schema(description = "总耗时（毫秒）")
    private Long totalDurationMs;

    @TableField(value = "spans", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Schema(description = "调用链Span列表（JSONB）")
    private String spans;

    @TableField("reply_content")
    @Schema(description = "AI完整回复内容（模型原始输出，含深度思考标签，不做删改）")
    private String replyContent;

    @TableField("display_content")
    @Schema(description = "最终展示内容（用户对话页可见正文，已剥离思考标签）")
    private String displayContent;

    @TableField("error_message")
    @Schema(description = "错误信息")
    private String errorMessage;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
