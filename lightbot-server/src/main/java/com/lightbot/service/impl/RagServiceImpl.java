package com.lightbot.service.impl;

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

        // 2. 将问题文本向量化
        float[] queryVector = embedText(question);

        // 3. 在知识库中检索相似内容（Top-5，相似度阈值0.5）
        List<Map<String, Object>> results = embeddingService.searchSimilar(
                knowledgeId, queryVector, 5, 0.5);

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
        return response.getResult().getOutput().getText();
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
}
