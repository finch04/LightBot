package com.lightbot.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.BizException;
import com.lightbot.dto.WorkflowGraphDTO;
import com.lightbot.dto.WorkflowTestRequest;
import com.lightbot.dto.WorkflowTestResultVO;
import com.lightbot.dto.WorkflowVersionVO;
import com.lightbot.entity.Agent;
import com.lightbot.enums.ErrorCode;
import com.lightbot.enums.NodeType;
import com.lightbot.service.AgentService;
import com.lightbot.service.WorkflowConfigService;
import com.lightbot.workflow.WorkflowConfigKeys;
import com.lightbot.workflow.WorkflowConfigParser;
import com.lightbot.workflow.WorkflowDefinition;
import com.lightbot.workflow.WorkflowExecutorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工作流配置：草稿暂存、发布、版本、调试
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowConfigServiceImpl implements WorkflowConfigService {

    private final AgentService agentService;
    private final ObjectMapper objectMapper;
    private final WorkflowExecutorService workflowExecutorService;

    @Override
    public Map<String, Object> getWorkflowConfig(Long agentId) {
        Agent agent = requireAgent(agentId);
        Map<String, Object> config = WorkflowConfigParser.parseConfigMap(agent.getConfig(), objectMapper);
        Map<String, Object> result = new HashMap<>();
        result.put("draft", WorkflowConfigParser.resolveDraftGraph(config));
        result.put("published", WorkflowConfigParser.resolvePublishedGraph(config));
        result.put("publishedVersion", config.getOrDefault(WorkflowConfigKeys.PUBLISHED_VERSION, 0));
        result.put("status", config.getOrDefault(WorkflowConfigKeys.WORKFLOW_STATUS, WorkflowConfigKeys.STATUS_DRAFT));
        return result;
    }

    @Override
    public void saveDraft(Long agentId, WorkflowGraphDTO graph) {
        Agent agent = requireAgent(agentId);
        Map<String, Object> config = WorkflowConfigParser.parseConfigMap(agent.getConfig(), objectMapper);
        Map<String, Object> graphMap = toGraphMap(graph);
        config.put(WorkflowConfigKeys.WORKFLOW_DRAFT, graphMap);
        config.put(WorkflowConfigKeys.WORKFLOW_LEGACY, graphMap);
        if (!WorkflowConfigKeys.STATUS_PUBLISHED.equals(config.get(WorkflowConfigKeys.WORKFLOW_STATUS))) {
            config.put(WorkflowConfigKeys.WORKFLOW_STATUS, WorkflowConfigKeys.STATUS_DRAFT);
        }
        persistConfig(agent, config);
    }

    @Override
    public Map<String, Object> publish(Long agentId, WorkflowGraphDTO graph) {
        List<String> errors = validate(agentId, graph);
        if (!errors.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), String.join("；", errors));
        }

        Agent agent = requireAgent(agentId);
        Map<String, Object> config = WorkflowConfigParser.parseConfigMap(agent.getConfig(), objectMapper);
        Map<String, Object> graphMap = toGraphMap(graph);

        int currentVersion = config.get(WorkflowConfigKeys.PUBLISHED_VERSION) instanceof Number
                ? ((Number) config.get(WorkflowConfigKeys.PUBLISHED_VERSION)).intValue() : 0;
        int nextVersion = currentVersion + 1;

        Map<String, Object> versionSnapshot = new HashMap<>(graphMap);
        versionSnapshot.put("version", nextVersion);
        versionSnapshot.put("publishedAt", LocalDateTime.now().toString());

        List<Map<String, Object>> versions = new ArrayList<>(WorkflowConfigParser.getVersions(config));
        versions.add(versionSnapshot);

        config.put(WorkflowConfigKeys.WORKFLOW_DRAFT, graphMap);
        config.put(WorkflowConfigKeys.WORKFLOW_PUBLISHED, graphMap);
        config.put(WorkflowConfigKeys.WORKFLOW_LEGACY, graphMap);
        config.put(WorkflowConfigKeys.WORKFLOW_VERSIONS, versions);
        config.put(WorkflowConfigKeys.PUBLISHED_VERSION, nextVersion);
        config.put(WorkflowConfigKeys.WORKFLOW_STATUS, WorkflowConfigKeys.STATUS_PUBLISHED);

        persistConfig(agent, config);

        Map<String, Object> result = new HashMap<>();
        result.put("version", nextVersion);
        result.put("status", WorkflowConfigKeys.STATUS_PUBLISHED);
        return result;
    }

    @Override
    public List<String> validate(Long agentId, WorkflowGraphDTO graph) {
        requireAgent(agentId);
        return validateGraph(graph);
    }

    @Override
    public List<WorkflowVersionVO> listVersions(Long agentId) {
        Agent agent = requireAgent(agentId);
        Map<String, Object> config = WorkflowConfigParser.parseConfigMap(agent.getConfig(), objectMapper);
        int publishedVersion = config.get(WorkflowConfigKeys.PUBLISHED_VERSION) instanceof Number
                ? ((Number) config.get(WorkflowConfigKeys.PUBLISHED_VERSION)).intValue() : 0;

        List<WorkflowVersionVO> list = new ArrayList<>();
        for (Map<String, Object> snap : WorkflowConfigParser.getVersions(config)) {
            int version = snap.get("version") instanceof Number ? ((Number) snap.get("version")).intValue() : 0;
            int nodeCount = 0;
            int edgeCount = 0;
            if (snap.get("nodes") instanceof List<?> nodes) {
                nodeCount = nodes.size();
            }
            if (snap.get("edges") instanceof List<?> edges) {
                edgeCount = edges.size();
            }
            LocalDateTime publishedAt = null;
            if (snap.get("publishedAt") != null) {
                try {
                    publishedAt = LocalDateTime.parse(snap.get("publishedAt").toString());
                } catch (Exception ignored) {
                    // ignore parse error
                }
            }
            list.add(WorkflowVersionVO.builder()
                    .version(version)
                    .publishedAt(publishedAt)
                    .nodeCount(nodeCount)
                    .edgeCount(edgeCount)
                    .current(version == publishedVersion)
                    .build());
        }
        list.sort((a, b) -> Integer.compare(b.getVersion(), a.getVersion()));
        return list;
    }

    @Override
    public void restoreVersion(Long agentId, Integer version) {
        Agent agent = requireAgent(agentId);
        Map<String, Object> config = WorkflowConfigParser.parseConfigMap(agent.getConfig(), objectMapper);
        Map<String, Object> target = WorkflowConfigParser.getVersions(config).stream()
                .filter(v -> version.equals(v.get("version") instanceof Number
                        ? ((Number) v.get("version")).intValue() : null))
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.BAD_REQUEST.getCode(), "版本不存在: " + version));

        Map<String, Object> graphMap = new HashMap<>();
        graphMap.put("nodes", target.get("nodes"));
        graphMap.put("edges", target.get("edges"));
        if (target.get("globalConfig") != null) {
            graphMap.put("globalConfig", target.get("globalConfig"));
        }

        config.put(WorkflowConfigKeys.WORKFLOW_DRAFT, graphMap);
        config.put(WorkflowConfigKeys.WORKFLOW_LEGACY, graphMap);
        persistConfig(agent, config);
    }

    @Override
    public WorkflowTestResultVO testRun(Long agentId, WorkflowTestRequest request) {
        Agent agent = requireAgent(agentId);
        WorkflowDefinition definition;
        if (request.getGraph() != null
                && request.getGraph().getNodes() != null
                && !request.getGraph().getNodes().isEmpty()) {
            definition = WorkflowConfigParser.toDefinition(toGraphMap(request.getGraph()), objectMapper);
        } else {
            boolean useDraft = request.getUseDraft() == null || Boolean.TRUE.equals(request.getUseDraft());
            definition = WorkflowConfigParser.fromAgentConfig(agent.getConfig(), useDraft, objectMapper);
        }
        if (definition == null || definition.getNodes() == null || definition.getNodes().isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "工作流为空，请先配置节点");
        }

        List<Map<String, Object>> events = new ArrayList<>();
        String output = workflowExecutorService.executeWithDefinition(
                agent, definition, null, request.getInput(), events);

        boolean usedDraft = request.getGraph() != null
                || request.getUseDraft() == null
                || Boolean.TRUE.equals(request.getUseDraft());

        return WorkflowTestResultVO.builder()
                .output(output)
                .nodeEvents(events)
                .usedDraft(usedDraft)
                .build();
    }

    private Agent requireAgent(Long agentId) {
        Agent agent = agentService.getById(agentId);
        if (agent == null) {
            throw new BizException(ErrorCode.AGENT_NOT_FOUND);
        }
        return agent;
    }

    private void persistConfig(Agent agent, Map<String, Object> config) {
        try {
            agent.setConfig(objectMapper.writeValueAsString(config));
            agentService.updateById(agent);
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR.getCode(), "保存工作流配置失败");
        }
    }

    private Map<String, Object> toGraphMap(WorkflowGraphDTO graph) {
        Map<String, Object> map = new HashMap<>();
        map.put("nodes", graph.getNodes() != null ? graph.getNodes() : List.of());
        map.put("edges", graph.getEdges() != null ? graph.getEdges() : List.of());
        if (graph.getGlobalConfig() != null) {
            map.put("globalConfig", graph.getGlobalConfig());
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private List<String> validateGraph(WorkflowGraphDTO graph) {
        List<String> errors = new ArrayList<>();
        if (graph == null || graph.getNodes() == null || graph.getNodes().isEmpty()) {
            errors.add("工作流节点为空");
            return errors;
        }

        List<Map<String, Object>> nodes = graph.getNodes();
        List<Map<String, Object>> edges = graph.getEdges() != null ? graph.getEdges() : List.of();

        long startCount = nodes.stream().filter(n -> "start".equals(String.valueOf(n.get("type")))).count();
        if (startCount == 0) {
            errors.add("缺少开始节点");
        } else if (startCount > 1) {
            errors.add("只能有一个开始节点");
        }

        long endCount = nodes.stream().filter(n -> "end".equals(String.valueOf(n.get("type")))).count();
        if (endCount == 0) {
            errors.add("缺少结束节点");
        }

        Set<String> connected = new HashSet<>();
        for (Map<String, Object> edge : edges) {
            if (edge.get("source") != null) {
                connected.add(edge.get("source").toString());
            }
            if (edge.get("target") != null) {
                connected.add(edge.get("target").toString());
            }
        }

        for (Map<String, Object> node : nodes) {
            String type = node.get("type") != null ? node.get("type").toString() : "";
            String id = node.get("id") != null ? node.get("id").toString() : "";
            if (!"start".equals(type) && !connected.contains(id)) {
                errors.add("节点未连接: " + id);
            }
            Map<String, Object> data = node.get("data") instanceof Map
                    ? (Map<String, Object>) node.get("data") : Map.of();
            if ("llm".equals(type)) {
                if (data.get("providerId") == null) {
                    errors.add("LLM节点未选择提供商: " + id);
                }
                if (data.get("modelId") == null) {
                    errors.add("LLM节点未选择模型: " + id);
                }
            }
            if ("retrieval".equals(type) && data.get("knowledgeId") == null) {
                errors.add("知识检索节点未选择知识库: " + id);
            }
            if ("tool".equals(type) && data.get("toolId") == null) {
                errors.add("工具节点未选择工具: " + id);
            }
            try {
                NodeType.fromValue(type);
            } catch (IllegalArgumentException e) {
                errors.add("未知节点类型: " + type);
            }
        }
        return errors;
    }
}
