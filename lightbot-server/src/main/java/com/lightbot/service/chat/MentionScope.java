package com.lightbot.service.chat;

import com.lightbot.dto.ChatMentionDTO;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 本轮 @ 提及的资源（由 {@link MentionMiddleware} 校验后构建）
 * <p>各 ID 集合用于提示词「优先使用」增强；不再收窄运行时工具/知识库/SubAgent 加载范围。</p>
 *
 * @author finch
 * @since 2026-06-29
 */
@Data
public class MentionScope {

    /** @ 的知识库 ID 集合（提示词优先检索） */
    private Set<Long> knowledgeIds = new HashSet<>();

    /** @ 的子智能体 ID 集合（提示词优先委派） */
    private Set<Long> subAgentIds = new HashSet<>();

    /** @ 的 Skill ID 集合（提示词优先使用） */
    private Set<Long> skillIds = new HashSet<>();

    /** @ 的工具 ID 集合（提示词优先调用） */
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
