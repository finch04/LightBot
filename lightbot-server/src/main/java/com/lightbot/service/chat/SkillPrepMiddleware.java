package com.lightbot.service.chat;

import com.lightbot.entity.Agent;
import com.lightbot.entity.Skill;
import com.lightbot.entity.Tool;
import com.lightbot.enums.CommonStatus;
import com.lightbot.service.AgentService;
import com.lightbot.service.SkillService;
import com.lightbot.service.ToolService;
import com.lightbot.service.sandbox.SkillActivationStore;
import com.lightbot.util.JsonIdParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Skill 准备中间件（懒激活版本）
 * <p>Agent 启动时只看到 Skill 名称和描述（摘要），使用 {@code read_skill} 工具读取全文后才激活。
 * 激活状态跨轮次持久化在 Redis 中。</p>
 *
 * @author finch
 * @since 2026-05-28
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillPrepMiddleware implements ChatMiddleware {

    private final AgentService agentService;
    private final SkillService skillService;
    private final ToolService toolService;
    private final SkillActivationStore skillActivationStore;

    @Override
    public Flux<String> execute(ChatContext ctx, ChatMiddlewareChain next) {
        prepare(ctx);
        return next.proceed(ctx);
    }

    /** 同步路径与流式路径共用的 Skill 解析 */
    public void prepare(ChatContext ctx) {
        Agent agent = ctx.getAgent();

        // 1. 从 Redis 加载已激活的 Skill（跨轮次状态）
        Long sessionId = ctx.getSessionId();
        if (sessionId != null) {
            Set<String> activated = skillActivationStore.getActivated(sessionId);
            ctx.setActivatedSkills(activated);
        }

        if (agent == null || agent.getId() == null) {
            ctx.setSkillSystemAppendix("");
            ctx.setSkillExtraToolIds(List.of());
            ctx.setSkillExtraMcpServerIds(List.of());
            ctx.setActiveSkillNames(List.of());
            ctx.setActiveSkillDetails(List.of());
            ctx.setToolNameToSkillDetail(Map.of());
            return;
        }

        List<Long> skillIds = agentService.getSkillIds(agent.getId());
        if (skillIds.isEmpty()) {
            ctx.setSkillSystemAppendix("");
            ctx.setSkillExtraToolIds(List.of());
            ctx.setSkillExtraMcpServerIds(List.of());
            ctx.setActiveSkillNames(List.of());
            ctx.setActiveSkillDetails(List.of());
            ctx.setToolNameToSkillDetail(Map.of());
            return;
        }

        List<Skill> skills = skillService.listByIds(skillIds).stream()
                .filter(s -> s != null && s.getStatus() == CommonStatus.ACTIVE)
                .sorted((a, b) -> {
                    int orderA = a.getSortOrder() != null ? a.getSortOrder() : 0;
                    int orderB = b.getSortOrder() != null ? b.getSortOrder() : 0;
                    return Integer.compare(orderA, orderB);
                })
                .toList();

        if (skills.isEmpty()) {
            ctx.setSkillSystemAppendix("");
            ctx.setSkillExtraToolIds(List.of());
            ctx.setSkillExtraMcpServerIds(List.of());
            ctx.setActiveSkillNames(List.of());
            ctx.setActiveSkillDetails(List.of());
            ctx.setToolNameToSkillDetail(Map.of());
            return;
        }

        // 2. 构建 Skill 摘要（不注入全量 promptTemplate）
        StringBuilder summary = new StringBuilder("\n\n## 可用技能（按需启用）\n");
        summary.append("以下技能已绑定到当前 Agent，使用 `read_skill` 工具读取完整指令后生效：\n");

        List<String> activeNames = new ArrayList<>();
        List<Map<String, Object>> activeDetails = new ArrayList<>();

        for (Skill skill : skills) {
            activeNames.add(skill.getName());
            Map<String, Object> detail = new HashMap<>();
            detail.put("name", skill.getName());
            detail.put("displayName", skill.getDisplayName() != null ? skill.getDisplayName() : skill.getName());
            detail.put("slug", skill.getSlug());
            detail.put("builtin", Integer.valueOf(1).equals(skill.getIsBuiltin()));
            detail.put("version", skill.getVersion());
            activeDetails.add(detail);

            // 构建摘要行
            summary.append("- **").append(skill.getName()).append("**");
            if (skill.getVersion() != null) {
                summary.append(" (v").append(skill.getVersion()).append(")");
            }
            summary.append(": ").append(skill.getDescription() != null ? skill.getDescription() : "");
            // 展示依赖信息
            List<String> toolNames = resolveToolNames(skill.getToolIds());
            if (!toolNames.isEmpty()) {
                summary.append("。依赖: ").append(String.join(", ", toolNames));
            }
            summary.append("\n");
        }

        // 3. 构建 toolName → Skill 详情映射（用于 trace）
        Map<String, Map<String, Object>> toolNameToSkill = new HashMap<>();
        Set<Long> allSkillToolIds = new LinkedHashSet<>();
        for (Skill skill : skills) {
            allSkillToolIds.addAll(JsonIdParser.parseIds(skill.getToolIds()));
        }
        if (!allSkillToolIds.isEmpty()) {
            try {
                List<Tool> tools = toolService.listByIds(new ArrayList<>(allSkillToolIds));
                Map<Long, String> toolIdToName = new HashMap<>();
                for (Tool t : tools) {
                    if (t != null && t.getName() != null) {
                        toolIdToName.put(t.getId(), t.getName());
                    }
                }
                for (int i = 0; i < skills.size(); i++) {
                    Skill skill = skills.get(i);
                    Map<String, Object> detail = activeDetails.get(i);
                    List<Long> sToolIds = JsonIdParser.parseIds(skill.getToolIds());
                    for (Long tid : sToolIds) {
                        String tName = toolIdToName.get(tid);
                        if (tName != null && !toolNameToSkill.containsKey(tName)) {
                            toolNameToSkill.put(tName, detail);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("[SkillPrep] 构建 toolName→Skill 映射失败: {}", e.getMessage());
            }
        }

        ctx.setSkillSystemAppendix(summary.toString());
        // 懒激活模式：不预设 extraToolIds / extraMcpServerIds，由 ToolPrepMiddleware 按需注入
        ctx.setSkillExtraToolIds(List.of());
        ctx.setSkillExtraMcpServerIds(List.of());
        ctx.setActiveSkillNames(activeNames);
        ctx.setActiveSkillDetails(activeDetails);
        ctx.setToolNameToSkillDetail(toolNameToSkill);

        log.info("[SkillPrep] agentId={}, skills={}, activated={}",
                agent.getId(), activeNames, ctx.getActivatedSkills());
    }

    /** 解析 toolIds JSON 为工具名称列表 */
    private List<String> resolveToolNames(String toolIdsJson) {
        List<Long> ids = JsonIdParser.parseIds(toolIdsJson);
        if (ids.isEmpty()) {
            return List.of();
        }
        try {
            List<Tool> tools = toolService.listByIds(ids);
            return tools.stream()
                    .filter(t -> t != null && t.getName() != null)
                    .map(Tool::getName)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return List.of();
        }
    }
}
