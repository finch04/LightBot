package com.lightbot.tool.builtin;

import com.lightbot.service.SkillService;
import com.lightbot.service.sandbox.SkillActivationStore;
import com.lightbot.service.sandbox.SkillStorageService;
import com.lightbot.tool.annotation.SystemTool;
import com.lightbot.tool.annotation.ToolParamMeta;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 内置工具 — 读取技能
 * <p>读取指定 Skill 的 SKILL.md 全文，触发懒激活。
 * 调用后该 Skill 的依赖工具将在下一轮对话中自动注入。</p>
 *
 * @author finch
 * @since 2026-06-18
 */
@Slf4j
@Component("readSkillTool")
@RequiredArgsConstructor
@SystemTool(displayName = "读取技能", description = "读取技能的完整指令内容（SKILL.md）", tags = {"技能"})
public class ReadSkillTool {

    private final SkillStorageService skillStorageService;
    private final SkillService skillService;
    private final SkillActivationStore skillActivationStore;

    @Tool(name = "read_skill",
          description = "读取指定技能的完整指令内容（SKILL.md）。传入技能的 slug 标识，返回该技能的完整提示词和使用说明。调用此工具会自动激活该技能及其依赖。")
    public String readSkill(
            @ToolParam(description = "技能的 slug 标识，如 deep-research")
            @ToolParamMeta(example = "deep-research") String slug,
            ToolContext toolContext) {
        log.info("[Tool:read_skill] 读取技能: slug={}", slug);

        // 1. 校验 slug
        if (slug == null || slug.isBlank()) {
            return "错误：slug 不能为空";
        }

        // 2. 校验 Skill 是否存在且启用
        var skill = skillService.getBySlug(slug.trim());
        if (skill == null) {
            return "错误：未找到技能 " + slug;
        }

        // 3. 读取 SKILL.md
        String content;
        try {
            content = skillStorageService.getSkillMarkdown(slug.trim());
        } catch (Exception e) {
            return "错误：读取技能文件失败 - " + e.getMessage();
        }

        // 4. 标记激活
        try {
            Long sessionId = resolveSessionId(toolContext);
            if (sessionId != null) {
                skillActivationStore.activate(sessionId, slug.trim());
                log.info("[Tool:read_skill] 技能已激活: slug={}, sessionId={}", slug, sessionId);
            }
        } catch (Exception e) {
            log.warn("[Tool:read_skill] 激活状态保存失败: {}", e.getMessage());
        }

        return content;
    }

    private Long resolveSessionId(ToolContext context) {
        if (context == null || context.getContext() == null) {
            return null;
        }
        Object val = context.getContext().get("sessionId");
        if (val instanceof Long l) {
            return l;
        }
        if (val instanceof Number n) {
            return n.longValue();
        }
        if (val instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }
}
