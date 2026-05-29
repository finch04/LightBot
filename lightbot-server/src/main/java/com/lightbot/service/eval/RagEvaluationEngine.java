package com.lightbot.service.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.entity.Chunk;
import com.lightbot.entity.EvalRagBenchmarkItem;
import com.lightbot.entity.EvalRagResultDetail;
import com.lightbot.model.ModelFactory;
import com.lightbot.service.ChunkService;
import com.lightbot.service.EmbeddingService;
import com.lightbot.service.SystemConfigService;
import com.lightbot.util.LlmTraceContext;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG 评估引擎
 * <p>移植自 Yuxi 项目的 evaluator.py + metrics.py + benchmark_generation.py</p>
 *
 * @author finch
 * @since 2026-05-28
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagEvaluationEngine {

    private final ChunkService chunkService;
    private final EmbeddingService embeddingService;
    private final EmbeddingModel embeddingModel;
    private final ModelFactory modelFactory;
    private final SystemConfigService systemConfigService;
    private final ObjectMapper objectMapper;

    private static final int[] K_VALUES = {1, 3, 5, 10};

    /** LLM 调用间隔（毫秒），避免触发 429 限流 */
    private static final long LLM_CALL_DELAY_MS = 1000;

    // ==================== Benchmark 生成 ====================

    private static final String BENCHMARK_GEN_PROMPT = """
            你将基于以下上下文生成一个可由上下文准确回答的问题与标准答案。
            仅返回一个JSON对象，不要包含其他文字。
            键为 query、gold_answer、gold_chunk_ids。gold_chunk_ids 必须是上述上下文片段的ID子集。

            上下文：
            %s
            """;

    /**
     * AI 自动生成评估基准题目
     *
     * @param knowledgeId   知识库ID
     * @param count         生成数量
     * @param providerId    模型提供商ID
     * @param modelId       模型ID
     * @param neighborCount 相似chunks数量（anchor前后各取N个邻居）
     * @return 生成的题目列表
     */
    public List<EvalRagBenchmarkItem> generateBenchmarkItems(Long knowledgeId, int count,
                                                              Long providerId, String modelId, int neighborCount,
                                                              java.util.function.Consumer<Integer> progressCallback) {
        // 1. 收集所有已向量化的 chunk
        List<Chunk> allChunks = chunkService.list(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Chunk>()
                        .eq(Chunk::getKnowledgeId, knowledgeId)
                        .eq(Chunk::getStatus, com.lightbot.enums.ChunkStatus.VECTORIZED)
                        .select(Chunk::getId, Chunk::getContent));
        if (allChunks.isEmpty()) {
            return List.of();
        }

        // 2. 构建 chunkId → content 映射
        Map<Long, String> chunkContentMap = new HashMap<>();
        for (Chunk c : allChunks) {
            chunkContentMap.put(c.getId(), c.getContent());
        }
        List<Long> chunkIds = new ArrayList<>(chunkContentMap.keySet());

        // 3. 逐条生成
        List<EvalRagBenchmarkItem> items = new ArrayList<>();
        int maxAttempts = Math.max(count * 5, 50);
        Random random = new Random();

        for (int attempt = 0; attempt < maxAttempts && items.size() < count; attempt++) {
            try {
                // 随机选一个 anchor chunk
                Long anchorId = chunkIds.get(random.nextInt(chunkIds.size()));
                String anchorContent = chunkContentMap.get(anchorId);

                // 构建上下文（anchor + 附近 chunk）
                List<Long> contextIds = new ArrayList<>();
                contextIds.add(anchorId);
                // 取 anchor 前后各 neighborCount 个 chunk 作为邻居
                int anchorIdx = chunkIds.indexOf(anchorId);
                for (int offset = -neighborCount; offset <= neighborCount; offset++) {
                    if (offset == 0) continue;
                    int neighborIdx = anchorIdx + offset;
                    if (neighborIdx >= 0 && neighborIdx < chunkIds.size()) {
                        contextIds.add(chunkIds.get(neighborIdx));
                    }
                }

                StringBuilder ctxBuilder = new StringBuilder();
                for (Long cid : contextIds) {
                    ctxBuilder.append("片段ID=").append(cid).append("\n")
                            .append(chunkContentMap.get(cid)).append("\n\n");
                }

                String prompt = String.format(BENCHMARK_GEN_PROMPT, ctxBuilder.toString());
                String result = callLlm(providerId, prompt, modelId);
                if (result == null || result.isBlank()) {
                    continue;
                }

                // 解析 JSON
                JsonNode node = objectMapper.readTree(extractJson(result));
                if (!node.has("query") || !node.has("gold_answer") || !node.has("gold_chunk_ids")) {
                    continue;
                }

                String query = node.get("query").asText("");
                String goldAnswer = node.get("gold_answer").asText("");
                if (query.isBlank() || goldAnswer.isBlank()) {
                    continue;
                }

                // 解析 gold_chunk_ids 并过滤为有效 ID
                Set<Long> validIds = new HashSet<>(contextIds);
                List<String> goldChunkIds = new ArrayList<>();
                for (JsonNode idNode : node.get("gold_chunk_ids")) {
                    Long cid = idNode.asLong();
                    if (validIds.contains(cid)) {
                        goldChunkIds.add(String.valueOf(cid));
                    }
                }
                if (goldChunkIds.isEmpty()) {
                    continue;
                }

                EvalRagBenchmarkItem item = new EvalRagBenchmarkItem();
                item.setQuery(query);
                item.setGoldAnswer(goldAnswer);
                item.setGoldChunkIds(objectMapper.writeValueAsString(goldChunkIds));
                item.setSortOrder(items.size());
                items.add(item);

                // 回调进度：30% ~ 90% 按已完成题目数线性增长
                if (progressCallback != null) {
                    int progress = 30 + (int) ((items.size() / (double) count) * 60);
                    progressCallback.accept(progress);
                }

            } catch (Exception e) {
                log.debug("[RagEval] 生成基准题目失败 attempt={}: {}", attempt, e.getMessage());
            }
        }

        return items;
    }

    // ==================== 评估运行 ====================

    /**
     * 评估单个问题
     */
    public EvalRagResultDetail evaluateQuestion(EvalRagBenchmarkItem item, Long knowledgeId,
                                                  Long answerProviderId, String answerModelId,
                                                  Long judgeProviderId, String judgeModelId) {
        EvalRagResultDetail detail = new EvalRagResultDetail();
        detail.setQuery(item.getQuery());
        detail.setGoldChunkIds(item.getGoldChunkIds());
        detail.setGoldAnswer(item.getGoldAnswer());

        // 1. 向量检索
        float[] queryVector = embedText(item.getQuery());
        List<Map<String, Object>> searchResults = queryVector != null
                ? embeddingService.searchSimilarSql(knowledgeId, queryVector, 10, 0.0)
                : List.of();

        List<String> retrievedChunkIds = new ArrayList<>();
        for (Map<String, Object> row : searchResults) {
            Object chunkId = row.get("chunk_id");
            if (chunkId != null) {
                retrievedChunkIds.add(String.valueOf(((Number) chunkId).longValue()));
            }
        }
        detail.setRetrievedChunkIds(objectMapper.valueToTree(retrievedChunkIds).toString());

        // 2. 计算检索指标
        List<String> goldChunkIds = parseJsonStringList(item.getGoldChunkIds());
        Map<String, Double> retrievalScores = new LinkedHashMap<>();
        if (!goldChunkIds.isEmpty()) {
            for (int k : K_VALUES) {
                retrievalScores.put("precision@" + k, precisionAtK(retrievedChunkIds, goldChunkIds, k));
                retrievalScores.put("recall@" + k, recallAtK(retrievedChunkIds, goldChunkIds, k));
                retrievalScores.put("f1@" + k, f1AtK(retrievedChunkIds, goldChunkIds, k));
            }
        }
        try {
            detail.setRetrievalScores(objectMapper.writeValueAsString(retrievalScores));
        } catch (Exception e) {
            detail.setRetrievalScores("{}");
        }

        // 3. 生成答案（如有 answerProviderId）
        if (answerProviderId != null && !searchResults.isEmpty()) {
            String generatedAnswer = generateAnswer(item.getQuery(), searchResults, answerProviderId, answerModelId);
            detail.setGeneratedAnswer(generatedAnswer);

            // 4. LLM Judge（如有 gold_answer + judgeProviderId）
            if (item.getGoldAnswer() != null && !item.getGoldAnswer().isBlank()
                    && judgeProviderId != null && generatedAnswer != null) {
                Map<String, Object> judgeResult = judgeCorrectness(
                        item.getQuery(), item.getGoldAnswer(), generatedAnswer, judgeProviderId, judgeModelId);
                detail.setAnswerScore(((Number) judgeResult.getOrDefault("score", 0.0)).doubleValue());
                detail.setAnswerReasoning((String) judgeResult.getOrDefault("reasoning", ""));
            }
        }

        return detail;
    }

    /**
     * 聚合所有明细的指标
     */
    public Map<String, Object> aggregateMetrics(List<EvalRagResultDetail> details) {
        Map<String, Double> retrievalAgg = new LinkedHashMap<>();
        for (int k : K_VALUES) {
            retrievalAgg.put("recall@" + k, 0.0);
            retrievalAgg.put("f1@" + k, 0.0);
        }

        int retrievalCount = 0;
        double totalAnswerScore = 0;
        int answerCount = 0;

        for (EvalRagResultDetail d : details) {
            // 聚合检索指标
            if (d.getRetrievalScores() != null && !"{}".equals(d.getRetrievalScores())) {
                try {
                    Map<String, Double> scores = objectMapper.readValue(d.getRetrievalScores(),
                            new TypeReference<>() {});
                    for (int k : K_VALUES) {
                        retrievalAgg.merge("recall@" + k, scores.getOrDefault("recall@" + k, 0.0), Double::sum);
                        retrievalAgg.merge("f1@" + k, scores.getOrDefault("f1@" + k, 0.0), Double::sum);
                    }
                    retrievalCount++;
                } catch (Exception ignored) {
                }
            }

            // 聚合答案评分
            if (d.getAnswerScore() != null) {
                totalAnswerScore += d.getAnswerScore();
                answerCount++;
            }
        }

        // 取平均
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Double> avgRetrieval = new LinkedHashMap<>();
        if (retrievalCount > 0) {
            for (Map.Entry<String, Double> e : retrievalAgg.entrySet()) {
                avgRetrieval.put(e.getKey(), Math.round(e.getValue() / retrievalCount * 10000.0) / 10000.0);
            }
        }
        result.put("retrieval", avgRetrieval);

        if (answerCount > 0) {
            Map<String, Object> answerAgg = new LinkedHashMap<>();
            answerAgg.put("accuracy", Math.round(totalAnswerScore / answerCount * 10000.0) / 10000.0);
            answerAgg.put("total", answerCount);
            answerAgg.put("correct", (int) totalAnswerScore);
            result.put("answer", answerAgg);
        }

        // 综合分：所有检索指标平均
        double overallScore = avgRetrieval.values().stream()
                .mapToDouble(Double::doubleValue).average().orElse(0.0);
        result.put("overallScore", Math.round(overallScore * 10000.0) / 10000.0);

        return result;
    }

    // ==================== 检索指标 ====================

    static double precisionAtK(List<String> retrieved, List<String> relevant, int k) {
        if (retrieved.isEmpty() || k <= 0) return 0.0;
        Set<String> retrievedSet = new HashSet<>(retrieved.subList(0, Math.min(k, retrieved.size())));
        Set<String> relevantSet = new HashSet<>(relevant);
        long hits = retrievedSet.stream().filter(relevantSet::contains).count();
        return (double) hits / k;
    }

    static double recallAtK(List<String> retrieved, List<String> relevant, int k) {
        if (relevant.isEmpty()) return 0.0;
        Set<String> retrievedSet = new HashSet<>(retrieved.subList(0, Math.min(k, retrieved.size())));
        Set<String> relevantSet = new HashSet<>(relevant);
        long hits = retrievedSet.stream().filter(relevantSet::contains).count();
        return (double) hits / relevantSet.size();
    }

    static double f1AtK(List<String> retrieved, List<String> relevant, int k) {
        double p = precisionAtK(retrieved, relevant, k);
        double r = recallAtK(retrieved, relevant, k);
        if (p + r == 0) return 0.0;
        return 2 * p * r / (p + r);
    }

    // ==================== LLM 调用 ====================

    private static final String JUDGE_PROMPT = """
            你是一个公正的评判者，请评估AI生成的答案相对于标准答案的准确性。

            问题：%s

            标准答案：
            %s

            AI生成的答案：
            %s

            评分标准（0.0 ~ 1.0 连续分值）：
            - 1.0：核心事实完全正确，与标准答案一致
            - 0.8 ~ 0.9：核心事实正确，有少量非关键信息遗漏或多余
            - 0.5 ~ 0.7：部分核心事实正确，但有明显遗漏或部分错误
            - 0.2 ~ 0.4：仅少部分事实正确，大部分关键信息缺失或错误
            - 0.0：完全错误、无关或与标准答案矛盾

            评判规则：
            1. 忽略措辞、标点符号、格式上的差异
            2. 只关注核心事实是否准确
            3. 如果AI回答"无法回答"或类似表述，给 0.0

            请返回以下JSON格式的结果（不要包含其他文本、Markdown 或注释）：
            {"score": 0.85, "reasoning": "简要说明判定理由"}
            """;

    private static final String ANSWER_GEN_PROMPT = """
            基于以下参考文档回答用户问题。如果文档中没有相关信息，请回答"根据已有信息无法回答该问题"。

            参考文档：
            %s

            用户问题：%s
            """;

    private String generateAnswer(String query, List<Map<String, Object>> searchResults,
                                    Long providerId, String modelId) {
        StringBuilder docs = new StringBuilder();
        int rank = 0;
        for (Map<String, Object> row : searchResults) {
            docs.append("[").append(++rank).append("] ")
                    .append(row.get("content")).append("\n\n");
        }
        String prompt = String.format(ANSWER_GEN_PROMPT, docs, query);
        return callLlm(providerId, prompt, modelId);
    }

    private Map<String, Object> judgeCorrectness(String query, String goldAnswer,
                                                   String generatedAnswer, Long providerId, String modelId) {
        String prompt = String.format(JUDGE_PROMPT, query, goldAnswer, generatedAnswer);
        String result = callLlm(providerId, prompt, modelId);
        if (result == null || result.isBlank()) {
            return Map.of("score", 0.0, "reasoning", "LLM 评判调用失败");
        }
        try {
            JsonNode node = objectMapper.readTree(extractJson(result));
            double score = node.has("score") ? node.get("score").asDouble(0.0) : 0.0;
            String reasoning = node.has("reasoning") ? node.get("reasoning").asText("") : "";
            return Map.of("score", score, "reasoning", reasoning);
        } catch (Exception e) {
            return Map.of("score", 0.0, "reasoning", "评分结果解析失败: " + result);
        }
    }

    private String callLlm(Long providerId, String userPrompt, String modelId) {
        try {
            Long pid = providerId != null ? providerId : resolveDefaultProviderId();
            Map<String, Object> options = new HashMap<>();
            // 优先使用指定的 modelId，否则使用系统默认
            if (modelId != null && !modelId.isBlank()) {
                options.put("modelId", modelId);
            } else {
                var defaultConfig = systemConfigService.getDefaultAiConfig();
                if (defaultConfig.getModelId() != null && !defaultConfig.getModelId().isBlank()) {
                    options.put("modelId", defaultConfig.getModelId());
                }
            }
            ChatOptions chatOptions = modelFactory.buildChatOptions(pid, options);
            ChatModel chatModel = modelFactory.getChatModel(pid);

            List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
            messages.add(new UserMessage(userPrompt));

            ChatResponse response = LlmTraceContext.callWithoutTrace(() ->
                    chatModel.call(new Prompt(messages, chatOptions)));
            // 限流：每次 LLM 调用后等待，避免 429
            sleepQuietly(LLM_CALL_DELAY_MS);
            return response.getResult().getOutput().getText();
        } catch (Exception e) {
            log.error("[RagEval] LLM 调用失败: {}", e.getMessage());
            return null;
        }
    }

    private Long resolveDefaultProviderId() {
        var defaultConfig = systemConfigService.getDefaultAiConfig();
        if (defaultConfig.getProviderId() != null) {
            return defaultConfig.getProviderId();
        }
        List<Long> providers = modelFactory.getAvailableProviderIds();
        return providers.isEmpty() ? null : providers.get(0);
    }

    // ==================== 工具方法 ====================

    private float[] embedText(String text) {
        try {
            EmbeddingResponse response = embeddingModel.call(
                    new EmbeddingRequest(List.of(text), null));
            return response.getResult().getOutput();
        } catch (Exception e) {
            log.error("[RagEval] 文本向量化失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从 LLM 响应中提取 JSON（去除可能的 markdown 代码块）
     */
    private String extractJson(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                return trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return trimmed;
    }

    private List<String> parseJsonStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private static void sleepQuietly(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }

    /**
     * 获取知识库所有 chunk（id + content），用于 benchmark 生成
     */
    public List<Chunk> getAllChunksForKnowledge(Long knowledgeId) {
        return chunkService.list(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Chunk>()
                        .eq(Chunk::getKnowledgeId, knowledgeId)
                        .select(Chunk::getId, Chunk::getContent));
    }
}
