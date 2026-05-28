package com.lightbot.dto;

import lombok.Data;

/**
 * Prompt 创建请求
 *
 * @author finch
 * @since 2026-05-27
 */
@Data
public class PromptCreateRequest {

    private String promptKey;

    private String description;

    private String tags;
}
