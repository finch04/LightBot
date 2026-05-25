package com.lightbot.workflow.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.entity.Knowledge;
import com.lightbot.enums.NodeType;
import com.lightbot.service.EmbeddingService;
import com.lightbot.service.KnowledgeService;
import com.lightbot.workflow.NodeExecutionContext;
import com.lightbot.workflow.NodeExecutionResult;
import com.lightbot.workflow.NodeProcessor;
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
    private final EmbeddingService embeddingService;
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
            log.warn("[RetrievalNodeProcessor] 未配置 knowledgeId");
            return passThrough(context, "retrievalResult", "");
        }

        Knowledge knowledge = knowledgeService.getById(knowledgeId);
        String query = String.valueOf(context.getVariables().getOrDefault("input", context.getUserInput()));

        int topK = DEFAULT_TOP_K;
        double threshold = DEFAULT_THRESHOLD;
        if (Boolean.TRUE.equals(nodeData.get("overrideConfig"))) {
            if (nodeData.get("topK") instanceof Number n) {
                topK = n.intValue();
            }
            if (nodeData.get("threshold") instanceof Number t) {
                threshold = t.doubleValue();
            }
        } else if (knowledge != null) {
            Map<String, Object> kbConfig = parseConfig(knowledge.getConfig());
            if (kbConfig.get("ragTopK") instanceof Number n) {
                topK = n.intValue();
            }
            if (kbConfig.get("ragThreshold") instanceof Number t) {
                threshold = t.doubleValue();
            }
        }

        String retrievalText = "";
        try {
            if (embeddingModel == null) {
                log.warn("[RetrievalNodeProcessor] EmbeddingModel 未配置");
                return passThrough(context, "retrievalResult", "");
            }
            float[] vector = embedText(query);
            List<Map<String, Object>> hits = embeddingService.searchSimilarSql(knowledgeId, vector, topK, threshold);
            retrievalText = hits.stream()
                    .map(h -> String.valueOf(h.getOrDefault("content", "")))
                    .filter(s -> !s.isBlank())
                    .collect(Collectors.joining("\n\n"));
        } catch (Exception e) {
            log.warn("[RetrievalNodeProcessor] 检索失败: {}", e.getMessage());
        }

        Map<String, Object> outputs = new HashMap<>();
        outputs.put("retrievalResult", retrievalText);
        outputs.put("input", query);

        return NodeExecutionResult.builder()
                .nextNodeId(resolveNextNodeId(context))
                .outputs(outputs)
                .streamContent(retrievalText)
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
}
