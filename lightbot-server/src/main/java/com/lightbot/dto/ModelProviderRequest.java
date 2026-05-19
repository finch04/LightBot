package com.lightbot.dto;

import com.lightbot.enums.ModelProviderType;
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

    private String config;
}
