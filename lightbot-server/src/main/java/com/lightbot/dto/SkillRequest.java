package com.lightbot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Skill 请求DTO
 *
 * @author finch
 * @since 2026-05-20
 */
@Data
public class SkillRequest {

    private Long id;

    @NotNull(message = "Agent ID不能为空")
    private Long agentId;

    private Long toolId;

    @NotBlank(message = "Skill名称不能为空")
    private String name;

    private String description;

    private String promptTemplate;

    private String config;

    private Integer sortOrder;
}
