package com.lightbot.dto;

import lombok.Data;

import java.util.List;

/**
 * Skill ZIP 导入预览 DTO
 *
 * @author finch
 * @since 2026-06-18
 */
@Data
public class SkillImportPreview {

    /** 草稿 ID（用于阶段二确认提交） */
    private String draftId;

    /** Skill 标识 */
    private String slug;

    /** Skill 名称 */
    private String name;

    /** 描述 */
    private String description;

    /** 语义版本号 */
    private String version;

    /** 依赖的工具名称列表 */
    private List<String> toolDependencies;

    /** 依赖的其他 Skill slug 列表 */
    private List<String> skillDependencies;

    /** ZIP 中包含的文件名列表 */
    private List<String> fileNames;
}
