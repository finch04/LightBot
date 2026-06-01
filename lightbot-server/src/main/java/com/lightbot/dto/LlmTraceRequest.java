package com.lightbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * LLM调用链查询请求参数
 *
 * @author finch
 * @since 2026-05-23
 */
@Data
@Schema(description = "LLM调用链查询参数")
public class LlmTraceRequest {

    @Schema(description = "页码", defaultValue = "1")
    private Integer pageNum = 1;

    @Schema(description = "每页条数", defaultValue = "20")
    private Integer pageSize = 20;

    @Schema(description = "会话ID")
    private Long sessionId;

    @Schema(description = "请求ID")
    private String requestId;

    @Schema(description = "AgentID")
    private Long agentId;

    @Schema(description = "状态: running/completed/failed")
    private String status;

    @Schema(description = "开始时间（ISO格式）")
    private String startTime;

    @Schema(description = "结束时间（ISO格式）")
    private String endTime;

    @Schema(description = "来源类型: chat=对话型, workflow=工作流型, 空=全部")
    private String traceSource;
}
