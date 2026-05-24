package com.lightbot.service.chat;

import com.lightbot.dto.ChatRequest;
import com.lightbot.dto.LlmTraceSpan;
import com.lightbot.entity.Agent;
import lombok.Data;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    // ===== MessageMiddleware 构建 =====
    private List<org.springframework.ai.chat.messages.Message> messages;

    // ===== ToolPrepMiddleware 准备 =====
    private ChatModel chatModel;
    private ToolCallingChatOptions toolOptions;
    private Map<String, ToolCallback> toolCallbackMap;

    // ===== 流式累加器 =====
    private String requestId;
    private StringBuilder fullReply;
    private StringBuilder reasoningContent;
    private String[] ragMetadataHolder;
    private int[] toolCallCountHolder;
    private int[] inputTokenHolder;
    private int[] outputTokenHolder;
    private List<Map<String, Object>> toolEventsList;
    private List<LlmTraceSpan> spans;
    private long startTime;

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
        ctx.spans = new ArrayList<>();
        return ctx;
    }
}
