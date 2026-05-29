package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lightbot.enums.QaPairSource;
import com.lightbot.enums.QaPairStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库问答对
 *
 * @author finch
 * @since 2026-05-29
 */
@Data
@TableName("qa_pair")
@Schema(description = "知识库问答对")
public class QaPair {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("knowledge_id")
    @Schema(description = "知识库ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long knowledgeId;

    @TableField("question")
    @Schema(description = "问题内容")
    private String question;

    @TableField("answer")
    @Schema(description = "标准答案")
    private String answer;

    @TableField("source")
    @Schema(description = "来源")
    private QaPairSource source;

    @TableField("status")
    @Schema(description = "状态")
    private QaPairStatus status;

    @TableField("token_count")
    @Schema(description = "问题token数量")
    private Integer tokenCount;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @TableField("deleted")
    @TableLogic
    @Schema(description = "逻辑删除标记")
    private Integer deleted;
}
