package com.lightbot.tool.builtin;

import com.lightbot.service.sandbox.SkillStorageService;
import com.lightbot.tool.annotation.SystemTool;
import com.lightbot.tool.annotation.ToolParamMeta;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 内置工具 — 列出技能文件
 * <p>列出指定 Skill 目录下的所有文件。</p>
 *
 * @author finch
 * @since 2026-06-18
 */
@Slf4j
@Component("listSkillFilesTool")
@RequiredArgsConstructor
@SystemTool(displayName = "列出技能文件", description = "列出技能目录下的所有文件", tags = {"技能"})
public class ListSkillFilesTool {

    private final SkillStorageService skillStorageService;

    @Tool(name = "list_skill_files",
          description = "列出指定技能目录下的所有文件。传入技能的 slug 标识，返回该技能目录下的文件列表。")
    public String listFiles(
            @ToolParam(description = "技能的 slug 标识")
            @ToolParamMeta(example = "deep-research") String slug) {
        log.info("[Tool:list_skill_files] 列出文件: slug={}", slug);

        if (slug == null || slug.isBlank()) {
            return "错误：slug 不能为空";
        }

        String normalizedSlug = slug.trim();

        if (!skillStorageService.skillMarkdownExists(normalizedSlug)) {
            return "错误：未找到技能 " + normalizedSlug;
        }

        // 列出 MinIO 中 skills/{slug}/ 下的文件
        try {
            List<String> objects = com.lightbot.util.SandboxPathValidator.normalize("skills/" + normalizedSlug + "/")
                    .equals("skills/" + normalizedSlug + "/")
                    ? skillStorageService.listSkillFiles(normalizedSlug)
                    : List.of();
            if (objects.isEmpty()) {
                return "技能目录为空";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("技能 ").append(normalizedSlug).append(" 的文件列表：\n");
            for (String obj : objects) {
                sb.append("- ").append(obj).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "错误：列举文件失败 - " + e.getMessage();
        }
    }
}
