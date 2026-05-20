package com.lightbot.model;

import com.lightbot.enums.ModelType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 联网拉取的模型信息
 *
 * @author finch
 * @since 2026-05-20
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "拉取的模型信息")
public class FetchedModel {

    @Schema(description = "模型标识")
    private String modelId;

    @Schema(description = "模型类型")
    private ModelType type;

    /**
     * 根据模型ID自动推断类型
     */
    public static FetchedModel of(String modelId) {
        return new FetchedModel(modelId, inferType(modelId));
    }

    private static ModelType inferType(String modelId) {
        String id = modelId.toLowerCase();
        if (id.contains("embedding") || id.contains("embed")) return ModelType.EMBEDDING;
        if (id.contains("rerank")) return ModelType.RERANK;
        if (id.contains("tts")) return ModelType.TTS;
        if (id.contains("stt") || id.contains("whisper")) return ModelType.STT;
        return ModelType.LLM;
    }
}
