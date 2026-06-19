package com.lightbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.BizException;
import com.lightbot.constant.ConfigKeys;
import com.lightbot.dto.WorkflowGraphDTO;
import com.lightbot.dto.WorkflowVersionVO;
import com.lightbot.entity.Agent;
import com.lightbot.entity.AgentVersion;
import com.lightbot.entity.Knowledge;
import com.lightbot.entity.McpServer;
import com.lightbot.entity.Skill;
import com.lightbot.entity.SubAgent;
import com.lightbot.entity.Tool;
import com.lightbot.enums.AgentStatus;
import com.lightbot.enums.AgentType;
import com.lightbot.enums.AgentVersionStatus;
import com.lightbot.enums.ErrorCode;
import com.lightbot.mapper.AgentMapper;
import com.lightbot.mapper.AgentVersionMapper;
import com.lightbot.service.AgentService;
import com.lightbot.service.AgentVersionService;
import com.lightbot.service.KnowledgeService;
import com.lightbot.service.McpServerService;
import com.lightbot.service.SkillService;
import com.lightbot.service.SubAgentService;
import com.lightbot.service.ToolService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final KnowledgeService knowledgeService;
    private final ToolService toolService;
    private final McpServerService mcpServerService;
    private final SubAgentService subAgentService;
    private final SkillService skillService;

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
        Agent agent = requireAgent(agentId);
        AgentVersion row = requirePublishedRow(agentId, version);
        Map<String, Object> snap = parseJsonMap(row.getConfig());

        Map<String, Object> result = new HashMap<>();
        result.put("version", version);
        result.put("description", row.getDescription());
        result.put("publishedAt", row.getPublishTime());

        if (KIND_WORKFLOW.equals(snap.get("kind"))) {
            result.put("kind", KIND_WORKFLOW);
            result.put("graph", extractWorkflowGraph(row));
        } else {
            // 对话型：含 kind=chat 与旧版无 kind 的扁平快照
            fillChatVersionDetail(result, snap, agent);
        }
        return result;
    }

    /**
     * 填充对话型版本详情，并按快照中的 ID 回查绑定实体
     */
    /** 模型能力字段：写入版本详情的 modelParams，供历史回显 */
    private static final String[] MODEL_CAPABILITY_KEYS = {
            ConfigKeys.Agent.MULTIMODAL_ENABLED,
            ConfigKeys.Agent.ENABLE_IMAGE_INPUT,
            ConfigKeys.Agent.ENABLE_VIDEO_INPUT,
            ConfigKeys.Agent.ENABLE_AUDIO_INPUT,
            ConfigKeys.Agent.ENABLE_WEB_SEARCH,
            ConfigKeys.Agent.WEB_SEARCH_FORCE,
            ConfigKeys.Agent.WEB_SEARCH_MAX_KEYWORD,
            ConfigKeys.Agent.ENABLE_TTS,
            ConfigKeys.Agent.ENABLE_REASONING,
    };

    private void putModelCapabilityParams(Map<String, Object> modelParams, Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return;
        }
        for (String key : MODEL_CAPABILITY_KEYS) {
            if (config.containsKey(key)) {
                modelParams.put(key, config.get(key));
            }
        }
    }

    private void fillChatVersionDetail(Map<String, Object> result, Map<String, Object> snap, Agent agent) {
        result.put("kind", KIND_CHAT);
        Map<String, Object> payload = extractChatPayload(snap);

        // 1. 提示词（版本管理）
        Map<String, Object> basicInfo = new HashMap<>();
        basicInfo.put("systemPrompt", payload.get("systemPrompt"));
        result.put("basicInfo", basicInfo);

        // 2. 模型参数（包含在 config 中）
        @SuppressWarnings("unchecked")
        Map<String, Object> config = payload.get("config") instanceof Map
                ? (Map<String, Object>) payload.get("config") : new HashMap<>();
        Map<String, Object> modelParams = new HashMap<>();
        modelParams.put("providerId", stringifyId(config.get("providerId")));
        modelParams.put("modelId", stringifyId(config.get("modelId")));
        modelParams.put("temperature", config.get("temperature"));
        modelParams.put("topP", config.get("topP"));
        modelParams.put("maxTokens", config.get("maxTokens"));
        modelParams.put("presencePenalty", config.get("presencePenalty"));
        modelParams.put("frequencyPenalty", config.get("frequencyPenalty"));
        modelParams.put("repetitionPenalty", config.get("repetitionPenalty"));
        putModelCapabilityParams(modelParams, config);
        result.put("modelParams", modelParams);

        // 3. 对话配置
        Map<String, Object> chatConfig = new HashMap<>();
        chatConfig.put("streamOutput", config.get("streamOutput"));
        chatConfig.put("maxContextMessages", config.get("maxContextMessages"));
        chatConfig.put("enableSummary", config.get(ConfigKeys.Agent.ENABLE_SUMMARY));
        chatConfig.put("summaryThresholdKb", config.get(ConfigKeys.Agent.SUMMARY_THRESHOLD_KB));
        chatConfig.put("summaryPrompt", config.get(ConfigKeys.Agent.SUMMARY_PROMPT));
        chatConfig.put("summaryKeepMessages", config.get(ConfigKeys.Agent.SUMMARY_KEEP_MESSAGES));
        chatConfig.put("summaryToolResultTokenLimit", config.get(ConfigKeys.Agent.SUMMARY_TOOL_RESULT_TOKEN_LIMIT));
        chatConfig.put("userSensitiveFilterEnabled", config.get(ConfigKeys.Agent.USER_SENSITIVE_FILTER_ENABLED));
        chatConfig.put("userSensitiveWords", config.get(ConfigKeys.Agent.USER_SENSITIVE_WORDS));
        chatConfig.put("sensitiveFilterEnabled", config.get(ConfigKeys.Agent.SENSITIVE_FILTER_ENABLED));
        chatConfig.put("sensitiveFilterStrategy", config.get(ConfigKeys.Agent.SENSITIVE_FILTER_STRATEGY));
        chatConfig.put("sensitiveFilterReplaceText", config.get(ConfigKeys.Agent.SENSITIVE_FILTER_REPLACE_TEXT));
        chatConfig.put("sensitiveWords", config.get(ConfigKeys.Agent.SENSITIVE_WORDS));
        chatConfig.put("asyncToolCalls", config.get("asyncToolCalls"));
        chatConfig.put("maxExecutionSteps", config.get(ConfigKeys.Agent.MAX_EXECUTION_STEPS));
        chatConfig.put("modelRetryTimes", config.get(ConfigKeys.Agent.MODEL_RETRY_TIMES));
        chatConfig.put("enableFileRead", config.get(ConfigKeys.Agent.ENABLE_FILE_READ));
        chatConfig.put("promptVariables", config.get("promptVariables"));
        result.put("chatConfig", chatConfig);

        // 4. 绑定 ID（字符串，避免 JS 精度丢失）
        List<String> knowledgeIds = resolveBindingIdStrings(payload, "knowledgeIds", "knowledges");
        List<String> toolIds = resolveBindingIdStrings(payload, "toolIds", "tools");
        List<String> mcpServerIds = resolveBindingIdStrings(payload, "mcpServerIds", "mcpServers");
        List<String> subAgentIds = resolveBindingIdStrings(payload, "subAgentIds", "subagents");
        List<String> skillIds = resolveBindingIdStrings(payload, "skillIds", "skills");
        result.put("knowledgeIds", knowledgeIds);
        result.put("toolIds", toolIds);
        result.put("mcpServerIds", mcpServerIds);
        result.put("subAgentIds", subAgentIds);
        result.put("skillIds", skillIds);

        // 5. 按 ID 回查绑定实体（供版本预览展示名称）
        result.put("knowledges", resolveKnowledgeSummaries(knowledgeIds));
        result.put("tools", resolveToolSummaries(toolIds));
        result.put("mcpServers", resolveMcpServerSummaries(mcpServerIds));
        result.put("subAgents", resolveSubAgentSummaries(subAgentIds));
        result.put("skills", resolveSkillSummaries(skillIds));

        // 6. 兼容旧前端：保留 payload
        Map<String, Object> compatPayload = new HashMap<>(payload);
        compatPayload.put("knowledgeIds", knowledgeIds);
        compatPayload.put("toolIds", toolIds);
        compatPayload.put("mcpServerIds", mcpServerIds);
        compatPayload.put("subAgentIds", subAgentIds);
        compatPayload.put("skillIds", skillIds);
        result.put("payload", compatPayload);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractChatPayload(Map<String, Object> snap) {
        if (snap == null || snap.isEmpty()) {
            return new HashMap<>();
        }
        if (KIND_CHAT.equals(snap.get("kind")) && snap.get("payload") instanceof Map<?, ?> outer) {
            Map<String, Object> payload = new HashMap<>();
            outer.forEach((k, v) -> payload.put(String.valueOf(k), v));
            // 兼容 initDraft 时误嵌套的双层 payload
            if (KIND_CHAT.equals(payload.get("kind")) && payload.get("payload") instanceof Map<?, ?> inner) {
                Map<String, Object> nested = new HashMap<>();
                inner.forEach((k, v) -> nested.put(String.valueOf(k), v));
                return nested;
            }
            return payload;
        }
        if (snap.containsKey("systemPrompt") || snap.containsKey("config")
                || snap.containsKey("knowledgeIds") || snap.containsKey("knowledges")) {
            return snap;
        }
        return new HashMap<>();
    }

    private List<String> resolveBindingIdStrings(Map<String, Object> payload, String idField, String configField) {
        List<String> ids = toStringIdList(payload.get(idField));
        if (!ids.isEmpty()) {
            return ids;
        }
        return toStringIdList(payload.get(configField));
    }

    private List<Map<String, Object>> resolveKnowledgeSummaries(List<String> ids) {
        List<Long> longIds = toLongListFromStrings(ids);
        if (longIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Knowledge> byId = knowledgeService.listByIds(longIds).stream()
                .collect(Collectors.toMap(Knowledge::getId, Function.identity(), (a, b) -> a));
        return buildOrderedSummaries(ids, longIds, byId, k -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", String.valueOf(k.getId()));
            m.put("name", k.getName());
            m.put("description", k.getDescription());
            return m;
        });
    }

    private List<Map<String, Object>> resolveToolSummaries(List<String> ids) {
        List<Long> longIds = toLongListFromStrings(ids);
        if (longIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Tool> byId = toolService.listByIds(longIds).stream()
                .collect(Collectors.toMap(Tool::getId, Function.identity(), (a, b) -> a));
        return buildOrderedSummaries(ids, longIds, byId, t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", String.valueOf(t.getId()));
            m.put("name", t.getName());
            m.put("displayName", t.getDisplayName());
            m.put("description", t.getDescription());
            if (t.getToolType() != null) {
                m.put("toolType", t.getToolType().getCode());
            }
            return m;
        });
    }

    private List<Map<String, Object>> resolveMcpServerSummaries(List<String> ids) {
        List<Long> longIds = toLongListFromStrings(ids);
        if (longIds.isEmpty()) {
            return List.of();
        }
        Map<Long, McpServer> byId = mcpServerService.listByIds(longIds).stream()
                .collect(Collectors.toMap(McpServer::getId, Function.identity(), (a, b) -> a));
        return buildOrderedSummaries(ids, longIds, byId, s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", String.valueOf(s.getId()));
            m.put("name", s.getName());
            m.put("description", s.getDescription());
            return m;
        });
    }

    private List<Map<String, Object>> resolveSkillSummaries(List<String> ids) {
        List<Long> longIds = toLongListFromStrings(ids);
        if (longIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Skill> byId = skillService.listByIds(longIds).stream()
                .collect(Collectors.toMap(Skill::getId, Function.identity(), (a, b) -> a));
        return buildOrderedSummaries(ids, longIds, byId, s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", String.valueOf(s.getId()));
            m.put("slug", s.getSlug());
            m.put("name", s.getName());
            m.put("displayName", s.getDisplayName());
            m.put("description", s.getDescription());
            m.put("isBuiltin", s.getIsBuiltin());
            return m;
        });
    }

    private List<Map<String, Object>> resolveSubAgentSummaries(List<String> ids) {
        List<Long> longIds = toLongListFromStrings(ids);
        if (longIds.isEmpty()) {
            return List.of();
        }
        Map<Long, SubAgent> byId = subAgentService.listByIds(longIds).stream()
                .collect(Collectors.toMap(SubAgent::getId, Function.identity(), (a, b) -> a));
        return buildOrderedSummaries(ids, longIds, byId, s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", String.valueOf(s.getId()));
            m.put("name", s.getName());
            m.put("displayName", s.getDisplayName());
            m.put("description", s.getDescription());
            return m;
        });
    }

    private <T> List<Map<String, Object>> buildOrderedSummaries(
            List<String> idStrs, List<Long> longIds, Map<Long, T> byId, Function<T, Map<String, Object>> mapper) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < longIds.size(); i++) {
            T entity = byId.get(longIds.get(i));
            if (entity != null) {
                list.add(mapper.apply(entity));
            } else if (i < idStrs.size()) {
                Map<String, Object> missing = new LinkedHashMap<>();
                missing.put("id", idStrs.get(i));
                missing.put("name", "（已删除 #" + idStrs.get(i) + "）");
                list.add(missing);
            }
        }
        return list;
    }

    private List<Long> toLongListFromStrings(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Long> result = new ArrayList<>();
        for (String id : ids) {
            if (id != null && !id.isBlank()) {
                result.add(Long.parseLong(id.trim()));
            }
        }
        return result;
    }

    private String stringifyId(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            return String.valueOf(n.longValue());
        }
        String text = String.valueOf(raw);
        return text.isBlank() ? null : text;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePublishedVersion(Long agentId, Integer version) {
        Agent agent = requireAgent(agentId);
        AgentVersion row = requirePublishedRow(agentId, version);

        // 当前线上版本不允许删除（避免对话运行时无法回放）
        int currentVersion = agent.getVersion() != null ? agent.getVersion() : 0;
        if (row.getVersion() != null && row.getVersion() == currentVersion) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(),
                    "当前线上版本不允许删除，请先发布新版本后再删除");
        }

        agentVersionMapper.deleteById(row.getId());
        log.info("[AgentVersion] 已删除发布版本: agentId={}, version={}", agentId, version);
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
        Map<String, Object> payload = extractChatPayload(snap);

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
        agentService.updateSkillBindings(agentId, toLongList(payload.get("skillIds")));

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
    public Map<String, Object> resolveRuntimeForChat(Agent agent, Integer configVersion) {
        requireAgent(agent.getId());
        migrateLegacyIfNeeded(agent);

        if (configVersion == null) {
            return WorkflowConfigParser.parseConfigMap(agent.getConfig(), objectMapper);
        }
        if (configVersion == 0) {
            return resolveFromVersionRow(agent, getDraftRow(agent.getId()));
        }
        if (configVersion > 0) {
            return resolveFromVersionRow(agent, requirePublishedRow(agent.getId(), configVersion));
        }
        throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "无效的配置版本: " + configVersion);
    }

    @Override
    public WorkflowDefinition loadWorkflowDefinitionForChat(Long agentId, Integer configVersion) {
        Agent agent = requireAgent(agentId);
        migrateLegacyIfNeeded(agent);
        if (configVersion == null) {
            return loadWorkflowDefinition(agentId, false);
        }
        if (configVersion == 0) {
            return loadWorkflowDefinition(agentId, true);
        }
        if (configVersion > 0) {
            AgentVersion row = requirePublishedRow(agentId, configVersion);
            Map<String, Object> graph = extractWorkflowGraph(row);
            return WorkflowConfigParser.toDefinition(graph, objectMapper);
        }
        throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "无效的配置版本: " + configVersion);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveFromVersionRow(Agent agent, AgentVersion row) {
        if (row == null || row.getConfig() == null || row.getConfig().isBlank()) {
            return WorkflowConfigParser.parseConfigMap(agent.getConfig(), objectMapper);
        }
        Map<String, Object> snap = parseJsonMap(row.getConfig());
        if (KIND_CHAT.equals(snap.get("kind"))) {
            Map<String, Object> payload = snap.get("payload") instanceof Map
                    ? (Map<String, Object>) snap.get("payload") : Map.of();
            applyChatPayloadToAgent(agent, payload);
            Object cfg = payload.get("config");
            if (cfg instanceof Map<?, ?> cfgMap) {
                return new HashMap<>((Map<String, Object>) cfgMap);
            }
            return WorkflowConfigParser.parseConfigMap(agent.getConfig(), objectMapper);
        }
        return WorkflowConfigParser.parseConfigMap(agent.getConfig(), objectMapper);
    }

    @SuppressWarnings("unchecked")
    private void applyChatPayloadToAgent(Agent agent, Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return;
        }
        if (payload.get("systemPrompt") instanceof String sp && !sp.isBlank()) {
            agent.setSystemPrompt(sp);
        }
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
            Map<String, Object> chatSnap = buildChatSnapshot(agent);
            snapshot.put("kind", KIND_CHAT);
            snapshot.put("payload", chatSnap.get("payload"));
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
    public void initDraftWithWorkflow(Agent agent, Map<String, Object> workflowSnapshot) {
        if (agent.getId() == null) {
            return;
        }
        AgentVersion existing = getDraftRow(agent.getId());
        if (existing != null) {
            return;
        }
        AgentVersion draft = new AgentVersion();
        draft.setAgentId(agent.getId());
        draft.setUserId(agent.getUserId());
        draft.setVersion(0);
        draft.setStatus(AgentVersionStatus.DRAFT);
        draft.setConfig(writeJson(workflowSnapshot));
        // 统计节点和边数量
        Object graph = workflowSnapshot.get("graph");
        if (graph instanceof Map<?, ?> g) {
            Object nodes = g.get("nodes");
            Object edges = g.get("edges");
            if (nodes instanceof List<?> nl) {
                draft.setNodeCount(nl.size());
            }
            if (edges instanceof List<?> el) {
                draft.setEdgeCount(el.size());
            }
        }
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
        payload.put("config", WorkflowConfigParser.parseConfigMap(agent.getConfig(), objectMapper));
        // ID转为字符串存储，避免JavaScript精度丢失
        payload.put("knowledgeIds", readBindingIdsAsStrings(agent, "knowledges"));
        payload.put("toolIds", readBindingIdsAsStrings(agent, "tools"));
        payload.put("mcpServerIds", readBindingIdsAsStrings(agent, "mcpServers"));
        payload.put("subAgentIds", readBindingIdsAsStrings(agent, "subagents"));
        payload.put("skillIds", readBindingIdsAsStrings(agent, "skills"));

        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("kind", KIND_CHAT);
        snapshot.put("payload", payload);
        return snapshot;
    }

    /**
     * 读取绑定ID列表并转为字符串列表（避免前端精度丢失）
     */
    private List<String> readBindingIdsAsStrings(Agent agent, String field) {
        List<Long> ids = readBindingIds(agent, field);
        return ids.stream().map(String::valueOf).toList();
    }

    /**
     * 将任意ID列表（可能是Number或String）统一转为字符串列表
     * 用于版本详情返回时避免JavaScript精度丢失
     */
    private List<String> toStringIdList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (item == null) continue;
            if (item instanceof Number n) {
                result.add(String.valueOf(n.longValue()));
            } else {
                String text = String.valueOf(item);
                if (!text.isBlank()) {
                    result.add(text);
                }
            }
        }
        return result;
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
        return description.trim();
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
            List<Long> ids = new ArrayList<>();
            for (var node : configNode.get(field)) {
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
        } catch (Exception e) {
            log.warn("[AgentVersion] 解析 config.{} 失败: agentId={}, error={}", field, agent.getId(), e.getMessage());
            return List.of();
        }
    }
}
