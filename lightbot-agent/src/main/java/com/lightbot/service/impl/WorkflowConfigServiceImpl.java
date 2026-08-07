package com.lightbot.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.BizException;
import com.lightbot.dto.WorkflowGraphDTO;
import com.lightbot.dto.DifyWorkflowExportPreviewVO;
import com.lightbot.dto.DifyWorkflowExportResult;
import com.lightbot.dto.DifyWorkflowImportPreviewVO;
import com.lightbot.dto.WorkflowNodeTestDTO;
import com.lightbot.dto.WorkflowAbandonDTO;
import com.lightbot.dto.WorkflowResumeDTO;
import com.lightbot.dto.WorkflowTestDTO;
import com.lightbot.vo.WorkflowTestResultVO;
import com.lightbot.vo.WorkflowTestRunDetailVO;
import com.lightbot.vo.WorkflowTestRunVO;
import com.lightbot.vo.WorkflowVersionVO;
import com.lightbot.entity.Agent;
import com.lightbot.entity.Message;
import com.lightbot.entity.SubAgent;
import com.lightbot.enums.ErrorCode;
import com.lightbot.enums.NodeType;
import com.lightbot.service.AgentService;
import com.lightbot.service.AgentVersionService;
import com.lightbot.service.MessageService;
import com.lightbot.service.SubAgentService;
import com.lightbot.service.WorkflowConfigService;
import com.lightbot.service.WorkflowTestRunService;
import com.lightbot.service.workflow.WorkflowTestSseHelper;
import com.lightbot.workflow.WorkflowConfigParser;
import com.lightbot.workflow.WorkflowDefinition;
import com.lightbot.util.WorkflowRunStateUtil;
import com.lightbot.workflow.WorkflowExecutorService;
import com.lightbot.workflow.WorkflowGraphValidateUtil;
import com.lightbot.workflow.WorkflowSuspendedRun;
import com.lightbot.workflow.WorkflowTraceRecorder;
import com.lightbot.workflow.dify.DifyWorkflowExporter;
import com.lightbot.workflow.dify.DifyWorkflowImporter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.scheduler.Schedulers;

