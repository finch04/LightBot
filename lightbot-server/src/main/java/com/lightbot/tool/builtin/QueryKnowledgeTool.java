package com.lightbot.tool.builtin;

import com.lightbot.constant.RagResultType;
import com.lightbot.dto.QaPairSearchResultVO;
import com.lightbot.entity.Knowledge;
import com.lightbot.service.AgentService;
import com.lightbot.service.impl.EmbeddingServiceImpl;
import com.lightbot.service.KnowledgeService;
import com.lightbot.service.QaPairService;
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
@SystemTool(displayName = "知识库检索", description = "搜索智能体绑定的知识库，获取与问题相关的文档内容", type = "knowledge", tags = {"知识库"})
@RequiredArgsConstructor
public class QueryKnowledgeTool {

    private final AgentService agentService;
    private final KnowledgeService knowledgeService;
    private final EmbeddingServiceImpl embeddingService;
    private final QaPairService qaPairService;
    private final EmbeddingModel embeddingModel;

    private static final ExecutorService SEARCH_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "tool-kb-search");
        t.setDaemon(true);
        return t;
    });

    /**
     * 按请求ID存储的搜索结果（跨线程安全）
     * <p>工具在 SEARCH_EXECUTOR 线程池执行，无法用 ThreadLocal 传递结果给主线程，
     * 改用 ConcurrentHashMap 以 requestId 为 key 存储</p>
     */
    private static final ConcurrentHashMap<String, List<Map<String, Object>>> SEARCH_RESULTS_MAP = new ConcurrentHashMap<>();

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
                                            row.put("knowledge_id", knowledgeId);
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

            // 4.1 QA 优先命中：直接返回标准答案
            if (qaPriorityHit != null) {
                String qaAnswer = (String) qaPriorityHit.get("answer");
                String qaQuestion = (String) qaPriorityHit.get("question");
                double qaScore = ((Number) qaPriorityHit.get("score")).doubleValue();
                ToolEventEmitter.emit("命中高匹配问答对（相似度 " + String.format("%.2f", qaScore) + "），直接返回标准答案");
                if (requestId != null) {
                    SEARCH_RESULTS_MAP.put(requestId, List.of(qaPriorityHit));
                }
                log.info("[Tool:query_knowledge] QA优先返回: question={}, score={}", qaQuestion, qaScore);
                return qaAnswer;
            }

            // 4.2 按 requestId 存储原始结果，供 ChatService 读取并持久化到消息 metadata
            if (requestId != null) {
                SEARCH_RESULTS_MAP.put(requestId, allResults);
            }

            ToolEventEmitter.emit("共找到 " + allResults.size() + " 条相关内容");

            if (allResults.isEmpty()) {
                log.warn("[Tool:query_knowledge] 未找到结果: agentId={}, knowledgeIds={}, question={}",
                        finalAgentId, knowledgeIds, question);
                return "未在知识库中找到与问题相关的内容。";
            }

            // 5. 格式化返回（供大模型理解）
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("在知识库中找到 %d 条相关内容：\n\n", allResults.size()));
            for (int i = 0; i < allResults.size(); i++) {
                Map<String, Object> row = allResults.get(i);
                String content = TextNormalizeUtil.normalizeForPrompt(String.valueOf(row.get("content")));
                String resultType = (String) row.get("result_type");
                String label = RagResultType.QA_PAIR.equals(resultType) ? "问答对" : String.valueOf(row.get("document_name"));
                sb.append(String.format("【%d. %s】\n%s\n\n", i + 1, label, content));
            }

            log.info("[Tool:query_knowledge] 检索完成: agentId={}, results={}", finalAgentId, allResults.size());
            return sb.toString();
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
        Map<String, Object> qp = parseQueryParams(knowledge);
        if (qp.get("final_top_k") instanceof Number n) return n.intValue();
        if (knowledge.getConfig() != null && !knowledge.getConfig().isBlank()) {
            try {
                var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(knowledge.getConfig());
                if (node.has("ragTopK")) return node.get("ragTopK").asInt(5);
            } catch (Exception ignored) {}
        }
        return 5;
    }

    private double resolveThreshold(Knowledge knowledge) {
        Map<String, Object> qp = parseQueryParams(knowledge);
        if (qp.get("similarity_threshold") instanceof Number n) return n.doubleValue();
        if (knowledge.getConfig() != null && !knowledge.getConfig().isBlank()) {
            try {
                var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(knowledge.getConfig());
                if (node.has("ragThreshold")) return node.get("ragThreshold").asDouble(0.5);
            } catch (Exception ignored) {}
        }
        return 0.5;
    }

    private boolean resolveQaEnabled(Knowledge knowledge) {
        Map<String, Object> qp = parseQueryParams(knowledge);
        if (qp.get("qa_enabled") instanceof Boolean b) return b;
        return true;
    }

    private int resolveQaTopK(Knowledge knowledge) {
        Map<String, Object> qp = parseQueryParams(knowledge);
        if (qp.get("qa_top_k") instanceof Number n) return n.intValue();
        if (knowledge.getConfig() != null && !knowledge.getConfig().isBlank()) {
            try {
                var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(knowledge.getConfig());
                if (node.has("qaTopK")) return node.get("qaTopK").asInt(3);
            } catch (Exception ignored) {}
        }
        return 3;
    }

    private double resolveQaThreshold(Knowledge knowledge) {
        Map<String, Object> qp = parseQueryParams(knowledge);
        if (qp.get("qa_threshold") instanceof Number n) return n.doubleValue();
        if (knowledge.getConfig() != null && !knowledge.getConfig().isBlank()) {
            try {
                var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(knowledge.getConfig());
                if (node.has("qaThreshold")) return node.get("qaThreshold").asDouble(0.85);
            } catch (Exception ignored) {}
        }
        return 0.85;
    }

    private boolean resolveQaPriority(Knowledge knowledge) {
        Map<String, Object> qp = parseQueryParams(knowledge);
        if (qp.get("qa_priority") instanceof Boolean b) return b;
        if (knowledge.getConfig() != null && !knowledge.getConfig().isBlank()) {
            try {
                var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(knowledge.getConfig());
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
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(knowledge.getQueryParams(), Map.class);
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
        List<Map<String, Object>> results = SEARCH_RESULTS_MAP.remove(requestId);
        return results != null ? results : List.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildSearchParams(Knowledge knowledge, String question) {
        Map<String, Object> params = new java.util.HashMap<>();
        if (knowledge.getQueryParams() != null && !knowledge.getQueryParams().isBlank()
                && !"{}".equals(knowledge.getQueryParams())) {
            try {
                params.putAll(new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(knowledge.getQueryParams(), Map.class));
            } catch (Exception ignored) {
            }
        }
        params.put("query_text", question);
        return params;
    }
}
