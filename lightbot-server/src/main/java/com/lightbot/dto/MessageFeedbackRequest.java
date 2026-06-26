package com.lightbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 消息反馈请求
 *
 * @author finch
 * @since 2026-06-26
 */
@Data
@Schema(description = "消息反馈请求")
public class MessageFeedbackRequest {

    @NotBlank(message = "评分不能为空")
    @Schema(description = "评分：like/dislike", requiredMode = Schema.RequiredMode.REQUIRED)
    private String rating;

    @Schema(description = "反馈原因（dislike时可选填写）")
    private String reason;
}
