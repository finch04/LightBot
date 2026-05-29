package com.lightbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 问答对创建DTO
 *
 * @author finch
 * @since 2026-05-29
 */
@Data
@Schema(description = "问答对创建请求")
public class QaPairCreateDTO {

    @NotBlank(message = "问题不能为空")
    @Schema(description = "问题内容", requiredMode = Schema.RequiredMode.REQUIRED)
    private String question;

    @NotBlank(message = "答案不能为空")
    @Schema(description = "标准答案", requiredMode = Schema.RequiredMode.REQUIRED)
    private String answer;
}
