package com.lightbot.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ChatRequestDTO {

    /** 文本内容；仅有附件时可留空，由服务端补默认提示 */
    private String message;

    private Long sessionId;

    private Long agentId;

    /**
     * 上层业务系统的终端用户标识（可选）。
     * <p>仅企业 API Key 调用时生效；传入后启用该用户在本 Key 下的跨会话长期记忆。
     * 不传则保持无个人记忆（仅 sessionId 隔离短期上下文）。</p>
     * <p>兼容字段：等价于 {@code callerContext.externalUserId}；优先使用 {@link #callerContext}。</p>
     */
    private String externalUserId;

    /**
     * 调用方身份上下文（可选，仅 API Key）。
     * <p>含 externalUserId / regionId / enterpriseId / profile，供 Tool、MCP、问数做数据与权限隔离。
     * 首轮写入会话后续聊可省略；隔离主键不可中途更换。</p>
     */
    private CallerContext callerContext;

    /**
     * 入参变量，用于替换系统提示词中的 {{变量名}} 占位符（不承载身份）
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
     * 重新生成：基于最近一条用户消息再次调用模型（不重复落库用户消息）
     */
    private Boolean regenerate;

    /**
     * 重新生成时要删除的助手消息 ID；为空时不删除库中记录（未落库的失败/终止回复重试）
     */
    private Long deleteAssistantMessageId;

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
     * 本轮是否启用深度思考。null=沿用 Agent 配置；false=强制关闭（如业务办理页提交回灌）。
     */
    private Boolean enableReasoning;

    /**
     * API Key ID（由拦截器注入，不从前端传入）
     */
    private transient Long apiKeyId;

    /**
     * 系统内部调用时的执行用户（无登录态时由 InitMiddleware 回退使用，不从前端传入）
     */
    private transient Long actorUserId;
}
