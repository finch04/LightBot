package com.lightbot.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息反馈列表VO（含消息内容摘要）
 *
 * @author finch
 * @since 2026-06-26
 */
@Data
@Schema(description = "消息反馈列表VO")
public class MessageFeedbackVO {

    @Schema(description = "反馈ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "消息ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long messageId;

    @Schema(description = "评分：like/dislike")
    private String rating;

    @Schema(description = "反馈原因")
    private String reason;

    @Schema(description = "消息内容摘要")
    private String messageContent;

    @Schema(description = "会话ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long sessionId;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
