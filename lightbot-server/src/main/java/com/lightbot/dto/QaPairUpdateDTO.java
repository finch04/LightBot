package com.lightbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
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

    @NotNull(message = "ID不能为空")
    @Schema(description = "问答对ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @Schema(description = "问题内容")
    private String question;

    @Schema(description = "标准答案")
    private String answer;
}
