package com.lightbot.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.BizException;
import com.lightbot.dto.WorkflowGraphDTO;
import com.lightbot.dto.WorkflowNodeTestRequest;
import com.lightbot.dto.WorkflowResumeRequest;
import com.lightbot.dto.WorkflowTestRequest;
import com.lightbot.dto.WorkflowTestResultVO;
import com.lightbot.dto.WorkflowVersionVO;
import com.lightbot.entity.Agent;
import com.lightbot.enums.ErrorCode;
import com.lightbot.enums.NodeType;
import com.lightbot.service.AgentService;
import com.lightbot.service.AgentVersionService;
import com.lightbot.service.WorkflowConfigService;
import com.lightbot.workflow.WorkflowConfigParser;
import com.lightbot.workflow.WorkflowDefinition;
import com.lightbot.workflow.WorkflowExecutorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工作流配置：委托 AgentVersionService，版本数据存 agent_version 表
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowConfigServiceImpl implements WorkflowConfigService {

    private final AgentService agentService;
    private final AgentVersionService agentVersionService;
    private final ObjectMapper objectMapper;
    private final WorkflowExecutorService workflowExecutorService;

    @Override
    public Map<String, Object> getWorkflowConfig(Long agentId) {
        return agentVersionService.getWorkflowEditorState(agentId);
    }

    @Override
    public void saveDraft(Long agentId, WorkflowGraphDTO graph) {
        agentVersionService.saveWorkflowDraft(agentId, graph);
    }

    @Override
    public Map<String, Object> publish(Long agentId, WorkflowGraphDTO graph) {
        List<String> errors = validate(agentId, graph);
        if (!errors.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), String.join("；", errors));
        }
        return agentVersionService.publishWorkflow(agentId, graph);
    }

    @Override
    public List<String> validate(Long agentId, WorkflowGraphDTO graph) {
        requireAgent(agentId);
        return validateGraph(graph);
    }

    @Override
    public List<WorkflowVersionVO> listVersions(Long agentId) {
        return agentVersionService.listPublishedVersions(agentId);
    }

    @Override
    public void restoreVersion(Long agentId, Integer version) {
        agentVersionService.restorePublishedToDraft(agentId, version);
    }

    @Override
    public Map<String, Object> getVersionGraph(Long agentId, Integer version) {
        return agentVersionService.getPublishedVersionGraph(agentId, version);
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
            definition = agentVersionService.loadWorkflowDefinition(agentId, useDraft);
        }
        if (definition == null || definition.getNodes() == null || definition.getNodes().isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "工作流为空，请先配置节点");
        }

        Map<String, Object> initialVariables = buildTestInitialVariables(request);

        boolean usedDraft = request.getGraph() != null
                || request.getUseDraft() == null
                || Boolean.TRUE.equals(request.getUseDraft());

        List<Map<String, Object>> events = new ArrayList<>();
        WorkflowTestResultVO result = workflowExecutorService.executeForTest(
                agent, definition, request.getInput(), events, initialVariables);
        result.setUsedDraft(usedDraft);
        return result;
    }

    @Override
    public WorkflowTestResultVO resumeWorkflow(Long agentId, WorkflowResumeRequest request) {
        requireAgent(agentId);
        Map<String, Object> formData = request.getFormData() != null ? request.getFormData() : Map.of();
        return workflowExecutorService.resumeAfterConfirm(agentId, request.getRunId(), formData);
    }

    @Override
    public WorkflowTestResultVO testNode(Long agentId, WorkflowNodeTestRequest request) {
        Agent agent = requireAgent(agentId);
        WorkflowDefinition definition;
        if (request.getGraph() != null
                && request.getGraph().getNodes() != null
                && !request.getGraph().getNodes().isEmpty()) {
            definition = WorkflowConfigParser.toDefinition(toGraphMap(request.getGraph()), objectMapper);
        } else {
            definition = agentVersionService.loadWorkflowDefinition(agentId, true);
        }
        if (definition == null || definition.getNodes() == null || definition.getNodes().isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "工作流为空，请先配置节点");
        }

        Map<String, Object> vars = new HashMap<>();
        if (request.getInputParams() != null) {
            request.getInputParams().forEach((k, v) -> {
                if (k != null && !k.isBlank()) {
                    vars.put(k, v != null ? v : "");
                }
            });
        }
        Object query = vars.get("query");
        Object input = vars.get("input");
        if (query == null || String.valueOf(query).isBlank()) {
            vars.put("query", input != null ? input : "测试输入");
        }
        if (input == null || String.valueOf(input).isBlank()) {
            vars.put("input", vars.get("query"));
        }

        try {
            return workflowExecutorService.executeSingleNode(agent, definition, request.getNodeId(), vars);
        } catch (Exception e) {
            // 单节点测试：业务/执行异常也返回结构化结果，避免前端只能走 HTTP 错误
            log.warn("[WorkflowConfigService] 单节点测试异常: agentId={}, nodeId={}, msg={}",
                    agentId, request.getNodeId(), e.getMessage());
            Map<String, Object> failEvent = new HashMap<>();
            failEvent.put("type", "workflow_node_complete");
            failEvent.put("nodeId", request.getNodeId());
            failEvent.put("success", false);
            failEvent.put("message", e.getMessage() != null ? e.getMessage() : "执行失败");
            failEvent.put("durationMs", 0L);
            String errMsg = e.getMessage() != null ? e.getMessage() : "执行失败";
            return WorkflowTestResultVO.builder()
                    .output(errMsg)
                    .nodeEvents(List.of(failEvent))
                    .usedDraft(true)
                    .build();
        }
    }

    /**
     * 构建调试运行预置变量：文本生成 / 文本对话
     */
    private Map<String, Object> buildTestInitialVariables(WorkflowTestRequest request) {
        Map<String, Object> vars = new HashMap<>();
        String input = request.getInput();
        if (input != null) {
            vars.put("input", input);
            vars.put("query", input);
        }
        if ("conversation".equalsIgnoreCase(request.getTestMode())
                && request.getConversationHistory() != null
                && !request.getConversationHistory().isEmpty()) {
            vars.put("history_list", request.getConversationHistory());
            StringBuilder historyText = new StringBuilder();
            for (Map<String, String> msg : request.getConversationHistory()) {
                if (msg == null) {
                    continue;
                }
                String role = msg.getOrDefault("role", "user");
                String content = msg.getOrDefault("content", "");
                historyText.append(role).append(": ").append(content).append("\n");
            }
            vars.put("history", historyText.toString().trim());
        }
        return vars;
    }

    private Agent requireAgent(Long agentId) {
        Agent agent = agentService.getById(agentId);
        if (agent == null) {
            throw new BizException(ErrorCode.AGENT_NOT_FOUND);
        }
        return agent;
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
            // 条件分支节点必须有默认路径（out_c 边）
            if ("condition".equals(type)) {
                boolean hasDefaultEdge = edges.stream().anyMatch(e ->
                        id.equals(String.valueOf(e.get("source")))
                                && "out_c".equals(String.valueOf(e.get("sourceHandle"))));
                if (!hasDefaultEdge) {
                    errors.add("条件分支节点缺少默认路径: " + id);
                }
            }
            try {
                NodeType.fromValue(type);
            } catch (IllegalArgumentException e) {
                errors.add("未知节点类型: " + type);
            }
        }

        // 环路检测：DFS 判断有向图中是否存在环
        String cycleResult = detectCycle(nodes, edges);
        if (cycleResult != null) {
            errors.add("工作流存在环路: " + cycleResult);
        }

        return errors;
    }

    /**
     * DFS 检测有向图环路
     * @return null 表示无环，非 null 返回环路描述
     */
    @SuppressWarnings("unchecked")
    private String detectCycle(List<Map<String, Object>> nodes, List<Map<String, Object>> edges) {
        // 构建邻接表
        Map<String, List<String>> adj = new HashMap<>();
        for (Map<String, Object> node : nodes) {
            String id = node.get("id") != null ? node.get("id").toString() : "";
            adj.put(id, new ArrayList<>());
        }
        for (Map<String, Object> edge : edges) {
            String src = edge.get("source") != null ? edge.get("source").toString() : "";
            String tgt = edge.get("target") != null ? edge.get("target").toString() : "";
            adj.computeIfAbsent(src, k -> new ArrayList<>()).add(tgt);
        }

        // DFS 状态：0=未访问，1=访问中，2=已完成
        Map<String, Integer> state = new HashMap<>();
        Map<String, String> parent = new HashMap<>();
        for (String nodeId : adj.keySet()) {
            if (state.getOrDefault(nodeId, 0) == 0) {
                String cycle = dfsDetectCycle(adj, nodeId, state, parent);
                if (cycle != null) {
                    return cycle;
                }
            }
        }
        return null;
    }

    private String dfsDetectCycle(Map<String, List<String>> adj, String node,
                                  Map<String, Integer> state, Map<String, String> parent) {
        state.put(node, 1); // 标记为访问中
        List<String> neighbors = adj.getOrDefault(node, List.of());
        for (String next : neighbors) {
            Integer nextState = state.getOrDefault(next, 0);
            if (nextState == 1) {
                // 找到环，回溯环路路径
                return buildCyclePath(parent, node, next);
            }
            if (nextState == 0) {
                parent.put(next, node);
                String cycle = dfsDetectCycle(adj, next, state, parent);
                if (cycle != null) {
                    return cycle;
                }
            }
        }
        state.put(node, 2); // 标记为已完成
        return null;
    }

    private String buildCyclePath(Map<String, String> parent, String from, String to) {
        List<String> path = new ArrayList<>();
        path.add(to);
        String cur = from;
        while (cur != null && !cur.equals(to)) {
            path.add(cur);
            cur = parent.get(cur);
        }
        path.add(to);
        java.util.Collections.reverse(path);
        return String.join(" → ", path);
    }
}
