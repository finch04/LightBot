package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * RAG 检索反馈实体
 *
 * @author finch
 * @since 2026-06-25
 */
@Data
@TableName("rag_feedback")
@Schema(description = "RAG检索反馈")
public class RagFeedback {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("message_id")
    @Schema(description = "关联消息ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long messageId;

    @TableField("user_id")
    @Schema(description = "反馈用户ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    @TableField("chunk_id")
    @Schema(description = "文档分块ID（chunk类型）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long chunkId;

    @TableField("qa_pair_id")
    @Schema(description = "问答对ID（qa_pair类型）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long qaPairId;

    @TableField("source_type")
    @Schema(description = "来源类型：chunk / qa_pair")
    private String sourceType;

    @TableField("feedback_type")
    @Schema(description = "反馈类型：positive / negative")
    private String feedbackType;

    @TableField("create_time")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
