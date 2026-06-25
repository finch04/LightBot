package com.lightbot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * Skill 文件树节点
 *
 * @author finch
 * @since 2026-06-24
 */
@Data
@Schema(description = "Skill 文件树节点")
public class SkillFileTreeNode {

    @Schema(description = "文件/目录名")
    private String name;

    @Schema(description = "相对路径（如 SKILL.md 或 images/logo.png）")
    private String path;

    @JsonProperty("isDir")
    @Schema(description = "是否为目录")
    private boolean isDir;

    @Schema(description = "子节点（仅目录有值）")
    private List<SkillFileTreeNode> children;
}
