package com.lightbot.dto;

import lombok.Data;

/**
 * 评估器测试请求
 *
 * @author finch
 * @since 2026-05-27
 */
@Data
public class EvalEvaluatorTestRequest {

    private Long evaluatorVersionId;

    private String variables;
}
