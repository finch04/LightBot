package com.lightbot.tool.builtin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.constant.ToolResultPrefixes;
import com.lightbot.dto.SkillImportPreview;
import com.lightbot.entity.Skill;
import com.lightbot.service.SkillService;
import com.lightbot.service.sandbox.GitHubSkillService;
import com.lightbot.service.sandbox.SkillActivationStore;
import com.lightbot.tool.ToolEventEmitter;
import com.lightbot.tool.annotation.SystemTool;
import com.lightbot.tool.annotation.ToolParamMeta;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内置工具 — 技能安装
 * <p>从 GitHub 仓库安装 Skill，安装后自动激活，下一轮对话即可使用。</p>
 * <p>简化版：仅支持 GitHub 仓库安装（owner/repo 格式），
 * 不支持 npx 全局搜索、ModelScope、沙盒路径安装。</p>
 *
 * @author finch
 * @since 2026-06-25
 */
@Slf4j
@Component("installSkillTool")
@RequiredArgsConstructor
@SystemTool(displayName = "技能安装", description = "从 GitHub 仓库安装技能，安装后自动激活",
        tags = {"技能", "安装"},
        outputExample = "{\"success\":true,\"installed\":[{\"slug\":\"deep-research\",\"displayName\":\"深度研究\",\"activated\":true}],\"total\":1}",
        outputSchema = "{\"type\":\"object\",\"properties\":{\"success\":{\"type\":\"boolean\"},\"installed\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{\"slug\":{\"type\":\"string\"},\"displayName\":{\"type\":\"string\"},\"activated\":{\"type\":\"boolean\"}}}},\"errors\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},\"total\":{\"type\":\"integer\"}}}")
public class InstallSkillTool {

    private final GitHubSkillService gitHubSkillService;
    private final SkillService skillService;
    private final SkillActivationStore skillActivationStore;
    private final ObjectMapper objectMapper;

    @Tool(name = "install_skill",
          description = "从 GitHub 仓库安装技能。传入仓库地址（如 owner/repo 或完整 GitHub URL），" +
                  "可选指定要安装的技能名称列表（不指定则安装仓库中所有技能）。" +
                  "安装完成后自动激活，下一轮对话即可使用新技能。")
    public String installSkill(
            @ToolParam(description = "GitHub 仓库地址，支持格式：owner/repo、github.com/owner/repo、完整 URL，可加 @branch 指定分支")
            @ToolParamMeta(example = "anthropics/skills") String source,
            @ToolParam(description = "要安装的技能名称列表（可选，不传则安装仓库中所有技能）")
            @ToolParamMeta(example = "[\"deep-research\"]", required = false) List<String> skillNames,
            ToolContext toolContext) {
        log.info("[Tool:install_skill] 安装技能: source={}, skillNames={}", source, skillNames);

        // 1. 解析仓库地址
        String[] parsed;
        try {
            parsed = gitHubSkillService.parseSource(source);
        } catch (Exception e) {
            return ToolResultPrefixes.failureJson("仓库地址解析失败: " + e.getMessage());
        }
        String owner = parsed[0];
        String repo = parsed[1];
        String branch = parsed[2];

        ToolEventEmitter.emit("正在从 " + owner + "/" + repo + " 下载技能...");

        // 2. 远程安装准备（下载 ZIP、暂存草稿），失败时降级全局搜索
        List<SkillImportPreview> previews;
        try {
            previews = gitHubSkillService.prepareRemoteInstall(owner, repo, branch,
                    skillNames != null && !skillNames.isEmpty() ? skillNames : List.of());
        } catch (Exception e) {
            log.warn("[Tool:install_skill] GitHub 下载失败，降级全局搜索: source={}, error={}", source, e.getMessage());
            return fallbackGlobalSearch(source, skillNames, e.getMessage());
        }

        if (previews.isEmpty()) {
            return ToolResultPrefixes.failureJson("未找到可安装的技能");
        }

        // 3. 逐个提交安装 + 激活
        Long sessionId = resolveSessionId(toolContext);
        List<Map<String, Object>> installed = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (SkillImportPreview preview : previews) {
            try {
                ToolEventEmitter.emit("正在安装技能: " + preview.getSlug() + "...");

                // 提交安装（含 slug 冲突处理）
                Skill skill = skillService.commitRemoteSkill(preview.getDraftId(), preview.getSlug());

                // 自动激活
                boolean activated = false;
                if (sessionId != null) {
                    try {
                        skillActivationStore.activate(sessionId, skill.getSlug());
                        activated = true;
                    } catch (Exception e) {
                        log.warn("[Tool:install_skill] 激活失败: slug={}, error={}", skill.getSlug(), e.getMessage());
                    }
                }

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("slug", skill.getSlug());
                item.put("displayName", skill.getDisplayName() != null ? skill.getDisplayName() : skill.getName());
                item.put("activated", activated);
                installed.add(item);

                log.info("[Tool:install_skill] 技能安装成功: slug={}, activated={}", skill.getSlug(), activated);
            } catch (Exception e) {
                log.error("[Tool:install_skill] 技能安装失败: slug={}, error={}", preview.getSlug(), e.getMessage());
                errors.add(preview.getSlug() + ": " + e.getMessage());
            }
        }

        // 4. 清理草稿（如果有未处理的）
        try {
            String draftId = previews.get(0).getDraftId();
            skillService.cleanupDraft(draftId);
        } catch (Exception ignored) {
        }

        // 5. 构建结果
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", !installed.isEmpty());
        result.put("installed", installed);
        if (!errors.isEmpty()) {
            result.put("errors", errors);
        }
        result.put("total", installed.size());

        String summary = installed.isEmpty() ? "安装失败" :
                "成功安装 " + installed.size() + " 个技能，下一轮对话即可使用";
        result.put("message", summary);

        ToolEventEmitter.emit(summary);

        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            return ToolResultPrefixes.failureJson("序列化失败: " + e.getMessage());
        }
    }

    /**
     * GitHub 下载失败时降级全局搜索，返回搜索结果供 AI 推荐替代方案
     */
    private String fallbackGlobalSearch(String source, List<String> skillNames, String originalError) {
        String keyword = (skillNames != null && !skillNames.isEmpty()) ? skillNames.get(0) : source;
        try {
            List<Map<String, String>> searchResults = gitHubSkillService.searchRemoteSkills(keyword);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", false);
            result.put("error", "GitHub 仓库下载失败: " + originalError);
            if (!searchResults.isEmpty()) {
                result.put("message", "已降级为全局搜索，以下是「" + keyword + "」的搜索结果，可从中选择安装");
                result.put("searchResults", searchResults);
                result.put("total", searchResults.size());
            } else {
                result.put("message", "GitHub 下载失败且全局搜索无结果，请检查仓库地址是否正确");
            }
            ToolEventEmitter.emit("GitHub 下载失败，已降级为全局搜索");
            return objectMapper.writeValueAsString(result);
        } catch (Exception searchEx) {
            log.warn("[Tool:install_skill] 全局搜索也失败: keyword={}, error={}", keyword, searchEx.getMessage());
            return ToolResultPrefixes.failureJson("下载技能失败: " + originalError + "；全局搜索也未能找到替代结果");
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
