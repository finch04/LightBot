package com.lightbot.dto;

import jakarta.validation.constraints.Size;
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

    @Size(max = 50, message = "评估器名称不超过50字")
    private String name;

    @Size(max = 50, message = "评估器描述不超过50字")
    private String description;
}
