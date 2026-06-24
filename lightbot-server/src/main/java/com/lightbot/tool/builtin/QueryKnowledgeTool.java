package com.lightbot.tool.builtin;

import com.lightbot.constant.RagResultType;
import com.lightbot.dto.QaPairSearchResultVO;
import com.lightbot.entity.Knowledge;
import com.lightbot.service.AgentService;
import com.lightbot.service.impl.EmbeddingServiceImpl;
import com.lightbot.service.KnowledgeService;
import com.lightbot.service.QaPairService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.util.RagParamResolver;
import com.lightbot.util.TextNormalizeUtil;
import com.lightbot.tool.ToolEventEmitter;
import com.lightbot.tool.annotation.SystemTool;
import com.lightbot.tool.annotation.ToolParamMeta;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 内置工具 — 知识库检索
 * <p>由 {@link com.lightbot.tool.registrar.ToolRegistrar} 统一注册，type=knowledge，
 * 当 Agent 绑定知识库时由中间件自动注入。</p>
 *
 * @author finch
 * @since 2026-05-22
 */
@Slf4j
@Component("queryKnowledgeTool")
@SystemTool(displayName = "知识库检索", description = "搜索智能体绑定的知识库，获取与问题相关的文档内容", type = "knowledge", tags = {"知识库"},
        outputExample = "{\"total\":2,\"qa_answer\":null,\"results\":[{\"result_type\":\"chunk\",\"content\":\"文档内容片段...\",\"score\":0.85,\"document_id\":1234567890,\"document_name\":\"产品说明书\",\"knowledge_id\":9876543210},{\"result_type\":\"qa_pair\",\"content\":\"问答对回答\",\"score\":0.72,\"question\":\"如何配置系统？\",\"answer\":\"请参考配置指南\",\"knowledge_id\":9876543210}]}",
        outputSchema = "{\"type\":\"object\",\"properties\":{\"total\":{\"type\":\"integer\",\"description\":\"匹配结果总数\"},\"qa_answer\":{\"type\":\"string\",\"description\":\"QA优先命中时直接返回的答案（无命中时为null）\"},\"results\":{\"type\":\"array\",\"description\":\"检索结果列表\",\"items\":{\"type\":\"object\",\"properties\":{\"result_type\":{\"type\":\"string\",\"description\":\"结果类型：chunk=文档片段，qa_pair=问答对\"},\"content\":{\"type\":\"string\",\"description\":\"匹配内容文本\"},\"score\":{\"type\":\"number\",\"description\":\"相似度得分（0-1）\"},\"document_id\":{\"type\":\"integer\",\"description\":\"文档ID（仅chunk类型）\"},\"document_name\":{\"type\":\"string\",\"description\":\"文档名（仅chunk类型）\"},\"question\":{\"type\":\"string\",\"description\":\"问题（仅qa_pair类型）\"},\"answer\":{\"type\":\"string\",\"description\":\"标准答案（仅qa_pair类型）\"},\"knowledge_id\":{\"type\":\"integer\",\"description\":\"所属知识库ID\"}}}}}}")
@RequiredArgsConstructor
public class QueryKnowledgeTool {

    private final AgentService agentService;
    private final KnowledgeService knowledgeService;
    private final EmbeddingServiceImpl embeddingService;
    private final QaPairService qaPairService;
    private final EmbeddingModel embeddingModel;
    private final RagParamResolver ragParamResolver;
    private final ObjectMapper objectMapper;

