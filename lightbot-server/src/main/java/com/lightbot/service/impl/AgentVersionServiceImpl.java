package com.lightbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.BizException;
import com.lightbot.dto.WorkflowGraphDTO;
import com.lightbot.dto.WorkflowVersionVO;
import com.lightbot.entity.Agent;
import com.lightbot.entity.AgentVersion;
import com.lightbot.enums.AgentStatus;
import com.lightbot.enums.AgentType;
import com.lightbot.enums.AgentVersionStatus;
import com.lightbot.enums.ErrorCode;
import com.lightbot.mapper.AgentMapper;
import com.lightbot.mapper.AgentVersionMapper;
import com.lightbot.service.AgentService;
import com.lightbot.service.AgentVersionService;
import com.lightbot.workflow.WorkflowConfigKeys;
import com.lightbot.workflow.WorkflowConfigParser;
import com.lightbot.workflow.WorkflowDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 版本：草稿与发布历史存 agent_version 表
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentVersionServiceImpl implements AgentVersionService {

    private static final String KIND_WORKFLOW = "workflow";
    private static final String KIND_CHAT = "chat";

    private final AgentMapper agentMapper;
    private final AgentVersionMapper agentVersionMapper;
    private final ObjectMapper objectMapper;

    @Lazy
    @Autowired
    private AgentService agentService;

    @Override
    public Map<String, Object> getWorkflowEditorState(Long agentId) {
        Agent agent = requireAgent(agentId);
        migrateLegacyIfNeeded(agent);

        AgentVersion draft = getDraftRow(agentId);
        Map<String, Object> draftGraph = extractWorkflowGraph(draft);

        AgentVersion latestPublished = getLatestPublishedRow(agentId);
        Map<String, Object> publishedGraph = latestPublished != null ? extractWorkflowGraph(latestPublished) : null;

        Map<String, Object> result = new HashMap<>();
        result.put("draft", draftGraph);
        result.put("published", publishedGraph);
        result.put("publishedVersion", agent.getVersion() != null ? agent.getVersion() : 0);
        result.put("status", resolveWorkflowStatusCode(agent));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveWorkflowDraft(Long agentId, WorkflowGraphDTO graph) {
        Agent agent = requireAgent(agentId);
        migrateLegacyIfNeeded(agent);

        Map<String, Object> snapshot = buildWorkflowSnapshot(graph);
        saveDraftConfig(agent, snapshot, countNodes(graph), countEdges(graph));

        if (agent.getStatus() == AgentStatus.PUBLISHED) {
            agent.setStatus(AgentStatus.PUBLISHED_EDITING);
            agentMapper.updateById(agent);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> publishWorkflow(Long agentId, WorkflowGraphDTO graph) {
        Agent agent = requireAgent(agentId);
        Map<String, Object> snapshot = buildWorkflowSnapshot(graph);

        int nextVersion = (agent.getVersion() != null ? agent.getVersion() : 0) + 1;
        AgentVersion published = new AgentVersion();
        published.setAgentId(agentId);
        published.setUserId(agent.getUserId());
        published.setVersion(nextVersion);
        published.setStatus(AgentVersionStatus.PUBLISHED);
        published.setConfig(writeJson(snapshot));
        published.setNodeCount(countNodes(graph));
        published.setEdgeCount(countEdges(graph));
        published.setPublishTime(LocalDateTime.now());
        published.setDescription(normalizePublishDescription(graph.getPublishDescription()));
        agentVersionMapper.insert(published);

        saveDraftConfig(agent, snapshot, published.getNodeCount(), published.getEdgeCount());

        agent.setVersion(nextVersion);
        agent.setStatus(AgentStatus.PUBLISHED);
        agent.setPublishTime(LocalDateTime.now());
        stripLegacyWorkflowFromAgentConfig(agent);
        agentMapper.updateById(agent);

        Map<String, Object> result = new HashMap<>();
        result.put("version", nextVersion);
        result.put("status", AgentStatus.PUBLISHED.getCode());
        return result;
    }

    @Override
    public List<WorkflowVersionVO> listPublishedVersions(Long agentId) {
        Agent agent = requireAgent(agentId);
        migrateLegacyIfNeeded(agent);

        List<AgentVersion> rows = agentVersionMapper.selectList(
                new LambdaQueryWrapper<AgentVersion>()
                        .eq(AgentVersion::getAgentId, agentId)
                        .eq(AgentVersion::getStatus, AgentVersionStatus.PUBLISHED)
                        .orderByDesc(AgentVersion::getVersion));

        int current = agent.getVersion() != null ? agent.getVersion() : 0;

        List<WorkflowVersionVO> list = new ArrayList<>();
        for (AgentVersion row : rows) {
            list.add(WorkflowVersionVO.builder()
                    .version(row.getVersion())
                    .publishedAt(row.getPublishTime())
                    .nodeCount(row.getNodeCount())
                    .edgeCount(row.getEdgeCount())
                    .current(row.getVersion() != null && row.getVersion().equals(current))
                    .description(row.getDescription())
                    .build());
        }
        return list;
    }

    @Override
    public Map<String, Object> getPublishedVersionGraph(Long agentId, Integer version) {
        Map<String, Object> detail = getPublishedVersionDetail(agentId, version);
        if (!KIND_WORKFLOW.equals(detail.get("kind"))) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "该版本不是工作流类型");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> graph = detail.get("graph") instanceof Map
                ? new HashMap<>((Map<String, Object>) detail.get("graph")) : new HashMap<>();
        graph.put("version", version);
        return graph;
    }

    @Override
    public Map<String, Object> getPublishedVersionDetail(Long agentId, Integer version) {
        requireAgent(agentId);
        AgentVersion row = requirePublishedRow(agentId, version);
        Map<String, Object> snap = parseJsonMap(row.getConfig());

        Map<String, Object> result = new HashMap<>();
        result.put("version", version);
        result.put("description", row.getDescription());
        result.put("publishedAt", row.getPublishTime());

        if (KIND_CHAT.equals(snap.get("kind"))) {
            result.put("kind", KIND_CHAT);
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = snap.get("payload") instanceof Map
                    ? (Map<String, Object>) snap.get("payload") : Map.of();
            result.put("payload", payload);
        } else {
            result.put("kind", KIND_WORKFLOW);
            result.put("graph", extractWorkflowGraph(row));
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restorePublishedToDraft(Long agentId, Integer version) {
        Agent agent = requireAgent(agentId);
        AgentVersion row = requirePublishedRow(agentId, version);
        Map<String, Object> snap = parseJsonMap(row.getConfig());

        if (KIND_CHAT.equals(snap.get("kind"))) {
            restoreChatSnapshotToDraft(agent, snap);
        } else {
            restoreWorkflowSnapshotToDraft(agent, snap, version);
        }
    }

    private void restoreWorkflowSnapshotToDraft(Agent agent, Map<String, Object> snap, Integer version) {
        Map<String, Object> graph = extractWorkflowGraphFromSnap(snap);
        if (graph == null) {
            graph = getPublishedVersionGraph(agent.getId(), version);
            graph.remove("version");
        }

        AgentVersion draft = getOrCreateDraftRow(agent);
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("kind", KIND_WORKFLOW);
        snapshot.put("graph", graph);
        draft.setConfig(writeJson(snapshot));
        draft.setNodeCount(countNodesInMap(graph));
        draft.setEdgeCount(countEdgesInMap(graph));
        agentVersionMapper.updateById(draft);
        updateAgentStatusAfterRestore(agent);
    }

    @SuppressWarnings("unchecked")
    private void restoreChatSnapshotToDraft(Agent agent, Map<String, Object> snap) {
        Map<String, Object> payload = snap.get("payload") instanceof Map
                ? (Map<String, Object>) snap.get("payload") : Map.of();

        if (payload.get("systemPrompt") != null) {
            agent.setSystemPrompt(String.valueOf(payload.get("systemPrompt")));
        }
        if (payload.get("welcomeMessage") != null) {
            agent.setWelcomeMessage(String.valueOf(payload.get("welcomeMessage")));
        }
        if (payload.get("recommendedQuestions") != null) {
            Object rq = payload.get("recommendedQuestions");
            try {
                agent.setRecommendedQuestions(rq instanceof String ? (String) rq : objectMapper.writeValueAsString(rq));
            } catch (Exception e) {
                log.warn("[AgentVersion] 恢复推荐问题失败: {}", e.getMessage());
            }
        }
        if (payload.get("config") instanceof Map<?, ?> cfg) {
            Map<String, Object> configMap = new HashMap<>();
            cfg.forEach((k, v) -> configMap.put(String.valueOf(k), v));
            agent.setConfig(writeJson(configMap));
        }
        agentMapper.updateById(agent);

        Long agentId = agent.getId();
        agentService.updateKnowledgeBindings(agentId, toLongList(payload.get("knowledgeIds")));
        agentService.updateToolBindings(agentId, toLongList(payload.get("toolIds")));
        agentService.updateMcpServerBindings(agentId, toLongList(payload.get("mcpServerIds")));
        agentService.updateSubAgentBindings(agentId, toLongList(payload.get("subAgentIds")));

        saveDraftConfig(agent, snap, 0, 0);
        updateAgentStatusAfterRestore(agent);
    }

    private void updateAgentStatusAfterRestore(Agent agent) {
        int publishedVer = agent.getVersion() != null ? agent.getVersion() : 0;
        if (publishedVer > 0) {
            agent.setStatus(AgentStatus.PUBLISHED_EDITING);
        } else {
            agent.setStatus(AgentStatus.DRAFT);
        }
        agentMapper.updateById(agent);
    }

    private AgentVersion requirePublishedRow(Long agentId, Integer version) {
        AgentVersion row = agentVersionMapper.selectOne(
                new LambdaQueryWrapper<AgentVersion>()
                        .eq(AgentVersion::getAgentId, agentId)
                        .eq(AgentVersion::getStatus, AgentVersionStatus.PUBLISHED)
                        .eq(AgentVersion::getVersion, version)
                        .last("LIMIT 1"));
        if (row == null) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "版本不存在: " + version);
        }
        return row;
    }

    private Map<String, Object> extractWorkflowGraphFromSnap(Map<String, Object> snap) {
        if (snap.get("graph") instanceof Map<?, ?> graph) {
            @SuppressWarnings("unchecked")
            Map<String, Object> g = (Map<String, Object>) graph;
            return g;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Long> toLongList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Number n) {
                ids.add(n.longValue());
            } else if (item != null && !String.valueOf(item).isBlank()) {
                ids.add(Long.parseLong(String.valueOf(item)));
            }
        }
        return ids;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> publishChatAgent(Long agentId, String description) {
        Agent agent = requireAgent(agentId);
        Map<String, Object> snapshot = buildChatSnapshot(agent);

        int nextVersion = (agent.getVersion() != null ? agent.getVersion() : 0) + 1;
        AgentVersion published = new AgentVersion();
        published.setAgentId(agentId);
        published.setUserId(agent.getUserId());
        published.setVersion(nextVersion);
        published.setStatus(AgentVersionStatus.PUBLISHED);
        published.setConfig(writeJson(snapshot));
        published.setPublishTime(LocalDateTime.now());
        published.setDescription(normalizePublishDescription(description));
        agentVersionMapper.insert(published);

        saveDraftConfig(agent, snapshot, 0, 0);

        agent.setVersion(nextVersion);
        agent.setStatus(AgentStatus.PUBLISHED);
        agent.setPublishTime(LocalDateTime.now());
        agentMapper.updateById(agent);

        Map<String, Object> result = new HashMap<>();
        result.put("version", nextVersion);
        result.put("status", AgentStatus.PUBLISHED.getCode());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveChatDraft(Long agentId) {
        Agent agent = requireAgent(agentId);
        Map<String, Object> snapshot = buildChatSnapshot(agent);
        saveDraftConfig(agent, snapshot, 0, 0);
        if (agent.getStatus() == AgentStatus.PUBLISHED) {
            agent.setStatus(AgentStatus.PUBLISHED_EDITING);
            agentMapper.updateById(agent);
        }
    }

    @Override
    public Map<String, Object> loadPublishedRuntimeConfig(Long agentId) {
        AgentVersion row = getLatestPublishedRow(agentId);
        if (row == null || row.getConfig() == null) {
            return null;
        }
        Map<String, Object> snap = parseJsonMap(row.getConfig());
        if (KIND_CHAT.equals(snap.get("kind"))) {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = snap.get("payload") instanceof Map
                    ? (Map<String, Object>) snap.get("payload") : snap;
            return payload;
        }
        return null;
    }

    @Override
    public WorkflowDefinition loadWorkflowDefinition(Long agentId, boolean useDraft) {
        Agent agent = requireAgent(agentId);
        migrateLegacyIfNeeded(agent);
        Map<String, Object> graph;
        if (useDraft) {
            graph = extractWorkflowGraph(getDraftRow(agentId));
        } else {
            graph = extractWorkflowGraph(getLatestPublishedRow(agentId));
            if (graph == null) {
                graph = extractWorkflowGraph(getDraftRow(agentId));
            }
        }
        return WorkflowConfigParser.toDefinition(graph, objectMapper);
    }

    @Override
    public void initDraftOnCreate(Agent agent) {
        if (agent.getId() == null) {
            return;
        }
        AgentVersion existing = getDraftRow(agent.getId());
        if (existing != null) {
            return;
        }
        Map<String, Object> snapshot = new HashMap<>();
        if (agent.getAgentType() == AgentType.WORKFLOW) {
            snapshot.put("kind", KIND_WORKFLOW);
            snapshot.put("graph", Map.of("nodes", List.of(), "edges", List.of(), "globalConfig", Map.of()));
        } else {
            snapshot.put("kind", KIND_CHAT);
            snapshot.put("payload", buildChatSnapshot(agent));
        }
        AgentVersion draft = new AgentVersion();
        draft.setAgentId(agent.getId());
        draft.setUserId(agent.getUserId());
        draft.setVersion(0);
        draft.setStatus(AgentVersionStatus.DRAFT);
        draft.setConfig(writeJson(snapshot));
        agentVersionMapper.insert(draft);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void migrateLegacyIfNeeded(Agent agent) {
        if (agent == null || agent.getId() == null) {
            return;
        }
        Long agentId = agent.getId();
        long draftCount = agentVersionMapper.selectCount(
                new LambdaQueryWrapper<AgentVersion>().eq(AgentVersion::getAgentId, agentId));
        if (draftCount > 0) {
            return;
        }

        Map<String, Object> config = WorkflowConfigParser.parseConfigMap(agent.getConfig(), objectMapper);
        Map<String, Object> draftGraph = WorkflowConfigParser.resolveDraftGraph(config);
        if (draftGraph != null) {
            Map<String, Object> snapshot = new HashMap<>();
            snapshot.put("kind", KIND_WORKFLOW);
            snapshot.put("graph", draftGraph);
            AgentVersion draft = new AgentVersion();
            draft.setAgentId(agentId);
            draft.setUserId(agent.getUserId());
            draft.setVersion(0);
            draft.setStatus(AgentVersionStatus.DRAFT);
            draft.setConfig(writeJson(snapshot));
            draft.setNodeCount(countNodesInMap(draftGraph));
            draft.setEdgeCount(countEdgesInMap(draftGraph));
            agentVersionMapper.insert(draft);
        }

        List<Map<String, Object>> legacyVersions = WorkflowConfigParser.getVersions(config);
        for (Map<String, Object> leg : legacyVersions) {
            int ver = leg.get("version") instanceof Number ? ((Number) leg.get("version")).intValue() : 0;
            if (ver <= 0) {
                continue;
            }
            Map<String, Object> graph = new HashMap<>();
            graph.put("nodes", leg.get("nodes") != null ? leg.get("nodes") : List.of());
            graph.put("edges", leg.get("edges") != null ? leg.get("edges") : List.of());
            if (leg.get("globalConfig") != null) {
                graph.put("globalConfig", leg.get("globalConfig"));
            }
            Map<String, Object> snapshot = new HashMap<>();
            snapshot.put("kind", KIND_WORKFLOW);
            snapshot.put("graph", graph);

            AgentVersion pub = new AgentVersion();
            pub.setAgentId(agentId);
            pub.setUserId(agent.getUserId());
            pub.setVersion(ver);
            pub.setStatus(AgentVersionStatus.PUBLISHED);
            pub.setConfig(writeJson(snapshot));
            pub.setNodeCount(countNodesInMap(graph));
            pub.setEdgeCount(countEdgesInMap(graph));
            if (leg.get("publishedAt") != null) {
                try {
                    pub.setPublishTime(LocalDateTime.parse(leg.get("publishedAt").toString()));
                } catch (Exception ignored) {
                    pub.setPublishTime(LocalDateTime.now());
                }
            }
            agentVersionMapper.insert(pub);
        }

        stripLegacyWorkflowFromAgentConfig(agent);
        if (!legacyVersions.isEmpty() || draftGraph != null) {
            agentMapper.updateById(agent);
        }
    }

    private void saveDraftConfig(Agent agent, Map<String, Object> snapshot, int nodeCount, int edgeCount) {
        AgentVersion draft = getOrCreateDraftRow(agent);
        draft.setConfig(writeJson(snapshot));
        draft.setNodeCount(nodeCount);
        draft.setEdgeCount(edgeCount);
        agentVersionMapper.updateById(draft);
    }

    private AgentVersion getOrCreateDraftRow(Agent agent) {
        AgentVersion draft = getDraftRow(agent.getId());
        if (draft != null) {
            return draft;
        }
        draft = new AgentVersion();
        draft.setAgentId(agent.getId());
        draft.setUserId(agent.getUserId());
        draft.setVersion(0);
        draft.setStatus(AgentVersionStatus.DRAFT);
        draft.setConfig("{}");
        agentVersionMapper.insert(draft);
        return draft;
    }

    private AgentVersion getDraftRow(Long agentId) {
        return agentVersionMapper.selectOne(
                new LambdaQueryWrapper<AgentVersion>()
                        .eq(AgentVersion::getAgentId, agentId)
                        .eq(AgentVersion::getStatus, AgentVersionStatus.DRAFT)
                        .orderByDesc(AgentVersion::getId)
                        .last("LIMIT 1"));
    }

    private AgentVersion getLatestPublishedRow(Long agentId) {
        return agentVersionMapper.selectOne(
                new LambdaQueryWrapper<AgentVersion>()
                        .eq(AgentVersion::getAgentId, agentId)
                        .eq(AgentVersion::getStatus, AgentVersionStatus.PUBLISHED)
                        .orderByDesc(AgentVersion::getVersion)
                        .last("LIMIT 1"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractWorkflowGraph(AgentVersion row) {
        if (row == null || row.getConfig() == null) {
            return null;
        }
        Map<String, Object> snap = parseJsonMap(row.getConfig());
        if (snap.get("graph") instanceof Map) {
            return (Map<String, Object>) snap.get("graph");
        }
        return snap;
    }

    private Map<String, Object> buildWorkflowSnapshot(WorkflowGraphDTO graph) {
        Map<String, Object> graphMap = new HashMap<>();
        graphMap.put("nodes", graph.getNodes() != null ? graph.getNodes() : List.of());
        graphMap.put("edges", graph.getEdges() != null ? graph.getEdges() : List.of());
        if (graph.getGlobalConfig() != null) {
            graphMap.put("globalConfig", graph.getGlobalConfig());
        }
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("kind", KIND_WORKFLOW);
        snapshot.put("graph", graphMap);
        return snapshot;
    }

    private Map<String, Object> buildChatSnapshot(Agent agent) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("systemPrompt", agent.getSystemPrompt());
        payload.put("welcomeMessage", agent.getWelcomeMessage());
        payload.put("recommendedQuestions", agent.getRecommendedQuestions());
        payload.put("config", WorkflowConfigParser.parseConfigMap(agent.getConfig(), objectMapper));
        payload.put("knowledgeIds", readBindingIds(agent, "knowledges"));
        payload.put("toolIds", readBindingIds(agent, "tools"));
        payload.put("mcpServerIds", readBindingIds(agent, "mcpServers"));
        payload.put("subAgentIds", readBindingIds(agent, "subagents"));

        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("kind", KIND_CHAT);
        snapshot.put("payload", payload);
        return snapshot;
    }

    private String resolveWorkflowStatusCode(Agent agent) {
        if (agent.getStatus() == null) {
            return AgentStatus.DRAFT.getCode();
        }
        return agent.getStatus().getCode();
    }

    private void stripLegacyWorkflowFromAgentConfig(Agent agent) {
        Map<String, Object> config = WorkflowConfigParser.parseConfigMap(agent.getConfig(), objectMapper);
        config.remove(WorkflowConfigKeys.WORKFLOW_DRAFT);
        config.remove(WorkflowConfigKeys.WORKFLOW_PUBLISHED);
        config.remove(WorkflowConfigKeys.WORKFLOW_VERSIONS);
        config.remove(WorkflowConfigKeys.WORKFLOW_LEGACY);
        config.remove(WorkflowConfigKeys.PUBLISHED_VERSION);
        config.remove(WorkflowConfigKeys.WORKFLOW_STATUS);
        try {
            agent.setConfig(objectMapper.writeValueAsString(config));
        } catch (Exception e) {
            log.warn("[AgentVersion] 清理 agent.config 失败: {}", e.getMessage());
        }
    }

    private int countNodes(WorkflowGraphDTO graph) {
        return graph.getNodes() != null ? graph.getNodes().size() : 0;
    }

    private int countEdges(WorkflowGraphDTO graph) {
        return graph.getEdges() != null ? graph.getEdges().size() : 0;
    }

    private int countNodesInMap(Map<String, Object> graph) {
        return graph.get("nodes") instanceof List<?> l ? l.size() : 0;
    }

    private int countEdgesInMap(Map<String, Object> graph) {
        return graph.get("edges") instanceof List<?> l ? l.size() : 0;
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private String writeJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR.getCode(), "序列化版本配置失败");
        }
    }

    private String normalizePublishDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        String trimmed = description.trim();
        if (trimmed.length() > 50) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "发布说明不能超过50字");
        }
        return trimmed;
    }

    private Agent requireAgent(Long agentId) {
        Agent agent = agentMapper.selectById(agentId);
        if (agent == null) {
            throw new BizException(ErrorCode.AGENT_NOT_FOUND);
        }
        return agent;
    }

    /**
     * 从 agent.config 读取绑定 ID 列表（与 AgentServiceImpl 解析规则一致）
     */
    private List<Long> readBindingIds(Agent agent, String field) {
        if (agent.getConfig() == null || agent.getConfig().isBlank()) {
            return List.of();
        }
        try {
            var configNode = objectMapper.readTree(agent.getConfig());
            if (!configNode.has(field)) {
                return List.of();
            }
            var arr = configNode.get(field);
            if ("knowledges".equals(field)) {
                List<Long> ids = new ArrayList<>();
                for (var node : arr) {
                    if (node.isNumber()) {
                        ids.add(node.longValue());
                    } else if (node.isTextual()) {
                        String text = node.asText();
                        if (text != null && !text.isBlank()) {
                            ids.add(Long.parseLong(text));
                        }
                    }
                }
                return ids;
            }
            return objectMapper.convertValue(arr, new com.fasterxml.jackson.core.type.TypeReference<List<Long>>() {});
        } catch (Exception e) {
            log.warn("[AgentVersion] 解析 config.{} 失败: agentId={}, error={}", field, agent.getId(), e.getMessage());
            return List.of();
        }
    }
}
