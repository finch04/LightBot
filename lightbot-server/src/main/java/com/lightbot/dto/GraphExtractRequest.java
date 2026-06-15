package com.lightbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 图谱抽取请求
 *
 * @author finch
 * @since 2026-06-15
 */
@Data
@Schema(description = "图谱抽取请求")
public class GraphExtractRequest {

    @Schema(description = "文档ID列表（为空表示全量抽取）")
    private List<Long> documentIds;

    @Schema(description = "模型提供商ID（为空使用系统默认）")
    private Long providerId;

    @Schema(description = "指定模型ID（为空使用 provider 默认模型）")
    private String modelId;

    @Schema(description = "Schema 约束文本，拼接到抽取 Prompt 尾部")
    private String schema;

    @Schema(description = "并发队列数（1-1000，默认50）")
    private Integer concurrency;

    @Schema(description = "模型参数 JSON（如 temperature、maxTokens）")
    private Map<String, Object> modelParams;
}
