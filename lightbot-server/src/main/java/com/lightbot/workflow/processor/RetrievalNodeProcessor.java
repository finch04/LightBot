package com.lightbot.workflow.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.entity.Knowledge;
import com.lightbot.enums.NodeType;
import com.lightbot.service.impl.EmbeddingServiceImpl;
import com.lightbot.service.KnowledgeService;
import com.lightbot.workflow.NodeExecutionContext;
import com.lightbot.workflow.NodeExecutionResult;
import com.lightbot.workflow.NodeProcessor;
import com.lightbot.workflow.WorkflowNodeDataUtils;
import com.lightbot.workflow.WorkflowVariableUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 知识检索节点处理器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetrievalNodeProcessor extends AbstractFlowNodeProcessor implements NodeProcessor {

    private static final int DEFAULT_TOP_K = 5;
    private static final double DEFAULT_THRESHOLD = 0.5;

    private final KnowledgeService knowledgeService;
    private final EmbeddingServiceImpl embeddingService;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private EmbeddingModel embeddingModel;

    @Override
    public NodeType getType() {
        return NodeType.RETRIEVAL;
    }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext context) {
        Map<String, Object> nodeData = context.getCurrentNodeData() != null
                ? context.getCurrentNodeData() : Map.of();

        Long knowledgeId = parseLong(nodeData.get("knowledgeId"));
        if (knowledgeId == null) {
            log.warn("[RetrievalNodeProcessor] 未配置 knowledgeId，节点数据: {}", nodeData);
            return passThrough(context, "retrievalResult", "");
        }

        // 1. 校验知识库是否存在
        Knowledge knowledge = knowledgeService.getById(knowledgeId);
        if (knowledge == null) {
            log.warn("[RetrievalNodeProcessor] 知识库不存在: knowledgeId={}", knowledgeId);
            return passThrough(context, "retrievalResult", "");
        }

        String query = WorkflowVariableUtils.resolveInputText(
                WorkflowNodeDataUtils.parseString(nodeData.get("inputVariable")),
                context.getVariables(),
                context.getUserInput());
        log.info("[RetrievalNodeProcessor] 开始检索: knowledgeId={}, query={}", knowledgeId, query);

        int topK = DEFAULT_TOP_K;
        double threshold = DEFAULT_THRESHOLD;
        if (Boolean.TRUE.equals(nodeData.get("overrideConfig"))) {
            if (nodeData.get("topK") instanceof Number n) {
                topK = n.intValue();
            }
            if (nodeData.get("threshold") instanceof Number t) {
                threshold = t.doubleValue();
            }
        } else {
            // 优先从 queryParams 读取
            Map<String, Object> queryParams = parseConfig(knowledge.getQueryParams());
            if (queryParams.get("final_top_k") instanceof Number n) {
                topK = n.intValue();
            } else {
                // 兼容旧 config.ragTopK
                Map<String, Object> kbConfig = parseConfig(knowledge.getConfig());
                if (kbConfig.get("ragTopK") instanceof Number n2) {
                    topK = n2.intValue();
                }
            }
            if (queryParams.get("similarity_threshold") instanceof Number t) {
                threshold = t.doubleValue();
            } else {
                // 兼容旧 config.ragThreshold
                Map<String, Object> kbConfig = parseConfig(knowledge.getConfig());
                if (kbConfig.get("ragThreshold") instanceof Number t2) {
                    threshold = t2.doubleValue();
                }
            }
        }

        String retrievalText = "";
        try {
            if (embeddingModel == null) {
                log.warn("[RetrievalNodeProcessor] EmbeddingModel 未配置，无法生成查询向量");
                return passThrough(context, "retrievalResult", "");
            }
            float[] vector = embedText(query);
            Map<String, Object> searchParams = buildSearchParams(knowledge, query);
            List<Map<String, Object>> hits = embeddingService.searchSimilarSql(knowledgeId, vector, topK, threshold, searchParams);
            log.info("[RetrievalNodeProcessor] 检索完成: knowledgeId={}, topK={}, threshold={}, 命中数={}",
                    knowledgeId, topK, threshold, hits.size());
            retrievalText = hits.stream()
                    .map(h -> String.valueOf(h.getOrDefault("content", "")))
                    .filter(s -> !s.isBlank())
                    .collect(Collectors.joining("\n\n"));
        } catch (Exception e) {
            log.error("[RetrievalNodeProcessor] 检索异常: knowledgeId={}, query={}, error={}",
                    knowledgeId, query, e.getMessage(), e);
        }

        Map<String, Object> outputs = new HashMap<>();
        outputs.put("retrievalResult", retrievalText);
        outputs.put("input", query);

        // streamContent 不设置，检索结果仅作为 outputs 传给下游 LLM 节点
        // 最终回答由 LLM 节点生成
        return NodeExecutionResult.builder()
                .nextNodeId(resolveNextNodeId(context))
                .outputs(outputs)
                .finished(false)
                .build();
    }

    private Long parseLong(Object val) {
        if (val instanceof Number n) {
            return n.longValue();
        }
        if (val instanceof String s && !s.isBlank()) {
            return Long.parseLong(s);
        }
        return null;
    }

    private float[] embedText(String text) {
        EmbeddingResponse response = embeddingModel.call(new EmbeddingRequest(List.of(text), null));
        return response.getResult().getOutput();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfig(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(configJson, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private Map<String, Object> buildSearchParams(Knowledge knowledge, String query) {
        Map<String, Object> params = new HashMap<>(parseConfig(knowledge.getQueryParams()));
        params.put("query_text", query);
        return params;
    }
}
