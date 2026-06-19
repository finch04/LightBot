package com.lightbot.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Prompt 创建请求
 *
 * @author finch
 * @since 2026-05-27
 */
@Data
public class PromptCreateRequest {

    @Size(max = 100, message = "Prompt Key不超过100字")
    private String promptKey;

    @Size(max = 50, message = "描述不超过50字")
    private String description;

    private String tags;
}
