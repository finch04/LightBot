package com.lightbot.dto;

import lombok.Data;

/**
 * 评估器创建请求
 *
 * @author finch
 * @since 2026-05-27
 */
@Data
public class EvalEvaluatorCreateRequest {

    private Long id;

    private String name;

    private String description;
}
