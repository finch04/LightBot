package com.lightbot.dto;

import lombok.Data;

/**
 * 长期记忆自动抽取入参
 * <p>由对话编排层（server）在助手消息落库后构建，替代原先直接传入 ChatContext，
 * 使记忆抽取逻辑脱离 server 编排层，得以下沉至 agent 模块。</p>
 *
 * @author finch
 * @since 2026-07-12
 */
@Data
public class MemoryExtractDTO {

    private Long userId;

    /** 企业 API Key ID（开放 API 外部用户记忆） */
    private Long apiKeyId;

    /** 上层业务终端用户标识 */
    private String externalUserId;

    private Long sessionId;

    private Long agentId;

    private Long sourceMessageId;

    private String userMessage;

    private String assistantReply;

    /** 本轮是否已通过 memory_save 工具主动保存记忆，为 true 时跳过启发式兜底抽取 */
    private Boolean memorySaved;
}
