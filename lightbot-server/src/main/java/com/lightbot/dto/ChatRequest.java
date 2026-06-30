package com.lightbot.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ChatRequest {

    /** 文本内容；仅有附件时可留空，由服务端补默认提示 */
    private String message;

    private Long sessionId;

    private Long agentId;

    /**
     * 入参变量，用于替换系统提示词中的 {{变量名}} 占位符
     */
    private Map<String, Object> bizParams;

    /**
     * 对话使用的配置版本：null=默认（已发布则用线上最新，否则 agent 表当前值）；
     * 0=暂存草稿；正整数=指定已发布版本号（用于调试/对比）
     */
    private Integer configVersion;

    /**
     * Agent版本快照ID（agent_version.id），用于持久化到会话。
     * 优先级高于 configVersion：非空时用于会话绑定，避免版本编号复用导致误匹配。
     */
    private Long agentVersionId;

    /**
     * 多模态附件（先调用上传接口获得）
     */
    private List<ChatAttachmentDTO> attachments;

    /**
     * 重新生成：删除上一轮助手回复后，基于最近一条用户消息再次调用模型（不重复落库用户消息）
     */
    private Boolean regenerate;

    /**
     * 编辑重发：更新指定用户消息内容后，删除助手回复并重新生成
     * <p>与 regenerate=true 配合使用，此时 message 为编辑后的新内容</p>
     */
    private Long editMessageId;

    /**
     * 引用回复：引用某条历史消息进行回复
     */
    private Long replyToMessageId;

    /**
     * 本轮用户 @ 提及的资源，后端校验后用于收窄检索/委派范围
     */
    private List<ChatMentionDTO> mentions;

    /**
     * API Key ID（由拦截器注入，不从前端传入）
     */
    private transient Long apiKeyId;
}
