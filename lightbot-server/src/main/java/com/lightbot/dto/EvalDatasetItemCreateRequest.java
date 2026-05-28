package com.lightbot.dto;

import lombok.Data;

import java.util.List;

/**
 * 评测数据项创建请求
 *
 * @author finch
 * @since 2026-05-27
 */
@Data
public class EvalDatasetItemCreateRequest {

    private Long datasetId;

    private Long datasetVersionId;

    private String dataContent;

    private List<String> dataContents;
}
