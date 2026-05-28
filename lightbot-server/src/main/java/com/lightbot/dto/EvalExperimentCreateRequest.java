package com.lightbot.dto;

import lombok.Data;

/**
 * 实验创建请求
 *
 * @author finch
 * @since 2026-05-27
 */
@Data
public class EvalExperimentCreateRequest {

    private String name;

    private String description;

    private Long datasetId;

    private Long datasetVersionId;

    private String datasetVersion;

    private String evaluationObjectConfig;

    private String evaluatorConfig;
}
