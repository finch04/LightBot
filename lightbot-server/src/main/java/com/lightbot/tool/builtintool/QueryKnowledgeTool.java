package com.lightbot.tool.builtintool;

import com.lightbot.entity.Knowledge;
import com.lightbot.service.AgentService;
import com.lightbot.service.EmbeddingService;
import com.lightbot.service.KnowledgeService;
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

    /** 工具执行期间捕获的原始搜索结果，供 ChatService 提取 RAG 引用 metadata */
    private static final ThreadLocal<List<Map<String, Object>>> SEARCH_RESULTS_HOLDER = new ThreadLocal<>();

    @Tool(name = "query_knowledge",
          description = "搜索智能体绑定的知识库，获取与问题相关的文档内容。当用户问题涉及特定领域知识、需要查找文档资料时调用此工具。")
    public String queryKnowledge(
            @ToolParam(description = "搜索问题") String question,
            ToolContext context) {
        Long agentId = ((Number) context.getContext().get("agentId")).longValue();
        log.info("[Tool:query_knowledge] 开始检索: agentId={}, question={}", agentId, question);

        // 1. 获取Agent绑定的知识库ID列表
        List<Long> knowledgeIds = agentService.getKnowledgeIds(agentId);
        if (knowledgeIds.isEmpty()) {
            return "该智能体未绑定任何知识库，无法检索。";
        }

        try {
            // 2. 向量化问题
            float[] queryVector = embedText(question);

            // 3. 并行检索多个知识库
            List<CompletableFuture<List<Map<String, Object>>>> futures = knowledgeIds.stream()
                    .map(knowledgeId -> CompletableFuture.supplyAsync(() -> {
                        try {
                            Knowledge knowledge = knowledgeService.getById(knowledgeId);
                            if (knowledge == null) return List.<Map<String, Object>>of();
                            int topK = parseTopK(knowledge);
                            double threshold = parseThreshold(knowledge);
                            return embeddingService.searchSimilar(knowledgeId, queryVector, topK, threshold);
                        } catch (Exception e) {
                            log.warn("[Tool:query_knowledge] 知识库检索失败: knowledgeId={}, error={}", knowledgeId, e.getMessage());
                            return List.<Map<String, Object>>of();
                        }
                    }, SEARCH_EXECUTOR))
                    .toList();

            // 4. 合并结果
            List<Map<String, Object>> allResults = futures.stream()
                    .map(CompletableFuture::join)
                    .flatMap(List::stream)
                    .toList();

            // 4.1 捕获原始结果供 ChatService 提取 RAG 引用
            SEARCH_RESULTS_HOLDER.set(allResults);

            if (allResults.isEmpty()) {
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
            log.error("[Tool:query_knowledge] 检索异常: agentId={}, error={}", agentId, e.getMessage());
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
            return node.has("topK") ? node.get("topK").asInt(5) : 5;
        } catch (Exception e) {
            return 5;
        }
    }

    private double parseThreshold(Knowledge knowledge) {
        if (knowledge.getConfig() == null || knowledge.getConfig().isBlank()) return 0.5;
        try {
            var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(knowledge.getConfig());
            return node.has("threshold") ? node.get("threshold").asDouble(0.5) : 0.5;
        } catch (Exception e) {
            return 0.5;
        }
    }

    /**
     * 获取工具执行期间捕获的RAG搜索结果（仅当前线程有效）
     * <p>调用方必须在使用后调用 {@link #clearSearchResults()} 清理</p>
     */
    public static List<Map<String, Object>> getSearchResults() {
        List<Map<String, Object>> results = SEARCH_RESULTS_HOLDER.get();
        return results != null ? new ArrayList<>(results) : List.of();
    }

    /**
     * 清理当前线程的RAG搜索结果缓存
     */
    public static void clearSearchResults() {
        SEARCH_RESULTS_HOLDER.remove();
    }
}
