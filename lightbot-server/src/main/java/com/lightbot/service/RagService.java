package com.lightbot.service;

import com.lightbot.entity.Knowledge;
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
 * RAG 检索增强生成服务
 *
 * @author finch
 * @since 2026-05-19
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private final EmbeddingService embeddingService;
    private final KnowledgeService knowledgeService;
    private final ChatModel chatModel;
    private final EmbeddingModel embeddingModel;

    private static final String RAG_SYSTEM_PROMPT = """
            你是 LightBot 智能助手。请基于以下参考资料回答用户的问题。
            如果参考资料中没有相关信息，请如实告知用户。

            参考资料：
            {context}
            """;

    /**
     * RAG 问答
     *
     * @param knowledgeId 知识库ID
     * @param question    用户问题
     * @return 回答
     */
    public String ask(Long knowledgeId, String question) {
        // 1. 获取知识库信息
        Knowledge knowledge = knowledgeService.getById(knowledgeId);
        if (knowledge == null) {
            throw new RuntimeException("知识库不存在");
        }

        // 2. 将问题向量化
        float[] queryVector = embedText(question);

        // 3. 检索相似内容
        List<Map<String, Object>> results = embeddingService.searchSimilar(
                knowledgeId, queryVector, 5, 0.5);

        if (results.isEmpty()) {
            return "抱歉，在知识库中没有找到相关信息。";
        }

        // 4. 构建上下文
        String context = results.stream()
                .map(row -> String.format("【%s】\n%s", row.get("document_name"), row.get("content")))
                .collect(Collectors.joining("\n\n---\n\n"));

        // 5. 调用模型生成回答
        String systemPrompt = RAG_SYSTEM_PROMPT.replace("{context}", context);
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        messages.add(new UserMessage(question));

        ChatResponse response = chatModel.call(new Prompt(messages));
        return response.getResult().getOutput().getText();
    }

    /**
     * 文本向量化
     */
    private float[] embedText(String text) {
        EmbeddingResponse response = embeddingModel.call(
                new EmbeddingRequest(List.of(text), null));
        return response.getResult().getOutput();
    }
}
