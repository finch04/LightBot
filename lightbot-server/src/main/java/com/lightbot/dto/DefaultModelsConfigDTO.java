package com.lightbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 默认模型配置汇总DTO
 *
 * @author finch
 * @since 2026-06-19
 */
@Data
@Schema(description = "默认模型配置汇总")
public class DefaultModelsConfigDTO {

    @Schema(description = "默认对话模型")
    private DefaultAiConfigDTO chat;

    @Schema(description = "默认向量模型")
    private DefaultAiConfigDTO embedding;

    @Schema(description = "默认TTS模型")
    private DefaultAiConfigDTO tts;

    @Schema(description = "默认重排模型")
    private DefaultAiConfigDTO rerank;
}
