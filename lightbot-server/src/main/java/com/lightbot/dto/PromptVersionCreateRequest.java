package com.lightbot.dto;

import lombok.Data;

/**
 * Prompt版本创建请求
 *
 * @author finch
 * @since 2026-05-27
 */
@Data
public class PromptVersionCreateRequest {

    private String promptKey;

    private String version;

    private String versionDesc;

    private String template;

    private String variables;

    private String modelConfig;

    /** 版本状态：pre（草稿）或 release（正式发布） */
    private String status;
}
