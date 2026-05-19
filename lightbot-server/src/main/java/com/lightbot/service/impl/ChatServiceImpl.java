package com.lightbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lightbot.dto.ChatRequest;
import com.lightbot.entity.Message;
import com.lightbot.enums.ContentType;
import com.lightbot.enums.MessageRole;
import com.lightbot.mapper.MessageMapper;
import com.lightbot.service.ChatService;
import com.lightbot.service.ChatSessionService;
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
 * AI对话服务实现类
 *
 * @author finch
 * @since 2026-05-19
 */
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatModel chatModel;
    private final MessageMapper messageMapper;
    private final ChatSessionService chatSessionService;

    private static final String SYSTEM_PROMPT = "你是 LightBot 智能助手，基于通义千问大模型，请用中文回答用户问题。";

    @Override
    public String chat(ChatRequest request) {
        // 1. 解析会话ID，无则新建
        Long sessionId = resolveSessionId(request.getSessionId(), request.getAgentId());

        // 2. 构建消息列表（含历史消息）
        List<org.springframework.ai.chat.messages.Message> messages = buildMessages(sessionId, request.getMessage());

        // 3. 调用模型获取回复
        ChatResponse response = chatModel.call(new Prompt(messages));
        String reply = response.getResult().getOutput().getText();

        // 4. 持久化用户消息和AI回复
        saveMessage(sessionId, MessageRole.USER, request.getMessage());
        saveMessage(sessionId, MessageRole.ASSISTANT, reply);

        return reply;
    }

    @Override
    public Flux<String> chatStream(ChatRequest request) {
        // 1. 解析会话ID
        Long sessionId = resolveSessionId(request.getSessionId(), request.getAgentId());

        // 2. 构建消息列表
        List<org.springframework.ai.chat.messages.Message> messages = buildMessages(sessionId, request.getMessage());

        // 3. 先保存用户消息
        saveMessage(sessionId, MessageRole.USER, request.getMessage());

        // 4. 流式调用模型，收集完整回复后持久化
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

    /**
     * 解析会话ID：有则复用，无则新建
     */
    private Long resolveSessionId(Long sessionId, Long agentId) {
        if (sessionId != null) {
            return sessionId;
        }
        return chatSessionService.createSession(agentId).getId();
    }

    /**
     * 构建消息列表：系统提示词 + 最近20条历史 + 当前用户消息
     */
    private List<org.springframework.ai.chat.messages.Message> buildMessages(Long sessionId, String userMessage) {
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT));

        // 加载最近20条历史消息，保持对话连贯性
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

    /**
     * 持久化消息并更新会话统计
     */
    private void saveMessage(Long sessionId, MessageRole role, String content) {
        Message msg = new Message();
        msg.setSessionId(sessionId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setContentType(ContentType.TEXT);
        msg.setTokenCount(0);
        messageMapper.insert(msg);

        chatSessionService.updateStats(sessionId, 0);
    }
}
