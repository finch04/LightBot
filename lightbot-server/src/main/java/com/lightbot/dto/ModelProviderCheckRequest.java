package com.lightbot.dto;

import com.lightbot.enums.ModelProviderType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 模型提供商连通性检查请求DTO（表单实时数据）
 *
 * @author finch
 * @since 2026-05-20
 */
@Data
public class ModelProviderCheckRequest {

    @NotNull(message = "类型不能为空")
    private ModelProviderType type;

    private String apiKey;

    private String baseUrl;
}
