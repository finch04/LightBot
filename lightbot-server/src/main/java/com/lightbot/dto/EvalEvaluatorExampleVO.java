package com.lightbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 示例评估器信息
 *
 * @author finch
 * @since 2026-06-19
 */
@Data
@Builder
@Schema(description = "示例评估器信息")
public class EvalEvaluatorExampleVO {

    @Schema(description = "示例标识key")
    private String key;

    @Schema(description = "评估器名称")
    private String name;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "涉及的评估维度标签")
    private List<String> tags;
}
