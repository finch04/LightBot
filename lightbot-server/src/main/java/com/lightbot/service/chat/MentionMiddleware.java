package com.lightbot.service.chat;

import com.lightbot.common.BizException;
import com.lightbot.dto.ChatMentionDTO;
import com.lightbot.entity.Agent;
import com.lightbot.entity.Knowledge;
import com.lightbot.entity.Skill;
import com.lightbot.entity.SubAgent;
import com.lightbot.entity.Tool;
import com.lightbot.enums.CommonStatus;
import com.lightbot.service.AgentService;
import com.lightbot.service.KnowledgeService;
import com.lightbot.service.SkillService;
import com.lightbot.service.SubAgentService;
import com.lightbot.service.ToolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Mention 校验中间件：在 {@code InitMiddleware} 之后校验 {@code ChatRequest.mentions}。
 * <p>校验通过后构建 {@link MentionScope} 写入 {@link ChatContext}，
 * 并通过 {@link MentionScopeStore} 缓存 knowledgeScope 供 {@code QueryKnowledgeTool} 跨线程读取。
 * 校验失败硬抛 400，列出所有失败 mention。</p>
 *
 * @author finch
 * @since 2026-06-29
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MentionMiddleware implements ChatMiddleware {

    private final AgentService agentService;
    private final KnowledgeService knowledgeService;
    private final SkillService skillService;
    private final SubAgentService subAgentService;
    private final ToolService toolService;
    private final MentionScopeStore mentionScopeStore;

    @Override
    public Flux<String> execute(ChatContext ctx, ChatMiddlewareChain next) {
        prepare(ctx);
        return next.proceed(ctx);
    }

    /**
     * 同步路径与流式路径共用的 mention 校验+scope 构建
     */
    public void prepare(ChatContext ctx) {
        List<ChatMentionDTO> mentions = ctx.getRequest() != null ? ctx.getRequest().getMentions() : null;
        if (mentions == null || mentions.isEmpty()) {
            return;
        }
        MentionScope scope = validateAndBuild(ctx, mentions);
        ctx.setMentionScope(scope);
        if (!scope.getKnowledgeIds().isEmpty()) {
            mentionScopeStore.putKnowledgeScope(ctx.getRequestId(), scope.getKnowledgeIds());
        }
        log.info("[Mention] requestId={}, knowledge={}, subagent={}, skill={}",
                ctx.getRequestId(), scope.getKnowledgeIds(), scope.getSubAgentIds(), scope.getSkillIds());
    }

    /**
     * 校验 mention 列表并构建 MentionScope，任一校验失败抛 BizException 列出全部错误
     */
    private MentionScope validateAndBuild(ChatContext ctx, List<ChatMentionDTO> mentions) {
        Agent agent = ctx.getAgent();
        Long agentId = agent != null ? agent.getId() : null;

        // 1. 解析当前可用的绑定集合：版本快照优先，否则用 Agent 表当前绑定
        Set<Long> boundKnowledgeIds = new HashSet<>(resolveBoundIds(
                ctx.getVersionKnowledgeIds(),
                agentId != null ? agentService.getKnowledgeIds(agentId) : List.of()));
        Set<Long> boundSubAgentIds = new HashSet<>(resolveBoundIds(
                ctx.getVersionSubAgentIds(),
                agentId != null ? agentService.getSubAgentIds(agentId) : List.of()));
        Set<Long> boundSkillIds = new HashSet<>(resolveBoundIds(
                ctx.getVersionSkillIds(),
                agentId != null ? agentService.getSkillIds(agentId) : List.of()));
        Set<Long> boundToolIds = new HashSet<>(resolveBoundIds(
                ctx.getVersionToolIds(),
                agentId != null ? agentService.getToolIds(agentId) : List.of()));

        MentionScope scope = new MentionScope();
        List<String> errors = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();

        // 临时收集通过绑定校验的 ID，最后批量校验 active 状态
        Set<Long> knowledgeIds = new LinkedHashSet<>();
        Set<Long> subAgentIds = new LinkedHashSet<>();
        Set<Long> skillIds = new LinkedHashSet<>();
        Set<Long> toolIds = new LinkedHashSet<>();

        for (ChatMentionDTO m : mentions) {
            String tokenLabel = m.getToken() != null ? m.getToken()
                    : (m.getType() != null ? "@" + m.getType().getCode() + ":" + m.getResourceId()
                            : "@unknown:" + m.getResourceId());

            if (m.getType() == null) {
                errors.add("[" + tokenLabel + "] 类型为空");
                continue;
            }
            Long rid;
            try {
                rid = Long.parseLong(m.getResourceId());
            } catch (Exception e) {
                errors.add("[" + tokenLabel + "] resourceId 无法解析为 Long");
                continue;
            }
            // 同 type+resourceId 去重
            if (!seenKeys.add(m.getType().name() + ":" + rid)) {
                continue;
            }

            switch (m.getType()) {
                case KNOWLEDGE -> {
                    if (!boundKnowledgeIds.contains(rid)) {
                        errors.add("[" + tokenLabel + "] 知识库不在当前版本绑定集合内");
                    } else {
                        knowledgeIds.add(rid);
                    }
                }
                case SUBAGENT -> {
                    if (!boundSubAgentIds.contains(rid)) {
                        errors.add("[" + tokenLabel + "] 子智能体不在当前版本绑定集合内");
                    } else {
                        subAgentIds.add(rid);
                    }
                }
                case SKILL -> {
                    if (!boundSkillIds.contains(rid)) {
                        errors.add("[" + tokenLabel + "] Skill 不在当前版本绑定集合内");
                    } else {
                        skillIds.add(rid);
                    }
                }
                case TOOL -> {
                    if (!boundToolIds.contains(rid)) {
                        errors.add("[" + tokenLabel + "] 工具不在当前版本绑定集合内");
                    } else {
                        toolIds.add(rid);
                    }
                }
                case MCP -> errors.add("[" + tokenLabel + "] 类型 " + m.getType().getCode() + " 暂未开放");
            }
            scope.getRawMentions().add(m);
        }

        // 2. 批量校验 active 状态（防止资源被禁用后仍被 mention）
        validateKnowledgeActive(knowledgeIds, errors);
        validateSubAgentActive(subAgentIds, errors);
        validateSkillActive(skillIds, errors);
        validateToolActive(toolIds, errors);

        if (!errors.isEmpty()) {
            throw new BizException("mention 校验失败：" + String.join("; ", errors));
        }

        scope.setKnowledgeIds(knowledgeIds);
        scope.setSubAgentIds(subAgentIds);
        scope.setSkillIds(skillIds);
        scope.setToolIds(toolIds);
        return scope;
    }

    private void validateKnowledgeActive(Set<Long> ids, List<String> errors) {
        if (ids.isEmpty()) return;
        for (Knowledge k : knowledgeService.listByIds(ids)) {
            if (k.getStatus() != CommonStatus.ACTIVE) {
                errors.add("[@knowledge:" + k.getId() + "] 知识库已禁用或删除");
            }
        }
    }

    private void validateSubAgentActive(Set<Long> ids, List<String> errors) {
        if (ids.isEmpty()) return;
        for (SubAgent s : subAgentService.listByIds(ids)) {
            if (!Integer.valueOf(1).equals(s.getEnabled())) {
                errors.add("[@subagent:" + s.getId() + "] 子智能体已禁用");
            }
        }
    }

    private void validateSkillActive(Set<Long> ids, List<String> errors) {
        if (ids.isEmpty()) return;
        for (Skill s : skillService.listByIds(ids)) {
            if (s.getStatus() != CommonStatus.ACTIVE) {
                errors.add("[@skill:" + s.getId() + "] Skill 已禁用或删除");
            }
        }
    }

    private void validateToolActive(Set<Long> ids, List<String> errors) {
        if (ids.isEmpty()) return;
        for (Tool t : toolService.listByIds(ids)) {
            if (t.getStatus() != CommonStatus.ACTIVE) {
                errors.add("[@tool:" + t.getId() + "] 工具已禁用或删除");
            }
        }
    }

    /** 版本快照绑定 ID 非空优先用快照，否则回退到 Agent 表当前绑定 */
    private List<Long> resolveBoundIds(List<Long> versionIds, List<Long> fallback) {
        if (versionIds != null && !versionIds.isEmpty()) {
            return versionIds;
        }
        return fallback != null ? fallback : Collections.emptyList();
    }
}