import cn.dev33.satoken.stp.StpUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 工作流配置：委托 AgentVersionService，版本数据存 agent_version 表
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowConfigServiceImpl implements WorkflowConfigService {

    private final AgentService agentService;
    private final AgentVersionService agentVersionService;
    private final SubAgentService subAgentService;
    private final ObjectMapper objectMapper;
    private final WorkflowExecutorService workflowExecutorService;
    private final WorkflowTestRunService workflowTestRunService;
    private final WorkflowTestSseHelper workflowTestSseHelper;
    private final MessageService messageService;
    private final WorkflowRunStateUtil workflowRunStateUtil;
    private final WorkflowTraceRecorder workflowTraceRecorder;
    private final DifyWorkflowImporter difyWorkflowImporter;
    private final DifyWorkflowExporter difyWorkflowExporter;

    @Override
    public Map<String, Object> getWorkflowConfig(Long agentId) {
        return agentVersionService.getWorkflowEditorState(agentId);
    }

    @Override
    public void saveDraft(Long agentId, WorkflowGraphDTO graph) {
        assertGraphPayload(graph);
        agentVersionService.saveWorkflowDraft(agentId, graph);
    }

    @Override
    public DifyWorkflowImportPreviewVO previewDifyImport(Long agentId, String yamlContent) {
        requireWorkflowAgent(agentId);
        return difyWorkflowImporter.preview(yamlContent);
    }

    @Override
    public DifyWorkflowImportPreviewVO importDifyWorkflow(Long agentId, String yamlContent) {
        requireWorkflowAgent(agentId);
        DifyWorkflowImportPreviewVO preview = difyWorkflowImporter.preview(yamlContent);
        if (hasBlocker(preview.getIssues()) || preview.getGraph() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "Dify 工作流预检未通过，请先修复阻断问题");
        }
        assertGraphPayload(preview.getGraph());
        // 仅覆盖当前草稿，已发布版本仍由 agent_version 保留。
        agentVersionService.saveWorkflowDraft(agentId, preview.getGraph());
        return preview;
    }

    @Override
    public DifyWorkflowExportPreviewVO previewDifyExport(Long agentId) {
        WorkflowGraphDTO draft = getWorkflowDraft(agentId);
        return difyWorkflowExporter.preview(draft);
    }

    @Override
    public DifyWorkflowExportResult exportDifyWorkflow(Long agentId) {
        Agent agent = requireWorkflowAgent(agentId);
        DifyWorkflowExportResult result = difyWorkflowExporter.export(getWorkflowDraft(agentId), agent.getName());
        if (result.getContent() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "当前工作流不能导出为 Dify YAML，请先修复预检问题");
        }
        return result;
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
        return validateGraph(agentId, graph);
    }

    @Override
    public Map<String, Object> getIoSchema(Long agentId) {
        Agent agent = requireAgent(agentId);
        if (agent.getAgentType() != com.lightbot.enums.AgentType.WORKFLOW) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "目标 Agent 不是工作流类型");
        }
        Integer version = agent.getVersion();
        if (version == null || version <= 0) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "工作流尚未发布");
        }
        WorkflowDefinition definition = agentVersionService.loadWorkflowDefinition(agentId, false);
        if (definition == null || definition.getNodes() == null || definition.getNodes().isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "工作流定义为空");
        }
        Map<String, Object> schema = com.lightbot.workflow.WorkflowIoSchemaUtil.buildSchema(definition);
        schema.put("agentId", String.valueOf(agentId));
        schema.put("agentName", agent.getName());
        schema.put("publishedVersion", version);
        return schema;
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
    public WorkflowTestResultVO testRun(Long agentId, WorkflowTestDTO request) {
        TestRunContext ctx = prepareTestRun(agentId, request);
        long startMs = System.currentTimeMillis();
        try {
            WorkflowTestResultVO result = workflowExecutorService.executeForTest(
                    ctx.agent(), ctx.definition(), request.getInput(), ctx.events(),
                    ctx.initialVariables(), ctx.runId());
            finalizeTestResult(result, ctx);
            workflowTestRunService.finishRun(ctx.runId(), result, System.currentTimeMillis() - startMs, null);
            return result;
        } catch (Exception e) {
            workflowTestRunService.finishRun(ctx.runId(), null, System.currentTimeMillis() - startMs, e.getMessage());
            throw e;
        }
    }

    @Override
    public SseEmitter testRunStream(Long agentId, WorkflowTestDTO request) {
        TestRunContext ctx = prepareTestRun(agentId, request);
        SseEmitter emitter = workflowTestSseHelper.createEmitter();
        AtomicInteger counter = new AtomicInteger(0);
        Consumer<Map<String, Object>> onEvent = workflowTestSseHelper.eventSender(emitter, counter);
        long startMs = System.currentTimeMillis();

        Schedulers.boundedElastic().schedule(() -> {
            try {
                WorkflowTestResultVO result = workflowExecutorService.executeForTest(
                        ctx.agent(), ctx.definition(), request.getInput(), ctx.events(),
                        ctx.initialVariables(), ctx.runId(), onEvent);
                finalizeTestResult(result, ctx);
                workflowTestRunService.finishRun(ctx.runId(), result, System.currentTimeMillis() - startMs, null);
                workflowTestSseHelper.sendDone(emitter, result);
            } catch (Exception e) {
                workflowTestRunService.finishRun(ctx.runId(), null, System.currentTimeMillis() - startMs, e.getMessage());
                workflowTestSseHelper.sendErrorAndComplete(emitter, counter, e.getMessage());
            }
        });
        return emitter;
    }

    /**
     * 准备调试运行上下文（定义加载、runId、初始变量）
     */
    private TestRunContext prepareTestRun(Long agentId, WorkflowTestDTO request) {
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
        long userId = StpUtil.getLoginIdAsLong();
        String runId = workflowTestRunService.startRun(agentId, userId, request, definition, usedDraft);
        return new TestRunContext(agent, definition, initialVariables, usedDraft, runId, new ArrayList<>());
    }

    private void finalizeTestResult(WorkflowTestResultVO result, TestRunContext ctx) {
        result.setUsedDraft(ctx.usedDraft());
        result.setRunId(ctx.runId());
        result.setTestRunId(workflowTestRunService.findIdByRunId(ctx.runId()));
    }

    private record TestRunContext(
            Agent agent,
            WorkflowDefinition definition,
            Map<String, Object> initialVariables,
            boolean usedDraft,
            String runId,
            List<Map<String, Object>> events) {
    }

    @Override
    public WorkflowTestResultVO resumeWorkflow(Long agentId, WorkflowResumeDTO request) {
        requireAgent(agentId);
        Map<String, Object> formData = request.getFormData() != null ? request.getFormData() : Map.of();
        long startMs = System.currentTimeMillis();
        WorkflowTestResultVO result = workflowExecutorService.resumeAfterConfirm(agentId, request.getRunId(), formData);
        workflowTestRunService.updateAfterResume(
                request.getRunId(), result, System.currentTimeMillis() - startMs, null);
        if (result.getRunId() == null) {
            result.setRunId(request.getRunId());
        }
        result.setTestRunId(workflowTestRunService.findIdByRunId(request.getRunId()));
        persistChatMessageAfterResume(agentId, request.getMessageId(), result);
        workflowTraceRecorder.refreshAfterChatResume(agentId, request.getMessageId(), result);
        return result;
    }

    @Override
    public SseEmitter resumeWorkflowStream(Long agentId, WorkflowResumeDTO request) {
        requireAgent(agentId);
        Map<String, Object> formData = request.getFormData() != null ? request.getFormData() : Map.of();
        SseEmitter emitter = workflowTestSseHelper.createEmitter();
        AtomicInteger counter = new AtomicInteger(0);
        Consumer<Map<String, Object>> onEvent = workflowTestSseHelper.eventSender(emitter, counter);
        long startMs = System.currentTimeMillis();

        Schedulers.boundedElastic().schedule(() -> {
            try {
                WorkflowTestResultVO result = workflowExecutorService.resumeAfterConfirm(
                        agentId, request.getRunId(), formData, onEvent);
                workflowTestRunService.updateAfterResume(
                        request.getRunId(), result, System.currentTimeMillis() - startMs, null);
                if (result.getRunId() == null) {
                    result.setRunId(request.getRunId());
                }
                result.setTestRunId(workflowTestRunService.findIdByRunId(request.getRunId()));
                persistChatMessageAfterResume(agentId, request.getMessageId(), result);
                workflowTraceRecorder.refreshAfterChatResume(agentId, request.getMessageId(), result);
                workflowTestSseHelper.sendDone(emitter, result);
            } catch (Exception e) {
                workflowTestRunService.updateAfterResume(
                        request.getRunId(), null, System.currentTimeMillis() - startMs, e.getMessage());
                workflowTestSseHelper.sendErrorAndComplete(emitter, counter, e.getMessage());
            }
        });
        return emitter;
    }

    @Override
    public void abandonWorkflowConfirm(Long agentId, WorkflowAbandonDTO request) {
        requireAgent(agentId);
        String runId = request.getRunId();
        WorkflowSuspendedRun suspended = workflowRunStateUtil.getSuspended(runId);
        if (suspended != null && !agentId.equals(suspended.getAgentId())) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "运行实例与 Agent 不匹配");
        }
        workflowRunStateUtil.deleteSuspended(runId);
        List<Map<String, Object>> patchedEvents = persistChatMessageAfterAbandon(agentId, request.getMessageId(), suspended);
        if (patchedEvents != null) {
            workflowTraceRecorder.refreshAfterChatAbandon(agentId, request.getMessageId(), patchedEvents);
        }
        log.info("[WorkflowConfigService] 用户放弃人工确认: agentId={}, runId={}, messageId={}",
                agentId, runId, request.getMessageId());
    }

    /**
     * Chat 场景：放弃确认后回写助手消息，避免刷新后仍显示待确认表单
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> persistChatMessageAfterAbandon(Long agentId, Long messageId, WorkflowSuspendedRun suspended) {
        if (messageId == null) {
            return null;
        }
        Message msg = messageService.getById(messageId);
        if (msg == null) {
            return null;
        }
        try {
            Map<String, Object> meta = new LinkedHashMap<>();
            if (msg.getMetadata() != null && !msg.getMetadata().isBlank()) {
                meta.putAll(objectMapper.readValue(msg.getMetadata(), Map.class));
            }
            List<Map<String, Object>> events = patchConfirmEventsOnAbandon(
                    extractWorkflowEvents(meta, suspended),
                    suspended != null ? suspended.getSuspendNodeId() : findSuspendNodeIdFromEvents(meta));
            if (events != null) {
                meta.put("workflowEvents", events);
            }
            meta.put("workflowSuspended", false);
            meta.put("workflowConfirmResolved", true);
            meta.put("workflowAbandoned", true);
            meta.remove("workflowConfirmForm");
            meta.remove("workflowRunId");
            msg.setMetadata(objectMapper.writeValueAsString(meta));

            String notice = "工作流已终止，用户放弃人工确认";
            String existing = msg.getContent() != null ? msg.getContent().trim() : "";
            if (existing.isEmpty()) {
                msg.setContent(notice);
            } else if (!existing.contains(notice)) {
                msg.setContent(existing + "\n\n" + notice);
            }
            messageService.updateById(msg);
            return events;
        } catch (Exception e) {
            log.warn("[WorkflowConfigService] 放弃确认回写 Chat 消息失败: messageId={}, error={}",
                    messageId, e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractWorkflowEvents(Map<String, Object> meta, WorkflowSuspendedRun suspended) {
        if (suspended != null && suspended.getWorkflowEvents() != null && !suspended.getWorkflowEvents().isEmpty()) {
            return new ArrayList<>(suspended.getWorkflowEvents());
        }
        Object fromMeta = meta.get("workflowEvents");
        if (fromMeta instanceof List<?> list) {
            List<Map<String, Object>> copied = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    copied.add(new LinkedHashMap<>((Map<String, Object>) map));
                }
            }
            return copied.isEmpty() ? null : copied;
        }
        return null;
    }

    private String findSuspendNodeIdFromEvents(Map<String, Object> meta) {
        Object eventsObj = meta.get("workflowEvents");
        if (!(eventsObj instanceof List<?> events)) {
            return null;
        }
        for (int i = events.size() - 1; i >= 0; i--) {
            Object item = events.get(i);
            if (!(item instanceof Map<?, ?> ev)) {
                continue;
            }
            if ("workflow_confirm_required".equals(ev.get("type"))) {
                Object nodeId = ev.get("nodeId");
                return nodeId != null ? nodeId.toString() : null;
            }
        }
        return null;
    }

    private List<Map<String, Object>> patchConfirmEventsOnAbandon(List<Map<String, Object>> events, String suspendNodeId) {
        if (events == null || suspendNodeId == null) {
            return events;
        }
        for (int i = events.size() - 1; i >= 0; i--) {
            Map<String, Object> e = events.get(i);
            if (!"workflow_node_complete".equals(e.get("type"))) {
                continue;
            }
            if (!suspendNodeId.equals(String.valueOf(e.get("nodeId")))) {
                continue;
            }
            if (!Boolean.TRUE.equals(e.get("suspended"))) {
                continue;
            }
            e.put("suspended", false);
            e.put("success", false);
            e.put("message", "用户已放弃");
            break;
        }
        for (Map<String, Object> e : events) {
            if (!"workflow_confirm_required".equals(e.get("type"))) {
                continue;
            }
            if (!suspendNodeId.equals(String.valueOf(e.get("nodeId")))) {
                continue;
            }
            e.put("resolved", true);
            e.put("abandoned", true);
        }
        Map<String, Object> abandonedEvent = new LinkedHashMap<>();
        abandonedEvent.put("type", "workflow_abandoned");
        abandonedEvent.put("message", "用户放弃人工确认，工作流已终止");
        events.add(abandonedEvent);
        return events;
    }

    /**
     * Chat 场景：恢复成功后回写助手消息 content/metadata，避免刷新后仍显示待确认
     */
    @SuppressWarnings("unchecked")
    private void persistChatMessageAfterResume(Long agentId, Long messageId, WorkflowTestResultVO result) {
        if (messageId == null || result == null) {
            return;
        }
        Message msg = messageService.getById(messageId);
        if (msg == null) {
            return;
        }
        try {
            Map<String, Object> meta = new LinkedHashMap<>();
            if (msg.getMetadata() != null && !msg.getMetadata().isBlank()) {
                meta.putAll(objectMapper.readValue(msg.getMetadata(), Map.class));
            }
            if (result.getNodeEvents() != null) {
                meta.put("workflowEvents", result.getNodeEvents());
            }
            if (Boolean.TRUE.equals(result.getSuspended())) {
                meta.put("workflowSuspended", true);
                meta.put("workflowRunId", result.getRunId());
                meta.put("workflowConfirmForm", result.getConfirmForm());
                meta.remove("workflowConfirmResolved");
            } else {
                meta.put("workflowSuspended", false);
                meta.put("workflowConfirmResolved", true);
                meta.remove("workflowConfirmForm");
                meta.remove("workflowRunId");
            }
            msg.setMetadata(objectMapper.writeValueAsString(meta));
            if (result.getOutput() != null && !result.getOutput().isBlank()) {
                if (Boolean.TRUE.equals(result.getSuspended())) {
                    String existing = msg.getContent() != null ? msg.getContent() : "";
                    if (existing.isBlank()) {
                        msg.setContent(result.getOutput());
                    }
                } else {
                    // 人工确认恢复完成后：用最终 output 替换挂起前的中间态正文
                    msg.setContent(result.getOutput());
                }
            }
            messageService.updateById(msg);
        } catch (Exception e) {
            log.warn("[WorkflowConfigService] 回写 Chat 消息失败: messageId={}, error={}", messageId, e.getMessage());
        }
    }

    @Override
    public List<WorkflowTestRunVO> listTestRuns(Long agentId) {
        requireAgent(agentId);
        return workflowTestRunService.listByAgent(agentId, 50);
    }

    @Override
    public WorkflowTestRunDetailVO getTestRun(Long agentId, String runId) {
        requireAgent(agentId);
        return workflowTestRunService.getDetail(agentId, runId);
    }

    @Override
    public void deleteTestRun(Long agentId, String runId) {
        requireAgent(agentId);
        workflowTestRunService.deleteRun(agentId, runId);
    }

    @Override
    public void clearTestRuns(Long agentId) {
        requireAgent(agentId);
        workflowTestRunService.clearByAgent(agentId);
    }

    @Override
    public WorkflowTestResultVO testNode(Long agentId, WorkflowNodeTestDTO request) {
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
    private Map<String, Object> buildTestInitialVariables(WorkflowTestDTO request) {
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

    private Agent requireWorkflowAgent(Long agentId) {
        Agent agent = requireAgent(agentId);
        if (agent.getAgentType() != com.lightbot.enums.AgentType.WORKFLOW) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "目标 Agent 不是工作流类型");
        }
        return agent;
    }

    private WorkflowGraphDTO getWorkflowDraft(Long agentId) {
        requireWorkflowAgent(agentId);
        Object draft = agentVersionService.getWorkflowEditorState(agentId).get("draft");
        if (!(draft instanceof Map<?, ?>)) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "工作流草稿为空，请先配置节点");
        }
        WorkflowGraphDTO graph = objectMapper.convertValue(draft, WorkflowGraphDTO.class);
        if (graph.getNodes() == null || graph.getNodes().isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "工作流草稿为空，请先配置节点");
        }
        if (graph.getEdges() == null) {
            graph.setEdges(List.of());
        }
        return graph;
    }

    private boolean hasBlocker(List<com.lightbot.dto.DifyWorkflowIssueVO> issues) {
        return issues != null && issues.stream().anyMatch(issue -> "BLOCKER".equals(issue.getSeverity()));
    }

    /** 校验所有可落库图载荷的基础结构与关键文本上限。 */
    private void assertGraphPayload(WorkflowGraphDTO graph) {
        List<String> errors = WorkflowGraphValidateUtil.validatePayload(
                graph == null ? null : graph.getNodes(), graph == null ? null : graph.getEdges());
        if (!errors.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), String.join("；", errors));
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
    private List<String> validateGraph(Long agentId, WorkflowGraphDTO graph) {
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
            if ("app_component".equals(type)) {
                Long targetId = com.lightbot.workflow.WorkflowNodeDataUtils.parseLongId(data.get("componentCode"));
                if (targetId == null) {
                    errors.add("应用组件节点未选择子工作流: " + id);
                } else if (targetId.equals(agentId)) {
                    errors.add("应用组件不能引用自身: " + id);
                } else {
                    String cycle = detectSubWorkflowCycle(agentId, targetId, new java.util.HashSet<>());
                    if (cycle != null) {
                        errors.add("应用组件存在循环引用: " + cycle);
                    }
                    Agent target = agentService.getById(targetId);
                    if (target == null || target.getAgentType() != com.lightbot.enums.AgentType.WORKFLOW) {
                        errors.add("应用组件目标不是有效的工作流 Agent: " + id);
                    } else if (target.getVersion() == null || target.getVersion() <= 0) {
                        errors.add("应用组件引用的子工作流尚未发布: " + id);
                    }
                }
            }
            if ("sub_agent".equals(type)) {
                Long subAgentId = com.lightbot.workflow.WorkflowNodeDataUtils.parseLongId(data.get("subAgentId"));
                if (subAgentId == null) {
                    errors.add("SubAgent 节点未选择子智能体: " + id);
                } else {
                    SubAgent subAgent = subAgentService.getById(subAgentId);
                    if (subAgent == null) {
                        errors.add("SubAgent 不存在: " + id);
                    } else if (!Integer.valueOf(1).equals(subAgent.getEnabled())) {
                        errors.add("SubAgent 未启用: " + id);
                    }
                }
                Object task = data.get("task");
                if (task == null || String.valueOf(task).isBlank()) {
                    errors.add("SubAgent 节点未配置任务描述: " + id);
                }
            }
            // 条件分支节点必须有未命中兜底边（{nodeId}_default）
            if ("condition".equals(type)) {
                String defaultHandle = id + "_default";
                boolean hasDefaultEdge = edges.stream().anyMatch(e ->
                        id.equals(String.valueOf(e.get("source")))
                                && defaultHandle.equals(String.valueOf(e.get("sourceHandle"))));
                if (!hasDefaultEdge) {
                    errors.add("条件分支节点缺少「都未命中」默认路径: " + id);
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

        errors.addAll(WorkflowGraphValidateUtil.validateMultiOutgoingEdges(nodes, edges));

        return errors;
    }

    /**
     * 检测子工作流 transitive 循环引用
     */
    private String detectSubWorkflowCycle(Long rootAgentId, Long currentTargetId, java.util.Set<Long> visiting) {
        if (currentTargetId.equals(rootAgentId)) {
            return "Agent " + rootAgentId + " → " + currentTargetId;
        }
        if (!visiting.add(currentTargetId)) {
            return null;
        }
        WorkflowDefinition definition = agentVersionService.loadWorkflowDefinition(currentTargetId, false);
        if (definition == null || definition.getNodes() == null) {
            visiting.remove(currentTargetId);
            return null;
        }
        for (com.lightbot.workflow.WorkflowNode node : definition.getNodes()) {
            if (node.getType() != com.lightbot.enums.NodeType.APP_COMPONENT || node.getData() == null) {
                continue;
            }
            Long nestedId = com.lightbot.workflow.WorkflowNodeDataUtils.parseLongId(
                    node.getData().get("componentCode"));
            if (nestedId == null) {
                continue;
            }
            if (nestedId.equals(rootAgentId)) {
                visiting.remove(currentTargetId);
                return "Agent " + rootAgentId + " → ... → " + nestedId;
            }
            String nested = detectSubWorkflowCycle(rootAgentId, nestedId, visiting);
            if (nested != null) {
                visiting.remove(currentTargetId);
                return "Agent " + currentTargetId + " → " + nested;
            }
        }
        visiting.remove(currentTargetId);
        return null;
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
