package com.lightbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Prompt构建模板创建请求
 *
 * @author finch
 * @since 2026-05-28
 */
@Data
@Schema(description = "Prompt构建模板创建请求")
public class PromptTemplateCreateRequest {

    @Schema(description = "模板唯一标识")
    private String promptTemplateKey;

    @Schema(description = "模板描述")
    private String templateDesc;

    @Schema(description = "模板内容")
    private String template;

    @Schema(description = "变量定义（逗号分隔）")
    private String variables;

    @Schema(description = "模型配置（JSON格式）")
    private String modelConfig;

    @Schema(description = "标签")
    private String tags;
}