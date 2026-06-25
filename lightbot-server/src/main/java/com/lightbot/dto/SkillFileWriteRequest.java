package com.lightbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Skill 文件写入请求
 *
 * @author finch
 * @since 2026-06-24
 */
@Data
@Schema(description = "Skill 文件写入请求")
public class SkillFileWriteRequest {

    @NotBlank(message = "文件路径不能为空")
    @Schema(description = "相对路径（如 SKILL.md 或 images/logo.png）")
    private String path;

    @Schema(description = "文件内容（新建/更新文件时必填）")
    private String content;

    @Schema(description = "是否为目录（新建时使用）")
    private boolean isDir;
}
