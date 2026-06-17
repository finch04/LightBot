package com.lightbot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Skill 请求 DTO
 *
 * @author finch
 * @since 2026-05-20
 */
@Data
public class SkillRequest {

    private Long id;

    /** 全局唯一标识（英文-小写-短横线）；新建 global Skill 必填 */
    @Size(max = 50, message = "标识不超过50字")
    private String slug;

    /** 兼容字段：旧的按 Agent 私有 Skill 使用 */
    private Long agentId;

    /** 依赖的 Tool ID 列表（字符串避免精度丢失） */
    private List<String> toolIds;

    /** 依赖的 MCP Server ID 列表 */
    private List<String> mcpServerIds;

    /** 可选模型覆盖（保留字段） */
    private Long modelId;

    @NotBlank(message = "Skill 名称不能为空")
    @Size(max = 50, message = "技能名称不超过50字")
    private String name;

    @Size(max = 50, message = "显示名称不超过50字")
    private String displayName;

    @Size(max = 200, message = "技能描述不超过200字")
    private String description;

    /** 提示词模板（注入主 Agent 系统提示） */
    private String promptTemplate;

    private String config;

    private Integer sortOrder;

    /** 作用域：global / agent，默认 global */
    private String scope;
}
