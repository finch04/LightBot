package com.lightbot.tool.builtintool;

import com.lightbot.entity.Knowledge;
import com.lightbot.service.AgentService;
import com.lightbot.service.EmbeddingService;
import com.lightbot.service.KnowledgeService;
import com.lightbot.tool.ToolEventEmitter;
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
import java.util.stream.Collectors;

/**
 * 内置工具 — 查询知识库
 * <p>搜索智能体绑定的知识库，由大模型按需调用，避免每次强制RAG</p>
 *
 * @author finch
 * @since 2026-05-22
 */
@Slf4j
@Component("queryKnowledgeTool")
@RequiredArgsConstructor
public class QueryKnowledgeTool {

    private final AgentService agentService;
    private final KnowledgeService knowledgeService;
    private final EmbeddingService embeddingService;
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
          description = "搜索智能体绑定的知识库，获取与问题相关的文档内容。当用户问题涉及特定领域知识、需要查找文档资料时调用此工具。")
    public String queryKnowledge(
            @ToolParam(description = "搜索问题") String question,
            ToolContext context) {
        Long agentId = ((Number) context.getContext().get("agentId")).longValue();
        String requestId = (String) context.getContext().get("requestId");
        log.info("[Tool:query_knowledge] 开始检索: agentId={}, question={}", agentId, question);

        // 1. 获取Agent绑定的知识库ID列表
        List<Long> knowledgeIds = agentService.getKnowledgeIds(agentId);
        log.info("[Tool:query_knowledge] Agent绑定知识库: agentId={}, knowledgeIds={}", agentId, knowledgeIds);
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
                            ToolEventEmitter.emit("正在检索知识库「" + kbName + "」...");
                            int topK = parseTopK(knowledge);
                            double threshold = parseThreshold(knowledge);
                            log.info("[Tool:query_knowledge] 检索知识库: name={}, knowledgeId={}, topK={}, threshold={}",
                                    kbName, knowledgeId, topK, threshold);
                            List<Map<String, Object>> results = embeddingService.searchSimilarSql(knowledgeId, queryVector, topK, threshold);
                            log.info("[Tool:query_knowledge] 知识库检索结果: name={}, count={}", kbName, results.size());
                            for (int i = 0; i < Math.min(results.size(), 5); i++) {
                                Map<String, Object> row = results.get(i);
                                log.info("[Tool:query_knowledge]   结果#{}: document={}, score={}, contentLength={}",
                                        i + 1, row.get("document_name"), row.get("score"),
                                        row.get("content") != null ? ((String) row.get("content")).length() : 0);
                            }
                            ToolEventEmitter.emit("知识库「" + kbName + "」找到 " + results.size() + " 条结果");
                            return results;
                        } catch (Exception e) {
                            log.warn("[Tool:query_knowledge] 知识库检索失败: knowledgeId={}, error={}", knowledgeId, e.getMessage(), e);
                            return List.<Map<String, Object>>of();
                        }
                    }, SEARCH_EXECUTOR))
                    .toList();

            // 4. 合并结果
            List<Map<String, Object>> allResults = futures.stream()
                    .map(CompletableFuture::join)
                    .flatMap(List::stream)
                    .toList();

            // 4.1 按 requestId 存储原始结果，供 ChatService 读取并持久化到消息 metadata
            if (requestId != null) {
                SEARCH_RESULTS_MAP.put(requestId, allResults);
            }

            ToolEventEmitter.emit("共找到 " + allResults.size() + " 条相关内容");

            if (allResults.isEmpty()) {
                log.warn("[Tool:query_knowledge] 未找到结果: agentId={}, knowledgeIds={}, question={}",
                        agentId, knowledgeIds, question);
                return "未在知识库中找到与问题相关的内容。";
            }

            // 5. 格式化返回（供大模型理解）
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("在知识库中找到 %d 条相关内容：\n\n", allResults.size()));
            for (int i = 0; i < allResults.size(); i++) {
                Map<String, Object> row = allResults.get(i);
                sb.append(String.format("【%d. %s】\n%s\n\n",
                        i + 1, row.get("document_name"), row.get("content")));
            }

            log.info("[Tool:query_knowledge] 检索完成: agentId={}, results={}", agentId, allResults.size());
            return sb.toString();
        } catch (Exception e) {
            log.error("[Tool:query_knowledge] 检索异常: agentId={}, error={}", agentId, e.getMessage(), e);
            return "知识库检索过程中发生错误：" + e.getMessage();
        }
    }

    private float[] embedText(String text) {
        EmbeddingResponse response = embeddingModel.call(new EmbeddingRequest(List.of(text), null));
        return response.getResult().getOutput();
    }

    private int parseTopK(Knowledge knowledge) {
        if (knowledge.getConfig() == null || knowledge.getConfig().isBlank()) return 5;
        try {
            var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(knowledge.getConfig());
            // 与 RagServiceImpl 保持一致，读取 ragTopK / ragThreshold
            return node.has("ragTopK") ? node.get("ragTopK").asInt(5) : 5;
        } catch (Exception e) {
            return 5;
        }
    }

    private double parseThreshold(Knowledge knowledge) {
        if (knowledge.getConfig() == null || knowledge.getConfig().isBlank()) return 0.5;
        try {
            var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(knowledge.getConfig());
            // 与 RagServiceImpl 保持一致，读取 ragTopK / ragThreshold
            return node.has("ragThreshold") ? node.get("ragThreshold").asDouble(0.5) : 0.5;
        } catch (Exception e) {
            return 0.5;
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
}
