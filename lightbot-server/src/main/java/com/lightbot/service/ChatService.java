package com.lightbot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lightbot.dto.ChatRequest;
import com.lightbot.entity.Message;
import com.lightbot.enums.ContentType;
import com.lightbot.enums.MessageRole;
import com.lightbot.mapper.MessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * AI对话服务
 *
 * @author finch
 * @since 2026-05-19
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatModel chatModel;
    private final MessageMapper messageMapper;
    private final ChatSessionService chatSessionService;

    private static final String SYSTEM_PROMPT = "你是 LightBot 智能助手，基于通义千问大模型，请用中文回答用户问题。";

    /**
     * 同步对话
     */
    public String chat(ChatRequest request) {
        Long sessionId = resolveSessionId(request.getSessionId(), request.getAgentId());
        List<org.springframework.ai.chat.messages.Message> messages = buildMessages(sessionId, request.getMessage());

        // 调用模型
        ChatResponse response = chatModel.call(new Prompt(messages));
        String reply = response.getResult().getOutput().getText();

        // 持久化消息
        saveMessage(sessionId, MessageRole.USER, request.getMessage());
        saveMessage(sessionId, MessageRole.ASSISTANT, reply);

        return reply;
    }

    /**
     * 流式对话（SSE）
     */
    public Flux<String> chatStream(ChatRequest request) {
        Long sessionId = resolveSessionId(request.getSessionId(), request.getAgentId());
        List<org.springframework.ai.chat.messages.Message> messages = buildMessages(sessionId, request.getMessage());

        // 保存用户消息
        saveMessage(sessionId, MessageRole.USER, request.getMessage());

        // 流式调用
        Flux<ChatResponse> stream = chatModel.stream(new Prompt(messages));

        StringBuilder fullReply = new StringBuilder();
        return stream.map(response -> {
            String delta = response.getResult().getOutput().getText();
            fullReply.append(delta);
            return delta;
        }).doOnComplete(() -> {
            saveMessage(sessionId, MessageRole.ASSISTANT, fullReply.toString());
        });
    }

    private Long resolveSessionId(Long sessionId, Long agentId) {
        if (sessionId != null) {
            return sessionId;
        }
        return chatSessionService.createSession(agentId).getId();
    }

    private List<org.springframework.ai.chat.messages.Message> buildMessages(Long sessionId, String userMessage) {
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT));

        // 加载最近20条历史消息
        List<Message> history = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getSessionId, sessionId)
                        .orderByAsc(Message::getCreateTime)
                        .last("LIMIT 20"));

        for (Message msg : history) {
            if (msg.getRole() == MessageRole.USER) {
                messages.add(new UserMessage(msg.getContent()));
            } else if (msg.getRole() == MessageRole.ASSISTANT) {
                messages.add(new AssistantMessage(msg.getContent()));
            }
        }

        messages.add(new UserMessage(userMessage));
        return messages;
    }

    private void saveMessage(Long sessionId, MessageRole role, String content) {
        Message msg = new Message();
        msg.setSessionId(sessionId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setContentType(ContentType.TEXT);
        msg.setTokenCount(0);
        messageMapper.insert(msg);

        // 更新会话统计
        chatSessionService.updateStats(sessionId, 0);
    }
}
