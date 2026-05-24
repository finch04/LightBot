package com.lightbot.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 默认AI配置DTO
 *
 * @author finch
 * @since 2026-05-24
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "默认AI配置")
public class DefaultAiConfigDTO {

    @Schema(description = "模型提供商ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long providerId;

    @Schema(description = "模型标识")
    private String modelId;
}