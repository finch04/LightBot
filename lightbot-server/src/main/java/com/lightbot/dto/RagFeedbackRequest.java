package com.lightbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * RAG 反馈请求 DTO
 *
 * @author finch
 * @since 2026-06-25
 */
@Data
@Schema(description = "RAG反馈请求")
public class RagFeedbackRequest {

    @NotNull(message = "消息ID不能为空")
    @Schema(description = "关联的消息ID")
    private Long messageId;

    @Schema(description = "文档分块ID（chunk类型时必填）")
    private Long chunkId;

    @Schema(description = "问答对ID（qa_pair类型时必填）")
    private Long qaPairId;

    @NotBlank(message = "来源类型不能为空")
    @Schema(description = "来源类型：chunk / qa_pair")
    private String sourceType;

    @NotBlank(message = "反馈类型不能为空")
    @Schema(description = "反馈类型：positive / negative")
    private String feedbackType;
}
