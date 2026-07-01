package com.lightbot.service.chat;

import com.lightbot.dto.ChatMentionDTO;
import com.lightbot.enums.MentionResourceType;

import java.util.List;

/**
 * 根据用户 @ mention 构建「优先使用」提示文案（不收窄运行时工具/知识库范围）。
 *
 * @author finch
 * @since 2026-07-01
 */
public final class MentionHintBuilder {

    private MentionHintBuilder() {
    }

    /**
     * 构建 system 追加块（Markdown）
     *
     * @param mentions 校验通过的 mention 列表
     * @return 非空则追加到 system prompt
     */
    public static String buildSystemAppendix(List<ChatMentionDTO> mentions) {
        if (mentions == null || mentions.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n\n## 用户 @ 指定（优先使用）\n");
        sb.append("用户在本轮通过 @ 明确指定以下资源，请**优先**使用；@ 仅表示优先级，**不会限制**其他已绑定工具、知识库、Skill 或 SubAgent。\n\n");
        appendLines(sb, mentions);
        return sb.toString();
    }

    /**
     * 构建用户消息前缀（紧凑提示）
     *
     * @param userMessage 原始用户正文
     * @param mentions    校验通过的 mention 列表
     */
    public static String prependUserMessageHint(String userMessage, List<ChatMentionDTO> mentions) {
        if (mentions == null || mentions.isEmpty()) {
            return userMessage;
        }
        StringBuilder sb = new StringBuilder("[用户 @ 指定（优先使用，不限其他已绑定能力）：");
        for (ChatMentionDTO m : mentions) {
            if (m.getType() == null) {
                continue;
            }
            String label = labelOf(m);
            sb.append("\n- ").append(m.getType().getDesc()).append("：").append(label);
        }
        sb.append("]\n\n");
        sb.append(userMessage != null ? userMessage : "");
        return sb.toString();
    }

    private static void appendLines(StringBuilder sb, List<ChatMentionDTO> mentions) {
        for (ChatMentionDTO m : mentions) {
            if (m.getType() == null) {
                continue;
            }
            sb.append("- ").append(priorityLine(m)).append("\n");
        }
    }

    private static String priorityLine(ChatMentionDTO m) {
        String label = labelOf(m);
        MentionResourceType type = m.getType();
        return switch (type) {
            case TOOL -> "优先调用工具 **" + label + "**";
            case KNOWLEDGE -> "优先在知识库 **" + label + "** 中检索（仍可使用 query_knowledge 查其他绑定库）";
            case SKILL -> "优先使用 Skill **" + label + "**（可先 `read_skill` 读取完整指令）";
            case SUBAGENT -> "优先通过 `delegate_to_subagent` 委派给 **" + label + "**";
            case MCP -> "优先使用 MCP **" + label + "**";
        };
    }

    private static String labelOf(ChatMentionDTO m) {
        if (m.getName() != null && !m.getName().isBlank()) {
            return m.getName();
        }
        if (m.getToken() != null && !m.getToken().isBlank()) {
            return m.getToken();
        }
        return String.valueOf(m.getResourceId());
    }
}
