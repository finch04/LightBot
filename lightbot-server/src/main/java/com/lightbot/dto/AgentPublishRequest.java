package com.lightbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Agent 发布请求（发布说明选填）
 */
@Data
@Schema(description = "Agent发布请求")
public class AgentPublishRequest {

    @Size(max = 50, message = "发布说明不能超过50字")
    @Schema(description = "发布说明（选填，最多50字）")
    private String description;
}
