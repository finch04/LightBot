package com.lightbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.constant.ConfigKeys;
import com.lightbot.dto.ChatRequest;
import com.lightbot.dto.RagReferenceVO;
import com.lightbot.entity.Agent;
import com.lightbot.entity.ChatSession;
import com.lightbot.entity.Knowledge;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    private final KnowledgeService knowledgeService;
    private final EmbeddingModel embeddingModel;
    private final ModelFactory modelFactory;
    private final TaskExecutor taskExecutor;

    private static final String DEFAULT_SYSTEM_PROMPT = "你是 LightBot 智能助手，请用中文回答用户问题。回答应简洁准确，遇到不确定的信息请如实告知。";

    private static final String RAG_CONTEXT_TEMPLATE = """
            请基于以下参考资料回答用户的问题。
            如果参考资料中没有相关信息，请如实告知用户。

            参考资料：
            %s
            """;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 状态消息前缀，前端通过此前缀识别状态消息 */
    private static final String STATUS_PREFIX = "[STATUS]";

    /** metadata消息前缀，前端通过此前缀识别metadata消息 */
    private static final String METADATA_PREFIX = "[METADATA]";

    /** 并行检索线程池 */
    private static final ExecutorService RAG_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "rag-search");
        t.setDaemon(true);
        return t;
    });

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

        log.info("[Chat] 用户消息: sessionId={}, agentId={}, message={}", sessionId,
                agent != null ? agent.getId() : null, request.getMessage());

        // 6. 调用模型获取回复
        ChatResponse response = chatModel.call(new Prompt(messages, options));
        String reply = response.getResult().getOutput().getText();

        log.info("[Chat] AI回复: sessionId={}, length={}", sessionId, reply != null ? reply.length() : 0);

        // 7. 持久化用户消息和AI回复
        saveMessage(sessionId, MessageRole.USER, request.getMessage());
        saveMessage(sessionId, MessageRole.ASSISTANT, reply);

        // 8. 异步生成标题
        taskExecutor.execute(() -> generateTitle(sessionId));

        return reply;
    }

    @Override
    public Flux<String> chatStream(ChatRequest request) {
        long startTime = System.currentTimeMillis();

        // 1. 解析会话ID
        Long sessionId = resolveSessionId(request.getSessionId(), request.getAgentId());

        // 2. 加载Agent配置
        Agent agent = loadAgent(request.getAgentId());

        // 3. 解析config获取providerId和配置
        Map<String, Object> configMap = parseConfig(agent != null ? agent.getConfig() : null);
        Long providerId = getProviderId(configMap);

        log.info("[Chat] 流式对话开始: sessionId={}, agentId={}, providerId={}, message={}",
                sessionId, agent != null ? agent.getId() : null, providerId, request.getMessage());

        // 4. 先保存用户消息
        saveMessage(sessionId, MessageRole.USER, request.getMessage());

        // 5. 构建消息列表（含RAG检索）
        long ragStart = System.currentTimeMillis();
        List<org.springframework.ai.chat.messages.Message> messages = buildMessages(sessionId, request.getMessage(), agent);
        long ragElapsed = System.currentTimeMillis() - ragStart;

        // 5.1 获取RAG引用信息（用于保存到metadata）
        String ragMetadata = null;
        if (agent != null) {
            List<Map<String, Object>> ragResults = getRagSearchResults(agent.getId(), request.getMessage());
            if (!ragResults.isEmpty()) {
                List<RagReferenceVO> refs = ragResults.stream().map(row -> {
                    RagReferenceVO vo = new RagReferenceVO();
                    vo.setDocumentName((String) row.get("document_name"));
                    String content = (String) row.get("content");
                    vo.setContentPreview(content != null && content.length() > 200
                            ? content.substring(0, 200) + "..."
                            : content);
                    Object score = row.get("score");
                    vo.setScore(score != null ? ((Number) score).doubleValue() : null);
                    Object knowledgeId = row.get("knowledge_id");
                    vo.setKnowledgeId(knowledgeId != null ? ((Number) knowledgeId).longValue() : null);
                    Object documentId = row.get("document_id");
                    vo.setDocumentId(documentId != null ? ((Number) documentId).longValue() : null);
                    Object chunkId = row.get("chunk_id");
                    vo.setChunkId(chunkId != null ? ((Number) chunkId).longValue() : null);
                    return vo;
                }).toList();
                try {
                    ragMetadata = OBJECT_MAPPER.writeValueAsString(Map.of("ragReferences", refs));
                } catch (Exception e) {
                    log.warn("[Chat] 序列化RAG引用失败: {}", e.getMessage());
                }
            }
        }

        // 6. 通过 ModelFactory 获取 ChatModel 和 ChatOptions
        ChatModel chatModel = modelFactory.getChatModel(providerId);
        ChatOptions options = modelFactory.buildChatOptions(providerId, configMap);

        // 7. 发送状态：RAG检索完成，开始思考
        String ragStatus = agent != null && agentKnowledgeService.getKnowledgeIds(agent.getId()).isEmpty()
                ? "正在思考..."
                : String.format("知识库检索完成（%dms），正在思考...", ragElapsed);

        // 8. 流式调用模型，收集完整回复后持久化
        Flux<ChatResponse> stream = chatModel.stream(new Prompt(messages, options));

        StringBuilder fullReply = new StringBuilder();
        String finalRagMetadata = ragMetadata;
        return Flux.concat(
                // 先发送状态消息
                Flux.just(STATUS_PREFIX + ragStatus),
                // 再发送流式文本
                stream.map(response -> {
                    if (response.getResult() == null || response.getResult().getOutput() == null) {
                        return "";
                    }
                    String delta = response.getResult().getOutput().getText();
                    if (delta == null) {
                        return "";
                    }
                    fullReply.append(delta);
                    return delta;
                }),
                // 流式结束后发送metadata（如果有）
                Flux.defer(() -> {
                    if (finalRagMetadata != null) {
                        return Flux.just(METADATA_PREFIX + finalRagMetadata);
                    }
                    return Flux.empty();
                })
        ).doOnNext(chunk -> {
            if (!chunk.isEmpty() && !chunk.startsWith(STATUS_PREFIX) && !chunk.startsWith(METADATA_PREFIX)) {
                log.debug("[Chat] 流式chunk: sessionId={}, chunk={}", sessionId, chunk);
            }
        }).doOnComplete(() -> {
            long totalElapsed = System.currentTimeMillis() - startTime;
            log.info("[Chat] 流式对话完成: sessionId={}, replyLength={}, totalElapsed={}ms",
                    sessionId, fullReply.length(), totalElapsed);
            saveMessage(sessionId, MessageRole.ASSISTANT, fullReply.toString(), finalRagMetadata);
            taskExecutor.execute(() -> generateTitle(sessionId));
        }).doOnError(e -> {
            log.error("[Chat] 流式对话异常: sessionId={}, error={}", sessionId, e.getMessage(), e);
        });
    }

    /**
     * 加载Agent配置。
     * agentId非空时加载指定Agent；为空时查询用户的默认Agent。
     */
    private Agent loadAgent(Long agentId) {
        // 1. 指定了agentId，直接加载
        if (agentId != null) {
            Agent agent = agentService.getById(agentId);
            if (agent == null) {
                log.warn("[Chat] Agent不存在，agentId={}", agentId);
            }
            return agent;
        }

        // 2. 未指定agentId，查询用户的默认Agent
        long userId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong();
        return agentService.getDefaultAgent(userId);
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
     * 从config Map中获取providerId。
     * 若Agent未配置providerId，自动使用第一个可用的模型提供商。
     */
    private Long getProviderId(Map<String, Object> configMap) {
        Object providerId = configMap.get(ConfigKeys.Agent.PROVIDER_ID);
        if (providerId != null) {
            return providerId instanceof Number ? ((Number) providerId).longValue() : Long.parseLong(providerId.toString());
        }

        // 自动使用第一个可用的模型提供商
        var providers = modelFactory.getAvailableProviderIds();
        if (providers.isEmpty()) {
            throw new IllegalArgumentException("请先在「模型提供商管理」中配置至少一个模型提供商");
        }
        log.info("[Chat] Agent未配置providerId，使用默认提供商: id={}", providers.get(0));
        return providers.get(0);
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

        // 2. RAG上下文：如果Agent绑定了知识库，检索相关上下文（并行检索）
        if (agent != null) {
            String ragContext = retrieveRagContextParallel(agent.getId(), userMessage);
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
     * RAG并行检索：并行查询多个知识库，提升检索速度
     */
    private String retrieveRagContextParallel(Long agentId, String question) {
        List<Long> knowledgeIds = agentKnowledgeService.getKnowledgeIds(agentId);
        if (knowledgeIds.isEmpty()) {
            return null;
        }

        try {
            // 1. 并行向量化问题
            float[] queryVector = embedText(question);

            // 2. 并行检索多个知识库（使用各知识库配置的 topK 和 threshold）
            List<CompletableFuture<List<Map<String, Object>>>> futures = knowledgeIds.stream()
                    .map(knowledgeId -> CompletableFuture.supplyAsync(() -> {
                        try {
                            Knowledge knowledge = knowledgeService.getById(knowledgeId);
                            int topK = parseRagTopK(knowledge);
                            double threshold = parseRagThreshold(knowledge);
                            return embeddingService.searchSimilar(knowledgeId, queryVector, topK, threshold);
                        } catch (Exception e) {
                            log.warn("[Chat] 知识库检索失败: knowledgeId={}, error={}", knowledgeId, e.getMessage());
                            return List.<Map<String, Object>>of();
                        }
                    }, RAG_EXECUTOR))
                    .toList();

            // 3. 等待所有检索完成
            List<Map<String, Object>> allResults = futures.stream()
                    .map(CompletableFuture::join)
                    .flatMap(List::stream)
                    .toList();

            if (allResults.isEmpty()) {
                return null;
            }

            // 4. 构建上下文
            return allResults.stream()
                    .map(row -> String.format("【%s】\n%s", row.get("document_name"), row.get("content")))
                    .collect(Collectors.joining("\n\n---\n\n"));
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
        saveMessage(sessionId, role, content, null);
    }

    /**
     * 持久化消息并更新会话统计（含metadata）
     */
    private void saveMessage(Long sessionId, MessageRole role, String content, String metadata) {
        Message msg = new Message();
        msg.setSessionId(sessionId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setContentType(ContentType.TEXT);
        msg.setTokenCount(0);
        msg.setMetadata(metadata);
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

            // 4. 使用会话绑定的Agent模型生成标题，未绑定则使用默认提供商
            Long providerId = resolveTitleProviderId(session.getAgentId());
            List<org.springframework.ai.chat.messages.Message> promptMessages = new ArrayList<>();
            promptMessages.add(new SystemMessage("你是一个标题生成助手，只输出标题，不要任何其他内容。"));
            promptMessages.add(new UserMessage("请根据以下对话内容生成一个简短的标题（不超过20个字，不要加引号）：\n" + conversationText));

            ChatResponse response = modelFactory.getChatModel(providerId).call(new Prompt(promptMessages));
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
     * 解析标题生成使用的providerId：优先使用会话绑定Agent的模型，未绑定则降级为默认提供商
     */
    private Long resolveTitleProviderId(Long agentId) {
        if (agentId != null) {
            Agent agent = agentService.getById(agentId);
            if (agent != null) {
                Map<String, Object> configMap = parseConfig(agent.getConfig());
                Long providerId = getProviderId(configMap);
                if (providerId != null) {
                    return providerId;
                }
            }
        }
        return getDefaultProviderId();
    }

    /**
     * 获取默认providerId（兜底方案）
     * 优先使用第一个可用的 ModelProvider
     */
    private Long getDefaultProviderId() {
        var providers = modelFactory.getAvailableProviderIds();
        if (providers.isEmpty()) {
            throw new IllegalStateException("没有可用的模型提供商，请先在模型提供商管理页面配置");
        }
        return providers.get(0);
    }

    @Override
    public List<RagReferenceVO> getRagReferences(Long sessionId, Long agentId, String question) {
        // 1. 加载Agent配置
        Agent agent = loadAgent(agentId);
        if (agent == null) {
            return List.of();
        }

        // 2. 获取RAG检索结果
        List<Map<String, Object>> searchResults = getRagSearchResults(agent.getId(), question);

        // 3. 转换为VO
        return searchResults.stream().map(row -> {
            RagReferenceVO vo = new RagReferenceVO();
            vo.setDocumentName((String) row.get("document_name"));
            String content = (String) row.get("content");
            vo.setContentPreview(content != null && content.length() > 200
                    ? content.substring(0, 200) + "..."
                    : content);
            Object score = row.get("score");
            vo.setScore(score != null ? ((Number) score).doubleValue() : null);
            Object knowledgeId = row.get("knowledge_id");
            vo.setKnowledgeId(knowledgeId != null ? ((Number) knowledgeId).longValue() : null);
            Object documentId = row.get("document_id");
            vo.setDocumentId(documentId != null ? ((Number) documentId).longValue() : null);
            Object chunkId = row.get("chunk_id");
            vo.setChunkId(chunkId != null ? ((Number) chunkId).longValue() : null);
            return vo;
        }).toList();
    }

    /**
     * 执行RAG检索，返回原始结果（用于获取引用信息）
     */
    private List<Map<String, Object>> getRagSearchResults(Long agentId, String question) {
        List<Long> knowledgeIds = agentKnowledgeService.getKnowledgeIds(agentId);
        if (knowledgeIds.isEmpty()) {
            return List.of();
        }

        try {
            float[] queryVector = embedText(question);
            List<Map<String, Object>> allResults = new ArrayList<>();

            // 并行检索（使用各知识库配置的 topK 和 threshold）
            List<CompletableFuture<List<Map<String, Object>>>> futures = knowledgeIds.stream()
                    .map(knowledgeId -> CompletableFuture.supplyAsync(() -> {
                        try {
                            Knowledge knowledge = knowledgeService.getById(knowledgeId);
                            int topK = parseRagTopK(knowledge);
                            double threshold = parseRagThreshold(knowledge);
                            return embeddingService.searchSimilar(knowledgeId, queryVector, topK, threshold);
                        } catch (Exception e) {
                            log.warn("[Chat] 知识库检索失败: knowledgeId={}, error={}", knowledgeId, e.getMessage());
                            return List.<Map<String, Object>>of();
                        }
                    }, RAG_EXECUTOR))
                    .toList();

            futures.forEach(f -> allResults.addAll(f.join()));

            return allResults;
        } catch (Exception e) {
            log.warn("[Chat] RAG检索失败: {}", e.getMessage());
            return List.of();
        }
    }

    private static final int DEFAULT_RAG_TOP_K = 5;
    private static final double DEFAULT_RAG_THRESHOLD = 0.5;

    /**
     * 从知识库配置中解析 RAG Top K
     */
    private int parseRagTopK(Knowledge knowledge) {
        if (knowledge == null) {
            return DEFAULT_RAG_TOP_K;
        }
        Map<String, Object> config = parseConfig(knowledge.getConfig());
        Object val = config.get("ragTopK");
        return val instanceof Number ? ((Number) val).intValue() : DEFAULT_RAG_TOP_K;
    }

    /**
     * 从知识库配置中解析 RAG 相似度阈值
     */
    private double parseRagThreshold(Knowledge knowledge) {
        if (knowledge == null) {
            return DEFAULT_RAG_THRESHOLD;
        }
        Map<String, Object> config = parseConfig(knowledge.getConfig());
        Object val = config.get("ragThreshold");
        return val instanceof Number ? ((Number) val).doubleValue() : DEFAULT_RAG_THRESHOLD;
    }
}