    private static final ExecutorService SEARCH_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "tool-kb-search");
        t.setDaemon(true);
        return t;
    });

    /** 搜索结果缓存 TTL（毫秒） */
    private static final long CACHE_TTL_MS = 5 * 60 * 1000L;

    /**
     * 按请求ID存储的搜索结果（跨线程安全，带 TTL 自动过期）
     * <p>工具在 SEARCH_EXECUTOR 线程池执行，无法用 ThreadLocal 传递结果给主线程，
     * 改用 ConcurrentHashMap 以 requestId 为 key 存储</p>
     */
    private static final ConcurrentHashMap<String, TimedEntry> SEARCH_RESULTS_MAP = new ConcurrentHashMap<>();

    private record TimedEntry(List<Map<String, Object>> data, long createdAt) {
        boolean isExpired() {
            return System.currentTimeMillis() - createdAt > CACHE_TTL_MS;
        }
    }

    @Tool(name = "query_knowledge",
          description = "搜索当前对话智能体绑定的知识库，获取与问题相关的文档内容。当用户问题涉及特定领域知识、需要查找文档资料时调用此工具。只需传入 question，不要传入 agentId。")
    public String queryKnowledge(
            @ToolParam(description = "搜索问题")
            @ToolParamMeta(example = "如何配置模型参数", required = true) String question,
            ToolContext context) {
        String requestId = (String) context.getContext().get("requestId");
        Long finalAgentId = resolveAgentId(context);
        log.info("[Tool:query_knowledge] 开始检索: agentId={}, question={}", finalAgentId, question);

        if (finalAgentId == null) {
            return "无法确定当前智能体，知识库检索已跳过。请从对话页选择 Agent 后重试。";
        }

        if (question == null || question.isBlank()) {
            return "搜索问题不能为空，请提供具体的搜索内容。";
        }

        // 1. 获取Agent绑定的知识库ID列表
        List<Long> knowledgeIds = agentService.getKnowledgeIds(finalAgentId);
        log.info("[Tool:query_knowledge] Agent绑定知识库: agentId={}, knowledgeIds={}", finalAgentId, knowledgeIds);
        if (knowledgeIds.isEmpty()) {
            return "该智能体未绑定任何知识库，无法检索。";
        }

        try {
            // 2. 向量化问题
            ToolEventEmitter.emit("正在向量化查询问题...");
            float[] queryVector = embedText(question);
            log.info("[Tool:query_knowledge] 问题向量化完成: dimension={}", queryVector.length);

            // 3. 并行检索多个知识库（阈值过滤下沉到SQL层）
            List<CompletableFuture<List<Map<String, Object>>>> futures = knowledgeIds.stream()
                    .map(knowledgeId -> CompletableFuture.supplyAsync(() -> {
                        try {
                            Knowledge knowledge = knowledgeService.getById(knowledgeId);
                            if (knowledge == null) {
                                log.warn("[Tool:query_knowledge] 知识库不存在: knowledgeId={}", knowledgeId);
                                return List.<Map<String, Object>>of();
                            }
                            String kbName = knowledge.getName();
                            int topK = resolveTopK(knowledge);
                            double threshold = resolveThreshold(knowledge);
                            boolean qaEnabled = resolveQaEnabled(knowledge);
                            int qaTopK = qaEnabled ? resolveQaTopK(knowledge) : 0;
                            double qaThreshold = qaEnabled ? resolveQaThreshold(knowledge) : 0;
                            boolean qaPriority = qaEnabled && resolveQaPriority(knowledge);
                            log.info("[Tool:query_knowledge] 检索知识库: name={}, knowledgeId={}, topK={}, threshold={}, qaEnabled={}, qaTopK={}, qaThreshold={}, qaPriority={}",
                                    kbName, knowledgeId, topK, threshold, qaEnabled, qaTopK, qaThreshold, qaPriority);

                            // 并行检索 Chunk 和 QA Pair
                            ToolEventEmitter.emit("正在检索知识库「" + kbName + "」的文档块...");
                            CompletableFuture<List<Map<String, Object>>> chunkFuture = CompletableFuture.supplyAsync(() -> {
                                try {
                                    Map<String, Object> searchParams = buildSearchParams(knowledge, question);
                                    return embeddingService.searchSimilarSql(knowledgeId, queryVector, topK, threshold, searchParams);
                                } catch (Exception e) {
                                    log.warn("[Tool:query_knowledge] Chunk检索失败: knowledgeId={}", knowledgeId);
                                    return List.<Map<String, Object>>of();
                                }
                            }, SEARCH_EXECUTOR);

                            CompletableFuture<List<Map<String, Object>>> qaFuture;
                            if (qaEnabled) {
                                ToolEventEmitter.emit("正在检索知识库「" + kbName + "」的问答对...");
                                qaFuture = CompletableFuture.supplyAsync(() -> {
                                    try {
                                        List<QaPairSearchResultVO> qaResults = qaPairService.searchSimilar(knowledgeId, queryVector, qaTopK, qaThreshold);
                                        return qaResults.stream().map(qa -> {
                                            Map<String, Object> row = new java.util.HashMap<>();
                                            row.put("id", qa.getId());
                                            row.put("question", qa.getQuestion());
                                            row.put("content", qa.getAnswer());
                                            row.put("answer", qa.getAnswer());
                                            row.put("score", qa.getScore());
                                            row.put("knowledge_id", knowledgeId.toString());
                                            row.put("document_name", "问答对");
                                            row.put("result_type", RagResultType.QA_PAIR);
                                            return row;
                                        }).toList();
                                    } catch (Exception e) {
                                        log.warn("[Tool:query_knowledge] QA Pair检索失败: knowledgeId={}", knowledgeId);
                                        return List.<Map<String, Object>>of();
                                    }
                                }, SEARCH_EXECUTOR);
                            } else {
                                qaFuture = CompletableFuture.completedFuture(List.of());
                            }

                            // 合并结果
                            List<Map<String, Object>> chunkResults = chunkFuture.join();
                            List<Map<String, Object>> qaResults = qaFuture.join();

                            // 标记 chunk 结果类型
                            chunkResults.forEach(row -> row.putIfAbsent("result_type", RagResultType.CHUNK));

                            log.info("[Tool:query_knowledge] 知识库检索结果: name={}, chunkCount={}, qaCount={}",
                                    kbName, chunkResults.size(), qaResults.size());
                            ToolEventEmitter.emit("知识库「" + kbName + "」: 文档块 " + chunkResults.size() + " 条" + (qaEnabled ? ", 问答对 " + qaResults.size() + " 条" : ""));

                            // QA 优先返回：高分 QA 标记特殊字段，外层统一处理
                            List<Map<String, Object>> allKbResults = new ArrayList<>();
                            if (qaPriority && !qaResults.isEmpty()) {
                                double topQaScore = ((Number) qaResults.get(0).get("score")).doubleValue();
                                if (topQaScore >= qaThreshold) {
                                    Map<String, Object> qaPriorityResult = new java.util.HashMap<>(qaResults.get(0));
                                    qaPriorityResult.put("_qa_priority", true);
                                    allKbResults.add(qaPriorityResult);
                                    log.info("[Tool:query_knowledge] QA优先命中: knowledgeId={}, score={}", knowledgeId, topQaScore);
                                    return allKbResults;
                                }
                            }
                            allKbResults.addAll(qaResults);
                            allKbResults.addAll(chunkResults);
                            return allKbResults;
                        } catch (Exception e) {
                            log.warn("[Tool:query_knowledge] 知识库检索失败: knowledgeId={}, error={}", knowledgeId, e.getMessage(), e);
                            return List.<Map<String, Object>>of();
                        }
                    }, SEARCH_EXECUTOR))
                    .toList();

            // 4. 合并结果，检查是否有 QA 优先命中
            List<Map<String, Object>> allResults = new ArrayList<>();
            Map<String, Object> qaPriorityHit = null;
            for (CompletableFuture<List<Map<String, Object>>> future : futures) {
                for (Map<String, Object> row : future.join()) {
                    if (Boolean.TRUE.equals(row.get("_qa_priority"))) {
                        qaPriorityHit = row;
                    } else {
                        allResults.add(row);
                    }
                }
            }

            // 4.1 QA 优先命中：返回 JSON（含 qa_answer）
            if (qaPriorityHit != null) {
                String qaAnswer = (String) qaPriorityHit.get("answer");
                String qaQuestion = (String) qaPriorityHit.get("question");
                double qaScore = ((Number) qaPriorityHit.get("score")).doubleValue();
                ToolEventEmitter.emit("命中高匹配问答对（相似度 " + String.format("%.2f", qaScore) + "），直接返回标准答案");
                if (requestId != null) {
                    SEARCH_RESULTS_MAP.put(requestId, new TimedEntry(List.of(qaPriorityHit), System.currentTimeMillis()));
                }
                log.info("[Tool:query_knowledge] QA优先返回: question={}, score={}", qaQuestion, qaScore);
                Map<String, Object> output = new java.util.LinkedHashMap<>();
                output.put("total", 1);
                output.put("qa_answer", qaAnswer);
                output.put("results", List.of());
                return objectMapper.writeValueAsString(output);
            }

            // 4.2 按 requestId 存储原始结果，供 ChatService 读取并持久化到消息 metadata
            if (requestId != null) {
                SEARCH_RESULTS_MAP.put(requestId, new TimedEntry(allResults, System.currentTimeMillis()));
            }

            ToolEventEmitter.emit("共找到 " + allResults.size() + " 条相关内容");

            if (allResults.isEmpty()) {
                log.warn("[Tool:query_knowledge] 未找到结果: agentId={}, knowledgeIds={}, question={}",
                        finalAgentId, knowledgeIds, question);
                Map<String, Object> empty = new java.util.LinkedHashMap<>();
                empty.put("total", 0);
                empty.put("qa_answer", null);
                empty.put("results", List.of());
                return objectMapper.writeValueAsString(empty);
            }

            // 5. 构建 JSON 返回
            Map<String, Object> output = new java.util.LinkedHashMap<>();
            output.put("total", allResults.size());
            output.put("qa_answer", null);
            output.put("results", allResults.stream().map(row -> {
                Map<String, Object> item = new java.util.LinkedHashMap<>();
                item.put("result_type", row.get("result_type"));
                item.put("content", TextNormalizeUtil.normalizeForPrompt(String.valueOf(row.get("content"))));
                item.put("score", row.get("score"));
                if (RagResultType.CHUNK.equals(row.get("result_type"))) {
                    Object docIdRaw = row.get("document_id");
                    item.put("document_id", docIdRaw != null ? docIdRaw.toString() : null);
                    item.put("document_name", row.get("document_name"));
                }
                if (RagResultType.QA_PAIR.equals(row.get("result_type"))) {
                    item.put("question", row.get("question"));
                    item.put("answer", row.get("answer"));
                }
                Object kbIdRaw = row.get("knowledge_id");
                item.put("knowledge_id", kbIdRaw != null ? kbIdRaw.toString() : null);
                return item;
            }).toList());

            log.info("[Tool:query_knowledge] 检索完成: agentId={}, results={}", finalAgentId, allResults.size());
            return objectMapper.writeValueAsString(output);
        } catch (Exception e) {
            log.error("[Tool:query_knowledge] 检索异常: agentId={}, error={}", finalAgentId, e.getMessage(), e);
            return "知识库检索过程中发生错误：" + e.getMessage();
        }
    }

    private static Long resolveAgentId(ToolContext context) {
        if (context == null || context.getContext() == null) {
            return null;
        }
        Object agentIdObj = context.getContext().get("agentId");
        if (agentIdObj instanceof Number num) {
            long id = num.longValue();
            return id > 0 ? id : null;
        }
        if (agentIdObj instanceof String str && !str.isBlank()) {
            try {
                long id = Long.parseLong(str.trim());
                return id > 0 ? id : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private float[] embedText(String text) {
        EmbeddingResponse response = embeddingModel.call(new EmbeddingRequest(List.of(text), null));
        return response.getResult().getOutput();
    }

    private int resolveTopK(Knowledge knowledge) {
        return ragParamResolver.resolveTopK(null, parseQueryParams(knowledge),
                knowledge.getConfig(), RagParamResolver.DEFAULT_TOP_K);
    }

    private double resolveThreshold(Knowledge knowledge) {
        return ragParamResolver.resolveThreshold(null, parseQueryParams(knowledge),
                knowledge.getConfig(), RagParamResolver.DEFAULT_THRESHOLD);
    }

    private boolean resolveQaEnabled(Knowledge knowledge) {
        Map<String, Object> qp = parseQueryParams(knowledge);
        if (qp.get("qa_enabled") instanceof Boolean b) return b;
        return true;
    }

    private int resolveQaTopK(Knowledge knowledge) {
        Map<String, Object> qp = parseQueryParams(knowledge);
        if (qp.get("qa_top_k") instanceof Number n) return n.intValue();
        return ragParamResolver.resolveTopK(null, null, knowledge.getConfig(), 3);
    }

    private double resolveQaThreshold(Knowledge knowledge) {
        Map<String, Object> qp = parseQueryParams(knowledge);
        if (qp.get("qa_threshold") instanceof Number n) return n.doubleValue();
        return ragParamResolver.resolveThreshold(null, null, knowledge.getConfig(), 0.85);
    }

    private boolean resolveQaPriority(Knowledge knowledge) {
        Map<String, Object> qp = parseQueryParams(knowledge);
        if (qp.get("qa_priority") instanceof Boolean b) return b;
        if (knowledge.getConfig() != null && !knowledge.getConfig().isBlank()) {
            try {
                var node = objectMapper.readTree(knowledge.getConfig());
                if (node.has("qaPriority")) return node.get("qaPriority").asBoolean(true);
            } catch (Exception ignored) {}
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseQueryParams(Knowledge knowledge) {
        if (knowledge.getQueryParams() == null || knowledge.getQueryParams().isBlank()
                || "{}".equals(knowledge.getQueryParams())) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(knowledge.getQueryParams(), Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }


    /**
     * 按 requestId 获取工具执行期间的搜索结果（跨线程安全）
     *
     * @param requestId 请求ID
     * @return 搜索结果列表，不存在则返回空列表
     */
    public static List<Map<String, Object>> getSearchResults(String requestId) {
        if (requestId == null) return List.of();
        TimedEntry entry = SEARCH_RESULTS_MAP.remove(requestId);
        if (entry == null || entry.isExpired()) {
            return List.of();
        }
        return entry.data();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildSearchParams(Knowledge knowledge, String question) {
        Map<String, Object> params = new java.util.HashMap<>();
        if (knowledge.getQueryParams() != null && !knowledge.getQueryParams().isBlank()
                && !"{}".equals(knowledge.getQueryParams())) {
            try {
                params.putAll(objectMapper.readValue(knowledge.getQueryParams(), Map.class));
            } catch (Exception ignored) {
            }
        }
        params.put("query_text", question);
        return params;
    }
}
