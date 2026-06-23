package com.lightbot.service.chat;

import com.lightbot.dto.ChatRequest;
import com.lightbot.dto.LlmTraceSpan;
import com.lightbot.entity.Agent;
import com.lightbot.entity.ToolCall;
import com.lightbot.util.SensitiveWordFilter;
import lombok.Data;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 对话管道上下文，承载整个中间件链的共享状态
 *
 * @author finch
 * @since 2026-05-23
 */
@Data
public class ChatContext {

    // ===== 输入 =====
    private ChatRequest request;

    // ===== InitMiddleware 解析 =====
    private Long sessionId;
    private Agent agent;
    private Map<String, Object> configMap;
    private Long providerId;

    // ===== 版本快照绑定 ID（configVersion 或已发布版本快照中的绑定，优先级高于 agent 表当前值） =====
    private List<Long> versionToolIds;
    private List<Long> versionKnowledgeIds;
    private List<Long> versionMcpServerIds;
    private List<Long> versionSubAgentIds;
    private List<Long> versionSkillIds;

    // ===== MessageMiddleware 构建 =====
    private List<org.springframework.ai.chat.messages.Message> messages;

    // ===== SkillPrepMiddleware 准备 =====
    /** 由 Skill 拼接出来的额外系统提示词（追加到 Agent.systemPrompt 之后） */
    private String skillSystemAppendix;
    /** 由 Skill 引入的额外 Tool ID（合并入 ToolPrep 的工具集合） */
    private List<Long> skillExtraToolIds;
    /** 由 Skill 引入的额外 MCP Server ID */
    private List<Long> skillExtraMcpServerIds;
    /** 启用的 Skill 名称列表（用于 trace 与日志） */
    private List<String> activeSkillNames;
    /** 启用的 Skill 详情（用于前端 skill_active 事件展示） */
    private List<Map<String, Object>> activeSkillDetails;
    /** toolName → Skill 详情映射（用于工具调用时按需推送 skill_active 事件） */
    private Map<String, Map<String, Object>> toolNameToSkillDetail;
    /** 已激活的 Skill slug 集合（跨轮次持久化在 Redis 中，每请求加载） */
    private Set<String> activatedSkills;

    // ===== SubAgentPrepMiddleware 准备 =====
    /** 当前 Agent 绑定的可委派 SubAgent ID 列表 */
    private List<Long> boundSubAgentIds;

    // ===== ToolPrepMiddleware 准备 =====
    private ChatModel chatModel;
    private ToolCallingChatOptions toolOptions;
    private Map<String, ToolCallback> toolCallbackMap;

    // ===== 消息ID =====
    /** 用户消息ID（MessageMiddleware 保存后写入） */
    private Long userMessageId;

    // ===== 流式累加器 =====
    private String requestId;
    private StringBuilder fullReply;
    private StringBuilder reasoningContent;
    private String[] ragMetadataHolder;
    private int[] toolCallCountHolder;
    private int[] inputTokenHolder;
    private int[] outputTokenHolder;
    private List<Map<String, Object>> toolEventsList;
    /** 工作流节点执行事件（WORKFLOW 类型 Agent） */
    private List<Map<String, Object>> workflowEventsList;
    private List<LlmTraceSpan> spans;
    private long startTime;

    /** 流式敏感词过滤状态（按累积全文过滤，避免分片漏拦） */
    private SensitiveWordFilter.StreamState sensitiveStreamState;

    /** 待持久化的工具调用记录（assistant 消息保存后批量写入，关联 messageId） */
    private List<ToolCall> pendingToolCalls;

    /**
     * 工厂方法：创建上下文并初始化所有累加器
     */
    public static ChatContext of(ChatRequest request) {
        ChatContext ctx = new ChatContext();
        ctx.request = request;
        ctx.fullReply = new StringBuilder();
        ctx.reasoningContent = new StringBuilder();
        ctx.ragMetadataHolder = new String[]{null};
        ctx.toolCallCountHolder = new int[]{0};
        ctx.inputTokenHolder = new int[]{0};
        ctx.outputTokenHolder = new int[]{0};
        ctx.toolEventsList = new ArrayList<>();
        ctx.workflowEventsList = new ArrayList<>();
        ctx.spans = new ArrayList<>();
        ctx.pendingToolCalls = new ArrayList<>();
        ctx.activatedSkills = new LinkedHashSet<>();
        return ctx;
    }
}
