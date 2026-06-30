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
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

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
    private Long userId;
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

    // ===== MentionMiddleware 构建 =====
    /** 本轮 @ 提及的资源范围（null 表示无 mention） */
    private MentionScope mentionScope;

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
    /** toolName → displayName 映射（前端展示用） */
    private Map<String, String> toolDisplayNameMap;

    // ===== 消息ID =====
    /** 用户消息ID（MessageMiddleware 保存后写入） */
    private Long userMessageId;
    /** 助手消息ID（buildDoneEvent 保存后写入） */
    private Long assistantMessageId;
    /** 用户消息的父消息ID（ask_user 触发时指向触发该问题的助手消息） */
    private Long userMessageParentId;

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

    /** 流式模型调用是否最终失败 */
    private boolean streamFailed;
    /** 流式模型调用最终失败提示 */
    private String streamErrorMessage;
    /** 流式模型调用最终失败错误码 */
    private String streamErrorCode;

    /** 流式敏感词过滤状态（按累积全文过滤，避免分片漏拦） */
    private SensitiveWordFilter.StreamState sensitiveStreamState;

    /** 流式 inline thinking 标签解析（Ollama 等模型） */
    com.lightbot.util.InlineThinkingStreamParser inlineThinkingParser;

    /** 流式 getText() 快照，用于从累积文本中提取增量 */
    private String streamTextSnapshot = "";

    /** 模型原始流式输出（未剥离标签），入库前兜底解析用（每轮 LLM 调用重置） */
    private StringBuilder rawLlmStreamText = new StringBuilder();

    /** Trace 用：整轮对话模型原始输出累积（含 thinking 标签，不重置、不删改） */
    private StringBuilder traceCompleteReply = new StringBuilder();

    /** Trace 用：metadata 通道的原始 reasoning 累积（MiMo 等，与正文分通道时不合并删改） */
    private StringBuilder traceMetadataReasoning = new StringBuilder();

    /** inline thinking 是否已完成最终解析（Trace 与入库共用，避免重复解析） */
    private boolean inlineThinkingFinalized;

    /** 待持久化的工具调用记录（assistant 消息保存后批量写入，关联 messageId） */
    private List<ToolCall> pendingToolCalls;

    // ===== 子代理流式事件队列 =====
    private Queue<SubAgentEvent> subAgentEventQueue;

    /** 当前 SubAgent 委派对应的 contentOffset（主 Agent 回复中的插入位置） */
    private Integer subAgentContentOffset;

    /** 流式模式下实时推送 [STATUS] JSON 到 SSE（由 ChatServiceImpl 注入） */
    private java.util.function.Consumer<String> realtimeStatusEmitter;

    /**
     * 实时推送结构化状态事件 JSON（不含 [STATUS] 前缀）
     *
     * @param statusJson 状态事件 JSON
     */
    public void emitRealtimeStatus(String statusJson) {
        if (realtimeStatusEmitter != null && statusJson != null && !statusJson.isBlank()) {
            realtimeStatusEmitter.accept(statusJson);
        }
    }

    /**
     * 子代理流式事件（token/tool_call/tool_result）
     */
    public record SubAgentEvent(String type, String subagentName, String content, int contentOffset) {}

    /**
     * 推送子代理事件到队列
     */
    public void pushSubAgentEvent(SubAgentEvent event) {
        if (subAgentEventQueue != null && event != null) {
            subAgentEventQueue.add(event);
        }
    }

    /**
     * 取出并清空所有待消费的子代理事件
     */
    public List<SubAgentEvent> drainSubAgentEvents() {
        if (subAgentEventQueue == null) {
            return List.of();
        }
        List<SubAgentEvent> events = new ArrayList<>();
        SubAgentEvent event;
        while ((event = subAgentEventQueue.poll()) != null) {
            events.add(event);
        }
        return events;
    }

    /**
     * 追加思考内容（入库前清理 NUL 等 PostgreSQL 非法字符）
     *
     * @param chunk 思考片段
     * @return 实际追加的清理后文本，未追加则返回空串
     */
    public String appendReasoningContent(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return "";
        }
        String cleaned = com.lightbot.util.TextNormalizeUtil.sanitizeForDatabase(chunk);
        if (cleaned.isEmpty()) {
            return "";
        }
        reasoningContent.append(cleaned);
        return cleaned;
    }

    /**
     * 流式结束后最终解析 thinking 标签并规范化 reasoning / 正文（Trace 与入库前调用）。
     */
    public void finalizeInlineThinking() {
        if (inlineThinkingFinalized) {
            return;
        }
        if (inlineThinkingParser == null) {
            inlineThinkingFinalized = true;
            return;
        }

        com.lightbot.util.InlineThinkingStreamParser.ParseResult tail = inlineThinkingParser.flush();
        if (!tail.reasoningDelta().isEmpty()) {
            appendReasoningContent(tail.reasoningDelta());
        }
        if (!tail.contentDelta().isEmpty()) {
            fullReply.append(tail.contentDelta());
        }

        String raw = getRawLlmStreamText().toString();
        String reply = fullReply.toString();
        String source = com.lightbot.util.InlineThinkingStreamParser.containsThinkingTags(raw) ? raw
                : com.lightbot.util.InlineThinkingStreamParser.containsThinkingTags(reply) ? reply : null;
        if (source != null) {
            com.lightbot.util.InlineThinkingStreamParser.ParseResult parsed =
                    com.lightbot.util.InlineThinkingStreamParser.parseComplete(source);
            reasoningContent.setLength(0);
            if (!parsed.reasoningDelta().isEmpty()) {
                appendReasoningContent(parsed.reasoningDelta());
            }
            fullReply.setLength(0);
            fullReply.append(parsed.contentDelta());
        }

        if (reasoningContent.length() > 0) {
            String normalized = com.lightbot.util.InlineThinkingStreamParser.normalizeReasoningText(
                    reasoningContent.toString());
            reasoningContent.setLength(0);
            reasoningContent.append(normalized);
        }
        if (fullReply.length() > 0) {
            String normalized = com.lightbot.util.InlineThinkingStreamParser.normalizeContentText(
                    fullReply.toString());
            fullReply.setLength(0);
            fullReply.append(normalized);
        }
        inlineThinkingFinalized = true;
    }

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
        ctx.subAgentEventQueue = new ConcurrentLinkedQueue<>();
        ctx.inlineThinkingParser = new com.lightbot.util.InlineThinkingStreamParser();
        ctx.rawLlmStreamText = new StringBuilder();
        ctx.traceCompleteReply = new StringBuilder();
        ctx.traceMetadataReasoning = new StringBuilder();
        return ctx;
    }

    /**
     * 追加 Trace 完整回复（模型原始输出，不做标签剥离或换行规范化）。
     */
    public void appendTraceCompleteReply(String delta) {
        if (delta == null || delta.isEmpty()) {
            return;
        }
        if (traceCompleteReply == null) {
            traceCompleteReply = new StringBuilder();
        }
        traceCompleteReply.append(delta);
    }

    /**
     * 追加 Trace 用 metadata reasoning 原始片段（MiMo 等分通道 reasoning）。
     */
    public void appendTraceMetadataReasoning(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return;
        }
        if (traceMetadataReasoning == null) {
            traceMetadataReasoning = new StringBuilder();
        }
        traceMetadataReasoning.append(chunk);
    }

    /**
     * 构建 Trace 的 AI 完整回复：优先流式原文（含 thinking 标签）；metadata reasoning 与正文分通道时按序拼接。
     */
    public String buildTraceCompleteReply() {
        String rawText = traceCompleteReply != null ? traceCompleteReply.toString() : "";
        String metaReasoning = traceMetadataReasoning != null ? traceMetadataReasoning.toString() : "";
        if (!metaReasoning.isEmpty() && !rawText.isEmpty()) {
            return metaReasoning + rawText;
        }
        if (!rawText.isEmpty()) {
            return rawText;
        }
        return metaReasoning;
    }

    /**
     * 获取流式 inline thinking 标签解析器（Ollama 等模型）
     */
    public com.lightbot.util.InlineThinkingStreamParser getInlineThinkingParser() {
        return inlineThinkingParser;
    }

    /**
     * 新一轮 LLM 流式调用前重置文本快照与 inline thinking 解析器。
     */
    public void resetStreamTextTracking() {
        streamTextSnapshot = "";
        inlineThinkingParser = new com.lightbot.util.InlineThinkingStreamParser();
        if (rawLlmStreamText == null) {
            rawLlmStreamText = new StringBuilder();
        } else {
            rawLlmStreamText.setLength(0);
        }
    }

    /**
     * 追加模型原始流式文本（含 thinking 标签，供入库前兜底解析）。
     */
    public void appendRawLlmStreamText(String delta) {
        if (delta == null || delta.isEmpty()) {
            return;
        }
        if (rawLlmStreamText == null) {
            rawLlmStreamText = new StringBuilder();
        }
        rawLlmStreamText.append(delta);
        appendTraceCompleteReply(delta);
    }

    /**
     * 获取模型原始流式输出累积
     */
    public StringBuilder getRawLlmStreamText() {
        if (rawLlmStreamText == null) {
            rawLlmStreamText = new StringBuilder();
        }
        return rawLlmStreamText;
    }

    /**
     * 从流式 getText() 提取增量（兼容增量片段与累积全文两种模式）。
     *
     * @param currentText 当前 chunk 的 getText() 返回值
     * @return 相对上一 chunk 的新增文本
     */
    public String consumeStreamTextDelta(String currentText) {
        if (currentText == null || currentText.isEmpty()) {
            return "";
        }
        if (!streamTextSnapshot.isEmpty()
                && currentText.startsWith(streamTextSnapshot)
                && currentText.length() > streamTextSnapshot.length()) {
            String delta = currentText.substring(streamTextSnapshot.length());
            streamTextSnapshot = currentText;
            return delta;
        }
        if (currentText.equals(streamTextSnapshot)) {
            return "";
        }
        streamTextSnapshot = streamTextSnapshot + currentText;
        return currentText;
    }
}
