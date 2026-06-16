package com.lightbot.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.BizException;
import com.lightbot.dto.RagSearchResultVO;
import com.lightbot.entity.Knowledge;
import com.lightbot.enums.ErrorCode;
import com.lightbot.model.ModelFactory;
import com.lightbot.dto.QaPairSearchResultVO;
import com.lightbot.service.EmbeddingService;
import com.lightbot.service.KnowledgePermissionHelper;
import com.lightbot.service.KnowledgeService;
import com.lightbot.service.QaPairService;
import com.lightbot.service.RagService;
import com.lightbot.service.SystemConfigService;
import com.lightbot.util.LlmTraceContext;
import com.lightbot.util.TextNormalizeUtil;
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
    private final KnowledgePermissionHelper permissionHelper;
    private final QaPairService qaPairService;
    private final EmbeddingModel embeddingModel;
    private final ModelFactory modelFactory;
    private final SystemConfigService systemConfigService;

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
        // 1.1 权限校验：需要成员权限
        permissionHelper.checkMember(knowledgeId);

        // 1.1 解析providerId（为空时使用默认提供商）
        Long actualProviderId = resolveProviderId(providerId);

        // 1.2 从知识库配置中读取检索参数
        int topK = parseRagTopK(knowledge);
        double threshold = parseRagThreshold(knowledge);
        log.info("[RAG] 问答开始: knowledgeId={}, providerId={}, topK={}, threshold={}, question={}",
                knowledgeId, actualProviderId, topK, threshold, question);

        // 2. 将问题文本向量化
        float[] queryVector = embedText(question);

        // 3. 在知识库中检索相似内容（阈值过滤下沉到SQL层，传 queryParams 支持 Milvus search_mode）
        Map<String, Object> mergedParams = buildSearchParams(knowledge, null, question);
        List<Map<String, Object>> results = ((EmbeddingServiceImpl) embeddingService)
                .searchSimilarSql(knowledgeId, queryVector, topK, threshold, mergedParams);
        log.info("[RAG] 向量检索完成(SQL过滤): threshold={}, 命中分块数={}", threshold, results.size());
        for (int i = 0; i < results.size(); i++) {
            Map<String, Object> row = results.get(i);
            String content = String.valueOf(row.get("content"));
            String preview = content.length() > 100 ? content.substring(0, 100) + "..." : content;
            log.info("[RAG] 检索分块[{}]: document={}, score={}, content={}", i, row.get("document_name"), row.get("score"), preview);
        }

        if (results.isEmpty()) {
            return "抱歉，在知识库中没有找到相关信息。";
        }

        // 4. 构建参考资料上下文
        String context = results.stream()
                .map(row -> String.format("【%s】\n%s", row.get("document_name"),
                        TextNormalizeUtil.normalizeForPrompt(String.valueOf(row.get("content")))))
                .collect(Collectors.joining("\n\n---\n\n"));

        // 5. 通过 ModelFactory 获取 ChatModel 并调用
        String systemPrompt = RAG_SYSTEM_PROMPT.replace("{context}", context);
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        messages.add(new UserMessage(question));

        ChatModel chatModel = modelFactory.getChatModel(actualProviderId);
        ChatResponse response = LlmTraceContext.callWithoutTrace(() -> chatModel.call(new Prompt(messages)));
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
        // 1.1 权限校验：需要成员权限
        permissionHelper.checkMember(knowledgeId);

        // 1.1 解析providerId（为空时使用默认提供商）
        Long actualProviderId = resolveProviderId(providerId);

        // 1.2 从知识库配置中读取检索参数
        int topK = parseRagTopK(knowledge);
        double threshold = parseRagThreshold(knowledge);
        log.info("[RAG] 流式问答开始: knowledgeId={}, providerId={}, topK={}, threshold={}, question={}",
                knowledgeId, actualProviderId, topK, threshold, question);

        // 2. 将问题文本向量化
        float[] queryVector = embedText(question);

        // 3. 在知识库中检索相似内容（阈值过滤下沉到SQL层，传 queryParams 支持 Milvus search_mode）
        Map<String, Object> mergedParams = buildSearchParams(knowledge, null, question);
        List<Map<String, Object>> results = ((EmbeddingServiceImpl) embeddingService)
                .searchSimilarSql(knowledgeId, queryVector, topK, threshold, mergedParams);
        log.info("[RAG] 向量检索完成(SQL过滤): threshold={}, 命中分块数={}", threshold, results.size());
        for (int i = 0; i < results.size(); i++) {
            Map<String, Object> row = results.get(i);
            String content = String.valueOf(row.get("content"));
            String preview = content.length() > 100 ? content.substring(0, 100) + "..." : content;
            log.info("[RAG] 检索分块[{}]: document={}, score={}, content={}", i, row.get("document_name"), row.get("score"), preview);
        }

        if (results.isEmpty()) {
            return Flux.just("抱歉，在知识库中没有找到相关信息。");
        }

        // 4. 构建参考资料上下文
        String context = results.stream()
                .map(row -> String.format("【%s】\n%s", row.get("document_name"),
                        TextNormalizeUtil.normalizeForPrompt(String.valueOf(row.get("content")))))
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

    @Override
    public List<RagSearchResultVO> search(Long knowledgeId, String question) {
        return search(knowledgeId, question, null);
    }

    @Override
    public List<RagSearchResultVO> search(Long knowledgeId, String question, Map<String, Object> overrides) {
        // 1. 校验知识库存在性
        Knowledge knowledge = knowledgeService.getById(knowledgeId);
        if (knowledge == null) {
            throw new BizException(ErrorCode.RAG_KNOWLEDGE_NOT_FOUND);
        }
        // 1.1 权限校验：需要成员权限
        permissionHelper.checkMember(knowledgeId);

        // 2. 解析检索参数：overrides > queryParams > config > 默认值
        int topK = resolveTopK(knowledge, overrides);
        double threshold = resolveThreshold(knowledge, overrides);
        boolean qaEnabled = resolveQaEnabled(knowledge, overrides);
        log.info("[RAG] 检索测试开始: knowledgeId={}, topK={}, threshold={}, qaEnabled={}, question={}",
                knowledgeId, topK, threshold, qaEnabled, question);

        // 3. 将问题文本向量化
        float[] queryVector = embedText(question);

        // 4. 并行检索 Chunk 和 QA Pair
        Map<String, Object> mergedParams = buildSearchParams(knowledge, overrides, question);

        java.util.concurrent.CompletableFuture<List<Map<String, Object>>> chunkFuture =
                java.util.concurrent.CompletableFuture.supplyAsync(() ->
                    ((EmbeddingServiceImpl) embeddingService)
                            .searchSimilarSql(knowledgeId, queryVector, topK, threshold, mergedParams)
                );

        java.util.concurrent.CompletableFuture<List<QaPairSearchResultVO>> qaFuture;
        if (qaEnabled) {
            int qaTopK = resolveQaTopK(knowledge, overrides);
            double qaThreshold = resolveQaThreshold(knowledge, overrides);
            qaFuture = java.util.concurrent.CompletableFuture.supplyAsync(() ->
                qaPairService.searchSimilar(knowledgeId, queryVector, qaTopK, qaThreshold)
            );
        } else {
            qaFuture = java.util.concurrent.CompletableFuture.completedFuture(java.util.List.of());
        }

        List<Map<String, Object>> chunkResults = chunkFuture.join();
        List<QaPairSearchResultVO> qaResults = qaFuture.join();
        log.info("[RAG] 检索测试完成: chunkCount={}, qaCount={}", chunkResults.size(), qaResults.size());

        // 5. 转为VO返回：QA 结果排在前面
        int rank = 0;
        List<RagSearchResultVO> voList = new ArrayList<>();

        for (QaPairSearchResultVO qa : qaResults) {
            RagSearchResultVO vo = new RagSearchResultVO();
            vo.setContent("【问答对】Q: " + qa.getQuestion() + "\nA: " + qa.getAnswer());
            vo.setRank(++rank);
            vo.setScore(qa.getScore());
            vo.setDocumentName("问答对");
            vo.setResultType("qa_pair");
            voList.add(vo);
        }

        for (Map<String, Object> row : chunkResults) {
            RagSearchResultVO vo = new RagSearchResultVO();
            vo.setContent((String) row.get("content"));
            vo.setRank(++rank);
            Object score = row.get("score");
            vo.setScore(score != null ? Math.round(((Number) score).doubleValue() * 10000.0) / 10000.0 : null);
            vo.setDocumentName((String) row.get("document_name"));
            Object documentId = row.get("document_id");
            vo.setDocumentId(documentId != null ? ((Number) documentId).longValue() : null);
            vo.setResultType("chunk");
            voList.add(vo);
        }
        return voList;
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
     * 解析providerId，为空时使用系统默认配置（或第一个可用的）
     */
    private Long resolveProviderId(Long providerId) {
        // 1. 优先使用传入的 providerId
        if (providerId != null) {
            return providerId;
        }

        // 2. 其次使用系统默认AI配置
        var defaultConfig = systemConfigService.getDefaultAiConfig();
        if (defaultConfig.getProviderId() != null) {
            return defaultConfig.getProviderId();
        }

        // 3. 最后使用第一个可用的提供商
        var providers = modelFactory.getAvailableProviderIds();
        if (providers.isEmpty()) {
            throw new BizException(ErrorCode.MODEL_PROVIDER_NOT_FOUND);
        }
        return providers.get(0);
    }

    /**
     * 解析 TopK 参数（无 overrides 版本，用于 ask/askStream）
     * 优先级：queryParams > config > 默认值
     */
    private int parseRagTopK(Knowledge knowledge) {
        return resolveTopK(knowledge, null);
    }

    /**
     * 解析 threshold 参数（无 overrides 版本，用于 ask/askStream）
     * 优先级：queryParams > config > 默认值
     */
    private double parseRagThreshold(Knowledge knowledge) {
        return resolveThreshold(knowledge, null);
    }

    /**
     * 解析 TopK 参数，支持 overrides 覆盖
     * 优先级：overrides > queryParams > config > 默认值
     */
    private int resolveTopK(Knowledge knowledge, Map<String, Object> overrides) {
        // 1. overrides 最高优先
        if (overrides != null) {
            Object val = overrides.get("final_top_k");
            if (val instanceof Number n) {
                return n.intValue();
            }
        }
        // 2. query_params 专有字段
        Map<String, Object> queryParams = parseJson(knowledge.getQueryParams());
        Object qpVal = queryParams.get("final_top_k");
        if (qpVal instanceof Number n) {
            return n.intValue();
        }
        // 3. 兼容旧 config 中的 ragTopK
        Map<String, Object> config = parseJson(knowledge.getConfig());
        Object cfgVal = config.get("ragTopK");
        if (cfgVal instanceof Number n) {
            return n.intValue();
        }
        // 4. 默认值
        return DEFAULT_TOP_K;
    }

    /**
     * 解析 threshold 参数，支持 overrides 覆盖
     * 优先级：overrides > queryParams > config > 默认值
     */
    private double resolveThreshold(Knowledge knowledge, Map<String, Object> overrides) {
        // 1. overrides 最高优先
        if (overrides != null) {
            Object val = overrides.get("similarity_threshold");
            if (val instanceof Number n) {
                return n.doubleValue();
            }
        }
        // 2. query_params 专有字段
        Map<String, Object> queryParams = parseJson(knowledge.getQueryParams());
        Object qpVal = queryParams.get("similarity_threshold");
        if (qpVal instanceof Number n) {
            return n.doubleValue();
        }
        // 3. 兼容旧 config 中的 ragThreshold
        Map<String, Object> config = parseJson(knowledge.getConfig());
        Object cfgVal = config.get("ragThreshold");
        if (cfgVal instanceof Number n) {
            return n.doubleValue();
        }
        // 4. 默认值
        return DEFAULT_THRESHOLD;
    }

    private boolean resolveQaEnabled(Knowledge knowledge, Map<String, Object> overrides) {
        if (overrides != null) {
            Object val = overrides.get("qa_enabled");
            if (val instanceof Boolean b) return b;
        }
        Map<String, Object> queryParams = parseJson(knowledge.getQueryParams());
        Object qpVal = queryParams.get("qa_enabled");
        if (qpVal instanceof Boolean b) return b;
        return true;
    }

    private int resolveQaTopK(Knowledge knowledge, Map<String, Object> overrides) {
        if (overrides != null) {
            Object val = overrides.get("qa_top_k");
            if (val instanceof Number n) return n.intValue();
        }
        Map<String, Object> queryParams = parseJson(knowledge.getQueryParams());
        Object qpVal = queryParams.get("qa_top_k");
        if (qpVal instanceof Number n) return n.intValue();
        Map<String, Object> config = parseJson(knowledge.getConfig());
        Object cfgVal = config.get("qaTopK");
        if (cfgVal instanceof Number n) return n.intValue();
        return 3;
    }

    private double resolveQaThreshold(Knowledge knowledge, Map<String, Object> overrides) {
        if (overrides != null) {
            Object val = overrides.get("qa_threshold");
            if (val instanceof Number n) return n.doubleValue();
        }
        Map<String, Object> queryParams = parseJson(knowledge.getQueryParams());
        Object qpVal = queryParams.get("qa_threshold");
        if (qpVal instanceof Number n) return n.doubleValue();
        Map<String, Object> config = parseJson(knowledge.getConfig());
        Object cfgVal = config.get("qaThreshold");
        if (cfgVal instanceof Number n) return n.doubleValue();
        return 0.85;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank() || "{}".equals(json)) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    /**
     * 构建检索参数：queryParams + overrides + query_text
     * <p>运行时覆盖 > 持久化配置 > 代码默认值</p>
     */
    private Map<String, Object> buildSearchParams(Knowledge knowledge, Map<String, Object> overrides, String question) {
        Map<String, Object> params = new java.util.HashMap<>(parseJson(knowledge.getQueryParams()));
        // 全量合并 overrides（运行时覆盖优先）
        if (overrides != null) {
            params.putAll(overrides);
        }
        params.put("query_text", question);
        return params;
    }
}
