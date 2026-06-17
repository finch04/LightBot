package com.lightbot.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 评测集创建请求
 *
 * @author finch
 * @since 2026-05-27
 */
@Data
public class EvalDatasetCreateRequest {

    private Long id;

    @Size(max = 50, message = "数据集名称不超过50字")
    private String name;

    @Size(max = 200, message = "数据集描述不超过200字")
    private String description;

    private String columnsConfig;
}
