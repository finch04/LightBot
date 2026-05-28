package com.lightbot.service.chat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.entity.Agent;
import com.lightbot.entity.Skill;
import com.lightbot.enums.CommonStatus;
import com.lightbot.service.AgentService;
import com.lightbot.service.SkillService;
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

/**
 * Skill 准备中间件
 * <p>对标 Yuxi 的 SkillMiddleware：把 Agent 绑定的 Skill 解析为
 * 「系统提示词追加块 + 额外 Tool/MCP ID」，写入 {@link ChatContext}。</p>
 * <p>本中间件不直接调用模型，只生成上下文供后续的 MessageMiddleware / ToolPrepMiddleware 使用。</p>
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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public Flux<String> execute(ChatContext ctx, ChatMiddlewareChain next) {
        prepare(ctx);
        return next.proceed(ctx);
    }

    /** 同步路径与流式路径共用的 Skill 解析 */
    public void prepare(ChatContext ctx) {
        Agent agent = ctx.getAgent();
        if (agent == null || agent.getId() == null) {
            ctx.setSkillSystemAppendix("");
            ctx.setSkillExtraToolIds(List.of());
            ctx.setSkillExtraMcpServerIds(List.of());
            ctx.setActiveSkillNames(List.of());
            ctx.setActiveSkillDetails(List.of());
            return;
        }

        List<Long> skillIds = agentService.getSkillIds(agent.getId());
        if (skillIds.isEmpty()) {
            ctx.setSkillSystemAppendix("");
            ctx.setSkillExtraToolIds(List.of());
            ctx.setSkillExtraMcpServerIds(List.of());
            ctx.setActiveSkillNames(List.of());
            ctx.setActiveSkillDetails(List.of());
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
            return;
        }

        StringBuilder prompt = new StringBuilder("\n\n## 可用技能（按需启用）\n");
        prompt.append("以下技能由 Agent 显式启用，请根据其「触发条件」判断是否使用，使用时严格遵守对应的执行规则：\n");

        Set<Long> extraToolIds = new LinkedHashSet<>();
        Set<Long> extraMcpServerIds = new LinkedHashSet<>();
        List<String> activeNames = new ArrayList<>();
        List<Map<String, Object>> activeDetails = new ArrayList<>();

        for (Skill skill : skills) {
            activeNames.add(skill.getName());
            Map<String, Object> detail = new HashMap<>();
            detail.put("name", skill.getName());
            detail.put("displayName", skill.getDisplayName() != null ? skill.getDisplayName() : skill.getName());
            detail.put("slug", skill.getSlug());
            detail.put("builtin", Integer.valueOf(1).equals(skill.getIsBuiltin()));
            activeDetails.add(detail);
            extraToolIds.addAll(parseIds(skill.getToolIds()));
            extraMcpServerIds.addAll(parseIds(skill.getMcpServerIds()));
            String template = skill.getPromptTemplate();
            if (template != null && !template.isBlank()) {
                prompt.append("\n").append(template.trim()).append("\n");
            } else {
                prompt.append("\n- **").append(skill.getName()).append("**: ")
                        .append(skill.getDescription() != null ? skill.getDescription() : "").append("\n");
            }
        }

        ctx.setSkillSystemAppendix(prompt.toString());
        ctx.setSkillExtraToolIds(new ArrayList<>(extraToolIds));
        ctx.setSkillExtraMcpServerIds(new ArrayList<>(extraMcpServerIds));
        ctx.setActiveSkillNames(activeNames);
        ctx.setActiveSkillDetails(activeDetails);

        log.info("[SkillPrep] agentId={}, skills={}, extraToolIds={}, extraMcpServerIds={}",
                agent.getId(), activeNames, extraToolIds, extraMcpServerIds);
    }

    /** 解析 JSONB 数组字段（字符串 ID） */
    private List<Long> parseIds(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<Object> raw = OBJECT_MAPPER.readValue(json, new TypeReference<>() {});
            List<Long> ids = new ArrayList<>();
            for (Object item : raw) {
                if (item == null) continue;
                String text = item.toString().trim();
                if (text.isBlank()) continue;
                try {
                    ids.add(Long.parseLong(text));
                } catch (NumberFormatException ignored) {
                    log.warn("[SkillPrep] 跳过非法 ID: {}", text);
                }
            }
            return ids;
        } catch (Exception e) {
            log.warn("[SkillPrep] 解析 ID 列表失败: {}", e.getMessage());
            return List.of();
        }
    }
}
