package com.lightbot.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * SKILL.md frontmatter 解析后的结构化数据
 *
 * @author finch
 * @since 2026-06-18
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillMetadata {

    /** Skill 唯一标识 */
    private String slug;

    /** Skill 名称（英文短名，对模型可读） */
    private String name;

    /** 描述 */
    private String description;

    /** 语义版本号 */
    private String version;

    /** 依赖的工具名称列表（不是 ID） */
    private List<String> toolDependencies;

    /** 依赖的 MCP Server 名称列表 */
    private List<String> mcpDependencies;

    /** 依赖的其他 Skill slug 列表 */
    private List<String> skillDependencies;

    /** frontmatter 之后的 markdown 正文（作为 promptTemplate） */
    private String promptTemplate;
}
