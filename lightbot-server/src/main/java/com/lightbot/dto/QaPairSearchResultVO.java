package com.lightbot.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 问答对检索结果VO
 *
 * @author finch
 * @since 2026-05-29
 */
@Data
@Schema(description = "问答对检索结果")
public class QaPairSearchResultVO {

    @Schema(description = "问答对ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "问题")
    private String question;

    @Schema(description = "答案")
    private String answer;

    @Schema(description = "相似度分数")
    private Double score;
}
