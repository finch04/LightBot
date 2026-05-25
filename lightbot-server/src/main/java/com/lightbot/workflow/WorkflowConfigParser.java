package com.lightbot.workflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 解析 Agent.config 中的工作流配置
 */
@Slf4j
public final class WorkflowConfigParser {

    private WorkflowConfigParser() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseConfigMap(String configJson, ObjectMapper objectMapper) {
        if (configJson == null || configJson.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(configJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("[WorkflowConfigParser] 解析 config 失败: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * 获取用于编辑的草稿图（优先 workflowDraft，兼容旧 workflow）
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> resolveDraftGraph(Map<String, Object> config) {
        if (config == null) {
            return null;
        }
        Object draft = config.get(WorkflowConfigKeys.WORKFLOW_DRAFT);
        if (draft instanceof Map) {
            return (Map<String, Object>) draft;
        }
        Object legacy = config.get(WorkflowConfigKeys.WORKFLOW_LEGACY);
        if (legacy instanceof Map) {
            return (Map<String, Object>) legacy;
        }
        return null;
    }

    /**
     * 获取已发布的工作流图（运行时使用）
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> resolvePublishedGraph(Map<String, Object> config) {
        if (config == null) {
            return null;
        }
        int publishedVersion = config.get(WorkflowConfigKeys.PUBLISHED_VERSION) instanceof Number
                ? ((Number) config.get(WorkflowConfigKeys.PUBLISHED_VERSION)).intValue() : 0;
        if (publishedVersion > 0) {
            Object published = config.get(WorkflowConfigKeys.WORKFLOW_PUBLISHED);
            if (published instanceof Map) {
                return (Map<String, Object>) published;
            }
            return null;
        }
        // 兼容旧数据：从未走发布流程时，仍使用 workflow / workflowDraft
        return resolveDraftGraph(config);
    }

    public static WorkflowDefinition toDefinition(Map<String, Object> graph, ObjectMapper objectMapper) {
        if (graph == null || graph.isEmpty()) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(graph);
            return objectMapper.readValue(json, WorkflowDefinition.class);
        } catch (Exception e) {
            log.warn("[WorkflowConfigParser] 解析工作流图失败: {}", e.getMessage());
            return null;
        }
    }

    public static WorkflowDefinition fromAgentConfig(String configJson, boolean useDraft, ObjectMapper objectMapper) {
        Map<String, Object> config = parseConfigMap(configJson, objectMapper);
        Map<String, Object> graph = useDraft ? resolveDraftGraph(config) : resolvePublishedGraph(config);
        if (graph == null && !useDraft) {
            graph = resolveDraftGraph(config);
        }
        return toDefinition(graph, objectMapper);
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> getVersions(Map<String, Object> config) {
        Object versions = config.get(WorkflowConfigKeys.WORKFLOW_VERSIONS);
        if (versions instanceof List) {
            return (List<Map<String, Object>>) versions;
        }
        return List.of();
    }
}
