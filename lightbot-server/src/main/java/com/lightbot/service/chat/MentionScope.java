package com.lightbot.service.chat;

import com.lightbot.dto.ChatMentionDTO;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 本轮 @ 提及的资源范围（由 {@link MentionMiddleware} 校验后构建）
 * <p>各中间件按需读取对应集合做检索/委派/提示收窄，{@code null} 或空集合表示未 @ 该类型。</p>
 *
 * @author finch
 * @since 2026-06-29
 */
@Data
public class MentionScope {

    /** @ 的知识库 ID 集合（QueryKnowledgeTool 收窄检索范围） */
    private Set<Long> knowledgeIds = new HashSet<>();

    /** @ 的子智能体 ID 集合（ToolPrepMiddleware 收窄 SubAgent 委派范围） */
    private Set<Long> subAgentIds = new HashSet<>();

    /** @ 的 Skill ID 集合（SkillPrepMiddleware 提示增强） */
    private Set<Long> skillIds = new HashSet<>();

    /** @ 的工具 ID 集合（ToolPrepMiddleware 收窄 Agent 直接绑定的工具，不影响 Skill 引入的工具） */
    private Set<Long> toolIds = new HashSet<>();

    /** 原始 mention 列表（持久化到 message.metadata 快照用） */
    private List<ChatMentionDTO> rawMentions = new ArrayList<>();

    /** 校验过程中的告警信息（不影响主流程，仅记录日志） */
    private List<String> warnings = new ArrayList<>();

    public boolean hasMention() {
        return !knowledgeIds.isEmpty() || !subAgentIds.isEmpty()
                || !skillIds.isEmpty() || !toolIds.isEmpty();
    }
}
