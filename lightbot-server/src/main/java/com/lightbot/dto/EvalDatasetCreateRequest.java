package com.lightbot.dto;

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

    private String name;

    private String description;

    private String columnsConfig;
}
