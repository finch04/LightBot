package com.lightbot.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.BizException;
import com.lightbot.entity.Knowledge;
import com.lightbot.enums.ErrorCode;
import com.lightbot.model.ModelFactory;
import com.lightbot.service.EmbeddingService;
import com.lightbot.service.KnowledgeService;
import com.lightbot.service.RagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RAG 检索增强生成服务实现类
 * <p>流程：问题向量化 -> 相似度检索 -> 构建上下文 -> 调用模型生成回答</p>
 *
 * @author finch
 * @since 2026-05-19
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagServiceImpl implements RagService {

    private final EmbeddingService embeddingService;
    private final KnowledgeService knowledgeService;
    private final EmbeddingModel embeddingModel;
    private final ModelFactory modelFactory;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int DEFAULT_TOP_K = 5;
    private static final double DEFAULT_THRESHOLD = 0.5;

    private static final String RAG_SYSTEM_PROMPT = """
            你是 LightBot 智能助手。请基于以下参考资料回答用户的问题。
            如果参考资料中没有相关信息，请如实告知用户。

            参考资料：
            {context}
            """;

    @Override
    public String ask(Long knowledgeId, String question, Long providerId) {
        // 1. 校验知识库存在性
        Knowledge knowledge = knowledgeService.getById(knowledgeId);
        if (knowledge == null) {
            throw new BizException(ErrorCode.RAG_KNOWLEDGE_NOT_FOUND);
        }

        // 1.1 解析providerId（为空时使用默认提供商）
        Long actualProviderId = resolveProviderId(providerId);

        // 1.2 从知识库配置中读取检索参数
        int topK = parseRagTopK(knowledge);
        double threshold = parseRagThreshold(knowledge);
        log.info("[RAG] 问答开始: knowledgeId={}, providerId={}, topK={}, threshold={}, question={}",
                knowledgeId, actualProviderId, topK, threshold, question);

        // 2. 将问题文本向量化
        float[] queryVector = embedText(question);

        // 3. 在知识库中检索相似内容（先获取原始结果用于日志）
        List<Map<String, Object>> rawResults = embeddingService.searchSimilarRaw(
                knowledgeId, queryVector, topK);
        log.info("[RAG] 向量检索原始结果数={}", rawResults.size());
        for (int i = 0; i < rawResults.size(); i++) {
            Map<String, Object> row = rawResults.get(i);
            String content = String.valueOf(row.get("content"));
            String preview = content.length() > 100 ? content.substring(0, 100) + "..." : content;
            log.info("[RAG] 原始分块[{}]: document={}, score={}, content={}", i, row.get("document_name"), row.get("score"), preview);
        }

        // 3.1 过滤低于阈值的结果
        List<Map<String, Object>> results = rawResults.stream()
                .filter(row -> {
                    Object score = row.get("score");
                    return score != null && ((Number) score).doubleValue() >= threshold;
                })
                .toList();
        log.info("[RAG] 阈值过滤后: threshold={}, 命中分块数={}", threshold, results.size());

        if (results.isEmpty()) {
            return "抱歉，在知识库中没有找到相关信息。";
        }

        // 4. 构建参考资料上下文
        String context = results.stream()
                .map(row -> String.format("【%s】\n%s", row.get("document_name"), row.get("content")))
                .collect(Collectors.joining("\n\n---\n\n"));

        // 5. 通过 ModelFactory 获取 ChatModel 并调用
        String systemPrompt = RAG_SYSTEM_PROMPT.replace("{context}", context);
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        messages.add(new UserMessage(question));

        ChatModel chatModel = modelFactory.getChatModel(actualProviderId);
        ChatResponse response = chatModel.call(new Prompt(messages));
        String answer = response.getResult().getOutput().getText();
        log.info("[RAG] 问答完成: answerLength={}", answer != null ? answer.length() : 0);
        return answer;
    }

    @Override
    public Flux<String> askStream(Long knowledgeId, String question, Long providerId) {
        // 1. 校验知识库存在性
        Knowledge knowledge = knowledgeService.getById(knowledgeId);
        if (knowledge == null) {
            throw new BizException(ErrorCode.RAG_KNOWLEDGE_NOT_FOUND);
        }

        // 1.1 解析providerId（为空时使用默认提供商）
        Long actualProviderId = resolveProviderId(providerId);

        // 1.2 从知识库配置中读取检索参数
        int topK = parseRagTopK(knowledge);
        double threshold = parseRagThreshold(knowledge);
        log.info("[RAG] 流式问答开始: knowledgeId={}, providerId={}, topK={}, threshold={}, question={}",
                knowledgeId, actualProviderId, topK, threshold, question);

        // 2. 将问题文本向量化
        float[] queryVector = embedText(question);

        // 3. 在知识库中检索相似内容（先获取原始结果用于日志）
        List<Map<String, Object>> rawResults = embeddingService.searchSimilarRaw(
                knowledgeId, queryVector, topK);
        log.info("[RAG] 向量检索原始结果数={}", rawResults.size());
        for (int i = 0; i < rawResults.size(); i++) {
            Map<String, Object> row = rawResults.get(i);
            String content = String.valueOf(row.get("content"));
            String preview = content.length() > 100 ? content.substring(0, 100) + "..." : content;
            log.info("[RAG] 原始分块[{}]: document={}, score={}, content={}", i, row.get("document_name"), row.get("score"), preview);
        }

        // 3.1 过滤低于阈值的结果
        List<Map<String, Object>> results = rawResults.stream()
                .filter(row -> {
                    Object score = row.get("score");
                    return score != null && ((Number) score).doubleValue() >= threshold;
                })
                .toList();
        log.info("[RAG] 阈值过滤后: threshold={}, 命中分块数={}", threshold, results.size());

        if (results.isEmpty()) {
            return Flux.just("抱歉，在知识库中没有找到相关信息。");
        }

        // 4. 构建参考资料上下文
        String context = results.stream()
                .map(row -> String.format("【%s】\n%s", row.get("document_name"), row.get("content")))
                .collect(Collectors.joining("\n\n---\n\n"));

        // 5. 通过 ModelFactory 获取 ChatModel 并流式调用
        String systemPrompt = RAG_SYSTEM_PROMPT.replace("{context}", context);
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        messages.add(new UserMessage(question));

        ChatModel chatModel = modelFactory.getChatModel(actualProviderId);
        return chatModel.stream(new Prompt(messages))
                .map(response -> response.getResult().getOutput().getText())
                .doOnComplete(() -> log.info("[RAG] 流式问答完成: knowledgeId={}", knowledgeId))
                .doOnError(e -> log.error("[RAG] 流式问答异常: knowledgeId={}, error={}", knowledgeId, e.getMessage()));
    }

    /**
     * 文本向量化：调用EmbeddingModel将文本转为向量
     */
    private float[] embedText(String text) {
        EmbeddingResponse response = embeddingModel.call(
                new EmbeddingRequest(List.of(text), null));
        return response.getResult().getOutput();
    }

    /**
     * 解析providerId，为空时使用默认提供商（第一个可用的）
     */
    private Long resolveProviderId(Long providerId) {
        if (providerId != null) {
            return providerId;
        }
        var providers = modelFactory.getAvailableProviderIds();
        if (providers.isEmpty()) {
            throw new BizException(ErrorCode.MODEL_PROVIDER_NOT_FOUND);
        }
        return providers.get(0);
    }

    /**
     * 从知识库配置中解析 RAG Top K（默认5）
     */
    private int parseRagTopK(Knowledge knowledge) {
        Map<String, Object> config = parseConfig(knowledge.getConfig());
        Object val = config.get("ragTopK");
        return val instanceof Number ? ((Number) val).intValue() : DEFAULT_TOP_K;
    }

    /**
     * 从知识库配置中解析 RAG 相似度阈值（默认0.5）
     */
    private double parseRagThreshold(Knowledge knowledge) {
        Map<String, Object> config = parseConfig(knowledge.getConfig());
        Object val = config.get("ragThreshold");
        return val instanceof Number ? ((Number) val).doubleValue() : DEFAULT_THRESHOLD;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfig(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(configJson, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }
}
