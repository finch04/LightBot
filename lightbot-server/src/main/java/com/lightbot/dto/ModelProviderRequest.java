package com.lightbot.dto;

import com.lightbot.enums.ModelProviderType;
import com.lightbot.validation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 模型提供商请求DTO
 *
 * @author finch
 * @since 2026-05-19
 */
@Data
public class ModelProviderRequest {

    private Long id;

    @NotNull(message = "名称不能为空")
    private String name;

    @NotNull(message = "类型不能为空")
    private ModelProviderType type;

    private String apiKey;

    private String baseUrl;

    /** 模型列表获取地址（为空时使用默认地址） */
    private String modelsEndpoint;

    /** 额外请求头（JSON格式） */
    @JsonFormat(message = "额外请求头必须为合法JSON格式")
    private String headersJson;

    /** 扩展配置（JSON格式） */
    @JsonFormat(message = "扩展配置必须为合法JSON格式")
    private String extraJson;

    /** 模型参数配置 */
    private String config;
}
