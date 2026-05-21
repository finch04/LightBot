package com.lightbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * OCR 健康检查结果
 *
 * @author finch
 * @since 2026-05-21
 */
@Data
@Builder
@Schema(description = "OCR健康检查结果")
public class OcrHealthResult {

    @Schema(description = "是否可用")
    private boolean healthy;

    @Schema(description = "模型路径")
    private String modelPath;

    @Schema(description = "状态消息")
    private String message;

    @Schema(description = "检测模型是否已下载")
    private boolean modelExists;
}
