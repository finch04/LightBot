package com.lightbot.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.BizException;
import com.lightbot.constant.RagResultType;
import com.lightbot.dto.RagSearchResultVO;
import com.lightbot.entity.Knowledge;
import com.lightbot.enums.ErrorCode;
import com.lightbot.model.ModelFactory;
import com.lightbot.model.ProviderResolver;
import com.lightbot.dto.QaPairSearchResultVO;
import com.lightbot.service.EmbeddingService;
import com.lightbot.service.KnowledgeMemberService;
import com.lightbot.service.KnowledgeService;
import com.lightbot.service.QaPairService;
import com.lightbot.service.RagService;
import com.lightbot.service.SystemConfigService;
import com.lightbot.util.JsonUtil;
import com.lightbot.util.LlmTraceContext;
import com.lightbot.util.RagParamResolver;
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
    private final KnowledgeMemberService permissionHelper;
    private final QaPairService qaPairService;
    private final EmbeddingModel embeddingModel;
    private final ModelFactory modelFactory;
    private final SystemConfigService systemConfigService;
    private final ObjectMapper objectMapper;
    private final ProviderResolver providerResolver;
    private final RagParamResolver ragParamResolver;
    private static final String RAG_SYSTEM_PROMPT = """
            你是 LightBot 智能助手。请基于以下参考资料回答用户的问题。
            如果参考资料中没有相关信息，请如实告知用户。

            参考资料：
            {context}
            """;

    @Override
    public String ask(Long knowledgeId, String question, Long providerId) {
        // 1. 公共 pipeline：校验 + 向量检索 + 上下文构建
        RagPipelineResult pipeline = prepareRagPipeline(knowledgeId, question, providerId, "问答");
        if (pipeline == null) {
            return "抱歉，在知识库中没有找到相关信息。";
        }

        // 2. 同步调用 LLM
        ChatModel chatModel = modelFactory.getChatModel(pipeline.providerId);
        ChatResponse response = LlmTraceContext.callWithoutTrace(() -> chatModel.call(new Prompt(pipeline.messages)));
        String answer = response.getResult().getOutput().getText();
        log.info("[RAG] 问答完成: answerLength={}", answer != null ? answer.length() : 0);
        return answer;
    }

    @Override
    public Flux<String> askStream(Long knowledgeId, String question, Long providerId) {
        // 1. 公共 pipeline：校验 + 向量检索 + 上下文构建
        RagPipelineResult pipeline = prepareRagPipeline(knowledgeId, question, providerId, "流式问答");
        if (pipeline == null) {
            return Flux.just("抱歉，在知识库中没有找到相关信息。");
        }

        // 2. 流式调用 LLM
        ChatModel chatModel = modelFactory.getChatModel(pipeline.providerId);
        return chatModel.stream(new Prompt(pipeline.messages))
                .map(response -> response.getResult().getOutput().getText())
                .doOnComplete(() -> log.info("[RAG] 流式问答完成: knowledgeId={}", knowledgeId))
                .doOnError(e -> log.error("[RAG] 流式问答异常: knowledgeId={}, error={}", knowledgeId, e.getMessage()));
    }

    /**
     * RAG 公共 pipeline：知识库校验 → 向量检索 → 上下文构建
     *
     * @return pipeline 结果，检索无命中时返回 null
     */
    private RagPipelineResult prepareRagPipeline(Long knowledgeId, String question, Long providerId, String logLabel) {
        // 1. 校验知识库存在性
        Knowledge knowledge = knowledgeService.getById(knowledgeId);
        if (knowledge == null) {
            throw new BizException(ErrorCode.RAG_KNOWLEDGE_NOT_FOUND);
        }
        // 1.1 权限校验：需要成员权限
        permissionHelper.checkMember(knowledgeId);

        // 1.2 解析providerId（为空时使用默认提供商）
        Long actualProviderId = providerResolver.resolve(providerId);

        // 1.3 从知识库配置中读取检索参数
        int topK = ragParamResolver.resolveTopK(null, parseJson(knowledge.getQueryParams()), knowledge.getConfig(), RagParamResolver.DEFAULT_TOP_K);
        double threshold = ragParamResolver.resolveThreshold(null, parseJson(knowledge.getQueryParams()), knowledge.getConfig(), RagParamResolver.DEFAULT_THRESHOLD);
        log.info("[RAG] {}开始: knowledgeId={}, providerId={}, topK={}, threshold={}, question={}",
                logLabel, knowledgeId, actualProviderId, topK, threshold, question);

        // 2. 将问题文本向量化
        float[] queryVector = embedText(question);

        // 3. 在知识库中检索相似内容（阈值过滤下沉到SQL层，传 queryParams 支持 Milvus search_mode）
        Map<String, Object> mergedParams = buildSearchParams(knowledge, null, question);
        List<Map<String, Object>> results = embeddingService.searchSimilarSql(knowledgeId, queryVector, topK, threshold, mergedParams);
        log.info("[RAG] 向量检索完成(SQL过滤): threshold={}, 命中分块数={}", threshold, results.size());
        for (int i = 0; i < results.size(); i++) {
            Map<String, Object> row = results.get(i);
            String content = String.valueOf(row.get("content"));
            String preview = content.length() > 100 ? content.substring(0, 100) + "..." : content;
            log.info("[RAG] 检索分块[{}]: document={}, score={}, content={}", i, row.get("document_name"), row.get("score"), preview);
        }

        if (results.isEmpty()) {
            return null;
        }

        // 4. 构建参考资料上下文
        String context = results.stream()
                .map(row -> String.format("【%s】\n%s", row.get("document_name"),
                        TextNormalizeUtil.normalizeForPrompt(String.valueOf(row.get("content")))))
                .collect(Collectors.joining("\n\n---\n\n"));

        // 5. 构建消息列表
        String systemPrompt = RAG_SYSTEM_PROMPT.replace("{context}", context);
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        messages.add(new UserMessage(question));

        return new RagPipelineResult(actualProviderId, messages);
    }

    /** RAG pipeline 预处理结果 */
    private record RagPipelineResult(Long providerId, List<Message> messages) {}

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
        Map<String, Object> queryParams = parseJson(knowledge.getQueryParams());
        int topK = ragParamResolver.resolveTopK(overrides, queryParams, knowledge.getConfig(), RagParamResolver.DEFAULT_TOP_K);
        double threshold = ragParamResolver.resolveThreshold(overrides, queryParams, knowledge.getConfig(), RagParamResolver.DEFAULT_THRESHOLD);
        boolean qaEnabled = resolveQaEnabled(knowledge, overrides);
        log.info("[RAG] 检索测试开始: knowledgeId={}, topK={}, threshold={}, qaEnabled={}, question={}",
                knowledgeId, topK, threshold, qaEnabled, question);

        // 3. 将问题文本向量化
        float[] queryVector = embedText(question);

        // 4. 并行检索 Chunk 和 QA Pair
        Map<String, Object> mergedParams = buildSearchParams(knowledge, overrides, question);

        java.util.concurrent.CompletableFuture<List<Map<String, Object>>> chunkFuture =
                java.util.concurrent.CompletableFuture.supplyAsync(() ->
                    embeddingService.searchSimilarSql(knowledgeId, queryVector, topK, threshold, mergedParams)
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
            vo.setResultType(RagResultType.QA_PAIR);
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
            vo.setResultType(RagResultType.CHUNK);
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

    private Map<String, Object> parseJson(String json) {
        return JsonUtil.parseJsonToMap(objectMapper, json);
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
