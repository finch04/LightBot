package com.lightbot.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 实验创建请求
 *
 * @author finch
 * @since 2026-05-27
 */
@Data
public class EvalExperimentCreateRequest {

    @Size(max = 30, message = "实验名称不超过30字")
    private String name;

    @Size(max = 50, message = "实验描述不超过50字")
    private String description;

    private Long datasetId;

    private Long datasetVersionId;

    private String datasetVersion;

    private String evaluationObjectConfig;

    private String evaluatorConfig;
}
