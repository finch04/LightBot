package com.lightbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 问答对更新DTO
 *
 * @author finch
 * @since 2026-05-29
 */
@Data
@Schema(description = "问答对更新请求")
public class QaPairUpdateDTO {

    @Schema(description = "问答对ID（由URL Path自动注入，无需传参）")
    private Long id;

    @Schema(description = "问题内容")
    private String question;

    @Schema(description = "标准答案")
    private String answer;
}
