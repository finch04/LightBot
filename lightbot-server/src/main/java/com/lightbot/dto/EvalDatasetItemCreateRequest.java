package com.lightbot.dto;

import jakarta.validation.constraints.Size;
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

    @Size(max = 2000, message = "数据内容不超过2000字")
    private String dataContent;

    private List<String> dataContents;
}
