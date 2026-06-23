package com.lightbot.tool.builtin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.entity.Skill;
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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 内置工具 — 读取技能
 * <p>读取指定 Skill 的 SKILL.md 全文，触发懒激活。</p>
 *
 * @author finch
 * @since 2026-06-18
 */
@Slf4j
@Component("readSkillTool")
@RequiredArgsConstructor
@SystemTool(displayName = "读取技能", description = "读取技能的完整指令内容（SKILL.md）", tags = {"技能"},
        outputExample = "{\"slug\":\"deep-research\",\"displayName\":\"深度研究\",\"content\":\"# 深度研究\\n\\n你是一个深度研究助手...\",\"activated\":true}",
        outputSchema = "{\"type\":\"object\",\"properties\":{\"slug\":{\"type\":\"string\",\"description\":\"技能slug标识\"},\"displayName\":{\"type\":\"string\",\"description\":\"技能显示名称\"},\"content\":{\"type\":\"string\",\"description\":\"SKILL.md完整内容\"},\"activated\":{\"type\":\"boolean\",\"description\":\"是否已激活成功\"}}}}")
public class ReadSkillTool {

    private final SkillStorageService skillStorageService;
    private final SkillService skillService;
    private final SkillActivationStore skillActivationStore;
    private final ObjectMapper objectMapper;

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
        String normalizedSlug = slug.trim();
        var skill = skillService.getBySlug(normalizedSlug);
        // AI 可能将连字符误传为下划线，尝试容错
        if (skill == null && normalizedSlug.contains("_")) {
            skill = skillService.getBySlug(normalizedSlug.replace("_", "-"));
        }
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
        boolean activated = false;
        try {
            Long sessionId = resolveSessionId(toolContext);
            if (sessionId != null) {
                skillActivationStore.activate(sessionId, slug.trim());
                activated = true;
                log.info("[Tool:read_skill] 技能已激活: slug={}, sessionId={}", slug, sessionId);
            }
        } catch (Exception e) {
            log.warn("[Tool:read_skill] 激活状态保存失败: {}", e.getMessage());
        }

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("slug", slug.trim());
        output.put("displayName", skill.getDisplayName() != null ? skill.getDisplayName() : skill.getName());
        output.put("content", content);
        output.put("activated", activated);
        try {
            return objectMapper.writeValueAsString(output);
        } catch (Exception e) {
            return content;
        }
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
