package com.lightbot.dto;

import com.lightbot.enums.ModelProviderType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 模型提供商预设 VO
 *
 * @author finch
 * @since 2026-06-27
 */
@Data
@Builder
@Schema(description = "模型提供商预设")
public class ModelProviderPresetVO {

    @Schema(description = "预设标识")
    private String code;

    @Schema(description = "预设名称")
    private String name;

    @Schema(description = "预设说明")
    private String description;

    @Schema(description = "提供商类型")
    private ModelProviderType type;

    @Schema(description = "Logo Data URL")
    private String logo;

    @Schema(description = "基础地址")
    private String baseUrl;

    @Schema(description = "默认模型ID")
    private String defaultModelId;

    @Schema(description = "Chat Completions 请求路径")
    private String completionsPath;

    @Schema(description = "模型列表获取地址")
    private String modelsEndpoint;

    @Schema(description = "额外请求头JSON")
    private String headersJson;

    @Schema(description = "扩展配置JSON")
    private String extraJson;
}
