package com.lightbot.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 问答对返回VO
 *
 * @author finch
 * @since 2026-05-29
 */
@Data
@Schema(description = "问答对信息")
public class QaPairVO {

    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "知识库ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long knowledgeId;

    @Schema(description = "问题内容")
    private String question;

    @Schema(description = "标准答案")
    private String answer;

    @Schema(description = "来源")
    private String source;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "问题token数量")
    private Integer tokenCount;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
