package com.lightbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Prompt版本创建请求
 *
 * @author finch
 * @since 2026-05-27
 */
@Data
@Schema(description = "Prompt版本创建请求")
public class PromptVersionCreateRequest {

    @Schema(description = "提示词唯一标识")
    private String promptKey;

    @Schema(description = "版本号")
    private String version;

    @Schema(description = "版本说明")
    private String versionDesc;

    @Schema(description = "模板内容")
    private String template;

    @Schema(description = "变量定义")
    private String variables;

    @Schema(description = "模型配置")
    private String modelConfig;

    @Schema(description = "工具配置")
    private String toolConfig;

    @Schema(description = "版本状态：pre（草稿）或 release（正式发布）")
    private String status;
}
