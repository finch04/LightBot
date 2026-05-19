package com.lightbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.constant.ConfigKeys;
import com.lightbot.dto.ChatRequest;
import com.lightbot.entity.Agent;
import com.lightbot.entity.ChatSession;
import com.lightbot.entity.Message;
import com.lightbot.enums.ContentType;
import com.lightbot.enums.MessageRole;
import com.lightbot.mapper.MessageMapper;
import com.lightbot.model.ModelFactory;
import com.lightbot.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI对话服务实现类
 *
 * @author finch
 * @since 2026-05-19
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final MessageMapper messageMapper;
    private final ChatSessionService chatSessionService;
    private final AgentService agentService;
    private final AgentKnowledgeService agentKnowledgeService;
    private final EmbeddingService embeddingService;
    private final EmbeddingModel embeddingModel;
    private final ModelFactory modelFactory;
    private final TaskExecutor taskExecutor;

    private static final String DEFAULT_SYSTEM_PROMPT = "你是 LightBot 智能助手，基于通义千问大模型，请用中文回答用户问题。";

    private static final String RAG_CONTEXT_TEMPLATE = """
            请基于以下参考资料回答用户的问题。
            如果参考资料中没有相关信息，请如实告知用户。

            参考资料：
            %s
            """;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public String chat(ChatRequest request) {
        // 1. 解析会话ID，无则新建
        Long sessionId = resolveSessionId(request.getSessionId(), request.getAgentId());

        // 2. 加载Agent配置
        Agent agent = loadAgent(request.getAgentId());

        // 3. 解析config获取providerId和配置
        Map<String, Object> configMap = parseConfig(agent != null ? agent.getConfig() : null);
        Long providerId = getProviderId(configMap);

        // 4. 构建消息列表（含系统提示词 + RAG上下文 + 历史消息）
        List<org.springframework.ai.chat.messages.Message> messages = buildMessages(sessionId, request.getMessage(), agent);

        // 5. 通过 ModelFactory 获取 ChatModel 和 ChatOptions
        ChatModel chatModel = modelFactory.getChatModel(providerId);
        ChatOptions options = modelFactory.buildChatOptions(providerId, configMap);

        // 6. 调用模型获取回复
        ChatResponse response = chatModel.call(new Prompt(messages, options));
        String reply = response.getResult().getOutput().getText();

        // 7. 持久化用户消息和AI回复
        saveMessage(sessionId, MessageRole.USER, request.getMessage());
        saveMessage(sessionId, MessageRole.ASSISTANT, reply);

        // 8. 异步生成标题
        taskExecutor.execute(() -> generateTitle(sessionId));

        return reply;
    }

    @Override
    public Flux<String> chatStream(ChatRequest request) {
        // 1. 解析会话ID
        Long sessionId = resolveSessionId(request.getSessionId(), request.getAgentId());

        // 2. 加载Agent配置
        Agent agent = loadAgent(request.getAgentId());

        // 3. 解析config获取providerId和配置
        Map<String, Object> configMap = parseConfig(agent != null ? agent.getConfig() : null);
        Long providerId = getProviderId(configMap);

        // 4. 构建消息列表
        List<org.springframework.ai.chat.messages.Message> messages = buildMessages(sessionId, request.getMessage(), agent);

        // 5. 通过 ModelFactory 获取 ChatModel 和 ChatOptions
        ChatModel chatModel = modelFactory.getChatModel(providerId);
        ChatOptions options = modelFactory.buildChatOptions(providerId, configMap);

        // 6. 先保存用户消息
        saveMessage(sessionId, MessageRole.USER, request.getMessage());

        // 7. 流式调用模型，收集完整回复后持久化
        Flux<ChatResponse> stream = chatModel.stream(new Prompt(messages, options));

        StringBuilder fullReply = new StringBuilder();
        return stream.map(response -> {
            String delta = response.getResult().getOutput().getText();
            fullReply.append(delta);
            return delta;
        }).doOnComplete(() -> {
            saveMessage(sessionId, MessageRole.ASSISTANT, fullReply.toString());
            taskExecutor.execute(() -> generateTitle(sessionId));
        });
    }

    /**
     * 加载Agent配置，agentId为空时返回null
     */
    private Agent loadAgent(Long agentId) {
        if (agentId == null) {
            return null;
        }
        Agent agent = agentService.getById(agentId);
        if (agent == null) {
            log.warn("[Chat] Agent不存在，agentId={}", agentId);
        }
        return agent;
    }

    /**
     * 解析config JSONB字符串为Map
     */
    private Map<String, Object> parseConfig(String config) {
        if (config == null || config.isBlank()) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(config, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("[Chat] 解析Agent config失败: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * 从config Map中获取providerId
     */
    private Long getProviderId(Map<String, Object> configMap) {
        Object providerId = configMap.get(ConfigKeys.Agent.PROVIDER_ID);
        if (providerId == null) {
            throw new IllegalArgumentException("Agent config中缺少providerId配置");
        }
        return providerId instanceof Number ? ((Number) providerId).longValue() : Long.parseLong(providerId.toString());
    }

    /**
     * 构建消息列表：系统提示词 + RAG上下文 + 最近20条历史 + 当前用户消息
     */
    private List<org.springframework.ai.chat.messages.Message> buildMessages(Long sessionId, String userMessage, Agent agent) {
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();

        // 1. 系统提示词：优先使用Agent的systemPrompt
        String systemPrompt = (agent != null && agent.getSystemPrompt() != null && !agent.getSystemPrompt().isBlank())
                ? agent.getSystemPrompt()
                : DEFAULT_SYSTEM_PROMPT;
        messages.add(new SystemMessage(systemPrompt));

        // 2. RAG上下文：如果Agent绑定了知识库，检索相关上下文
        if (agent != null) {
            String ragContext = retrieveRagContext(agent.getId(), userMessage);
            if (ragContext != null) {
                messages.add(new SystemMessage(String.format(RAG_CONTEXT_TEMPLATE, ragContext)));
            }
        }

        // 3. 加载最近20条历史消息
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

        // 4. 当前用户消息
        messages.add(new UserMessage(userMessage));
        return messages;
    }

    /**
     * RAG检索：查询Agent绑定的知识库，向量检索相关上下文
     */
    private String retrieveRagContext(Long agentId, String question) {
        List<Long> knowledgeIds = agentKnowledgeService.getKnowledgeIds(agentId);
        if (knowledgeIds.isEmpty()) {
            return null;
        }

        try {
            float[] queryVector = embedText(question);
            List<String> contexts = new ArrayList<>();

            for (Long knowledgeId : knowledgeIds) {
                List<Map<String, Object>> results = embeddingService.searchSimilar(knowledgeId, queryVector, 3, 0.5);
                for (Map<String, Object> row : results) {
                    contexts.add(String.format("【%s】\n%s", row.get("document_name"), row.get("content")));
                }
            }

            return contexts.isEmpty() ? null : String.join("\n\n---\n\n", contexts);
        } catch (Exception e) {
            log.warn("[Chat] RAG检索失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 文本向量化
     */
    private float[] embedText(String text) {
        EmbeddingResponse response = embeddingModel.call(
                new EmbeddingRequest(List.of(text), null));
        return response.getResult().getOutput();
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

    /**
     * 异步生成对话标题：标题仍为"新对话"且消息数>=2时，调用AI生成简短标题
     */
    private void generateTitle(Long sessionId) {
        try {
            // 1. 检查会话是否存在且标题仍为默认值
            ChatSession session = chatSessionService.getById(sessionId);
            if (session == null || !"新对话".equals(session.getTitle())) {
                return;
            }

            // 2. 获取前4条消息
            List<Message> messages = messageMapper.selectList(
                    new LambdaQueryWrapper<Message>()
                            .eq(Message::getSessionId, sessionId)
                            .orderByAsc(Message::getCreateTime)
                            .last("LIMIT 4"));

            if (messages.size() < 2) {
                return;
            }

            // 3. 拼接对话文本
            StringBuilder conversationText = new StringBuilder();
            for (Message msg : messages) {
                String role = msg.getRole() == MessageRole.USER ? "用户" : "助手";
                conversationText.append(role).append("：").append(msg.getContent()).append("\n");
            }

            // 4. 使用默认提供商调用模型生成标题
            List<org.springframework.ai.chat.messages.Message> promptMessages = new ArrayList<>();
            promptMessages.add(new SystemMessage("你是一个标题生成助手，只输出标题，不要任何其他内容。"));
            promptMessages.add(new UserMessage("请根据以下对话内容生成一个简短的标题（不超过20个字，不要加引号）：\n" + conversationText));

            ChatResponse response = modelFactory.getChatModel(getDefaultProviderId()).call(new Prompt(promptMessages));
            String title = response.getResult().getOutput().getText().trim();

            // 5. 清理标题
            title = title.replaceAll("^[\"'「」『』]+|[\"'「」『』]+$", "");
            if (title.length() > 30) {
                title = title.substring(0, 30);
            }

            // 6. 更新会话标题
            if (!title.isBlank()) {
                chatSessionService.updateTitle(sessionId, title);
                log.info("[Chat] 会话标题已生成: sessionId={}, title={}", sessionId, title);
            }
        } catch (Exception e) {
            log.warn("[Chat] 标题生成失败: sessionId={}, error={}", sessionId, e.getMessage());
        }
    }

    /**
     * 获取默认providerId（用于标题生成等不需要指定provider的场景）
     * 优先使用第一个可用的 ModelProvider
     */
    private Long getDefaultProviderId() {
        // 从 ModelProviderService 获取第一个可用的 provider
        var providers = modelFactory.getAvailableProviderIds();
        if (providers.isEmpty()) {
            throw new IllegalStateException("没有可用的模型提供商，请先在模型提供商管理页面配置");
        }
        return providers.get(0);
    }
}
