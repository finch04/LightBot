package com.lightbot.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.vo.WorkflowTestResultVO;
import com.lightbot.entity.Agent;
import com.lightbot.entity.Message;
import com.lightbot.enums.AgentType;
import com.lightbot.enums.NodeType;
import com.lightbot.service.AgentService;
import com.lightbot.service.AgentVersionService;
import com.lightbot.service.MessageService;
import com.lightbot.util.WorkflowRunStateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 工作流执行服务
 * <p>DAG 执行引擎，从 START 节点开始，按边连接顺序执行各节点</p>
 *
 * @author finch
 * @since 2026-05-24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowExecutorService {

    private final NodeProcessorRegistry registry;
    private final WorkflowNodeRunner nodeRunner;
    private final AgentService agentService;
    private final AgentVersionService agentVersionService;
    private final MessageService messageService;
    private final ObjectMapper objectMapper;
    private final WorkflowRunStateUtil workflowRunStateUtil;
    private static final int MAX_SUB_WORKFLOW_DEPTH = 5;
    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{([^}]+)}}");

    /**
     * 执行工作流
     *
     * @param agentId   Agent ID
     * @param sessionId 会话 ID
     * @param userInput 用户输入
     * @return 执行结果（最终输出）
     */
    public String execute(Long agentId, Long sessionId, String userInput, List<Map<String, Object>> workflowEvents) {
        return execute(agentId, sessionId, userInput, workflowEvents, null);
    }

    /**
     * 执行工作流，支持实时推送节点事件（onEvent 非空时每个节点开始/结束立即回调）
     */
    public String execute(Long agentId, Long sessionId, String userInput,
                          List<Map<String, Object>> workflowEvents,
                          Consumer<Map<String, Object>> onEvent) {
        Agent agent = agentService.getById(agentId);
        if (agent == null) {
            throw new IllegalArgumentException("Agent不存在: " + agentId);
        }

        WorkflowDefinition workflow = agentVersionService.loadWorkflowDefinition(agentId, false);
        if (workflow == null || workflow.getNodes() == null || workflow.getNodes().isEmpty()) {
            log.warn("[WorkflowExecutorService] 工作流未发布或为空: agentId={}", agentId);
            return "工作流尚未发布或为空，请先在编排页发布工作流";
        }

        return executeWithDefinition(agent, workflow, sessionId, userInput, workflowEvents, onEvent);
    }

    /**
     * 使用指定工作流定义执行（调试/测试）
     */
    public String executeWithDefinition(Agent agent, WorkflowDefinition workflow, Long sessionId,
                                        String userInput, List<Map<String, Object>> workflowEvents) {
        return executeWithDefinition(agent, workflow, sessionId, userInput, workflowEvents, null);
    }

    public String executeWithDefinition(Agent agent, WorkflowDefinition workflow, Long sessionId,
                                        String userInput, List<Map<String, Object>> workflowEvents,
                                        Consumer<Map<String, Object>> onEvent) {
        return executeWithDefinition(agent, workflow, sessionId, userInput, workflowEvents, onEvent, null);
    }

    public String executeWithDefinition(Agent agent, WorkflowDefinition workflow, Long sessionId,
                                        String userInput, List<Map<String, Object>> workflowEvents,
                                        Consumer<Map<String, Object>> onEvent,
                                        Map<String, Object> initialVariables) {
        return executeWithDefinition(agent, workflow, sessionId, userInput, workflowEvents, onEvent, initialVariables, null);
    }

    public String executeWithDefinition(Agent agent, WorkflowDefinition workflow, Long sessionId,
                                        String userInput, List<Map<String, Object>> workflowEvents,
                                        Consumer<Map<String, Object>> onEvent,
                                        Map<String, Object> initialVariables,
                                        Consumer<String> onStreamChunk) {
        Long agentId = agent.getId();
        if (workflow == null || workflow.getNodes() == null || workflow.getNodes().isEmpty()) {
            return "工作流为空";
        }

        // 1. 构建执行上下文
        NodeExecutionContext context = NodeExecutionContext.builder()
                .agentId(agentId)
                .sessionId(sessionId)
                .userInput(userInput)
                .agentConfig(agent.getConfig())
                .workflow(workflow)
                .variables(new LinkedHashMap<>())
                .scopedVariables(new LinkedHashMap<>())
                .sysVariables(new LinkedHashMap<>())
                .nodeOutputs(new LinkedHashMap<>())
                .onStreamChunk(onStreamChunk)
                .workflowEvents(workflowEvents)
                .onEvent(onEvent)
                .subWorkflowDepth(0)
                .build();
        WorkflowVariableScope.initContext(context);

        // 2. 注入全局配置中的会话变量默认值
        applyGlobalConfig(context, workflow.getGlobalConfig());

        // 2.0 注入会话历史（与对话型一致，供工作流节点使用 history_list / input）
        injectSessionHistory(context, sessionId, userInput);
        WorkflowVariableScope.syncSysBucket(context);

        // 2.1 调试运行预置变量（query / history_list 等）
        if (initialVariables != null && !initialVariables.isEmpty()) {
            context.getVariables().putAll(initialVariables);
            WorkflowVariableScope.syncSysBucket(context);
        }

        // 3. 从 START 节点开始执行 DAG
        String currentNodeId = workflow.getStartNodeId();
        if (currentNodeId == null) {
            log.warn("[WorkflowExecutorService] 未找到 START 节点: agentId={}", agentId);
            return "工作流缺少开始节点";
        }

        LoopOutcome outcome = runExecutionLoop(
                agent, workflow, context, currentNodeId, 0,
                new StringBuilder(), workflowEvents, onEvent, serializeWorkflow(workflow),
                ExecutionOptions.chat());

        if (outcome.isSuspended()) {
            return outcome.getStreamResult();
        }

        emitWorkflowEvent(workflowEvents, onEvent, Map.of("type", "workflow_complete", "contentOffset", 0));

        if (context.getVariables().containsKey("result")) {
            return WorkflowResultUtils.formatAsText(context.getVariables().get("result"));
        }
        return outcome.getStreamResult();
    }

    /**
     * 人工确认后恢复执行
     *
     * @param agentId  Agent ID
     * @param runId    挂起运行 ID
     * @param formData 确认表单数据
     * @return 调试/恢复结果
     */
    public WorkflowTestResultVO resumeAfterConfirm(Long agentId, String runId, Map<String, Object> formData) {
        return resumeAfterConfirm(agentId, runId, formData, null);
    }

    /**
     * 人工确认后恢复工作流（支持实时推送节点事件）
     *
     * @param agentId  Agent ID
     * @param runId    挂起运行 ID
     * @param formData 确认表单数据
     * @param onEvent  实时事件回调（可为 null）
     * @return 调试/恢复结果
     */
    public WorkflowTestResultVO resumeAfterConfirm(Long agentId, String runId, Map<String, Object> formData,
                                                   Consumer<Map<String, Object>> onEvent) {
        WorkflowSuspendedRun suspended = workflowRunStateUtil.getSuspended(runId);
        if (suspended == null) {
            throw new IllegalArgumentException("挂起的运行不存在或已过期: " + runId);
        }
        if (!agentId.equals(suspended.getAgentId())) {
            throw new IllegalArgumentException("运行实例与 Agent 不匹配");
        }

        Agent agent = agentService.getById(agentId);
        if (agent == null) {
            throw new IllegalArgumentException("Agent不存在: " + agentId);
        }

        WorkflowDefinition workflow = deserializeWorkflow(suspended.getWorkflowGraphJson());
        if (workflow == null) {
            throw new IllegalStateException("无法恢复工作流定义");
        }

        // 1. 合并确认表单到变量池
        NodeExecutionContext context = NodeExecutionContext.builder()
                .agentId(agentId)
                .sessionId(suspended.getSessionId())
                .userInput(suspended.getUserInput())
                .agentConfig(agent.getConfig())
                .workflow(workflow)
                .variables(new LinkedHashMap<>(suspended.getVariables()))
                .scopedVariables(new LinkedHashMap<>())
                .sysVariables(new LinkedHashMap<>())
                .nodeOutputs(new LinkedHashMap<>(suspended.getNodeOutputs()))
                .build();
        WorkflowVariableScope.initContext(context);
        WorkflowVariableScope.syncSysBucket(context);

        boolean askUserResume = isAskUserSuspend(workflow, suspended);
        Map<String, Object> submitted = filterSubmittedFormData(
                askUserResume ? WorkflowHitlPayloadBuilder.normalizeAskUserSubmittedForm(formData) : formData);

        WorkflowNode suspendNode = suspended.getSuspendNodeId() != null
                ? workflow.getNode(suspended.getSuspendNodeId()) : null;

        if (askUserResume && suspendNode != null) {
            Map<String, Object> nodeData = suspendNode.getData() != null ? suspendNode.getData() : Map.of();
            Map<String, Object> phaseOutputs = extractNodeOutputsMap(
                    context.getNodeOutputs(), suspended.getSuspendNodeId());
            Map<String, Object> combined = WorkflowHitlPayloadBuilder.mergePhaseOutputsWithAnswer(phaseOutputs, submitted);
            Map<String, Object> nodeOutputs = WorkflowMappingUtils.applyOutputMappings(
                    nodeData,
                    combined,
                    WorkflowHitlPayloadBuilder.ANSWER_FIELD_KEY,
                    combined.get(WorkflowHitlPayloadBuilder.ANSWER_FIELD_KEY));
            // outputMappings 会丢弃 toolResultText/question，需保留供 Chat 工具节点渲染
            WorkflowHitlPayloadBuilder.preserveAskUserRenderMeta(nodeOutputs, combined);
            WorkflowVariableScope.mergeNodeOutputs(context, suspendNode, nodeOutputs);
            context.getNodeOutputs().put(suspended.getSuspendNodeId(), nodeOutputs);
        } else {
            submitted.forEach((key, value) -> context.getVariables().put(key, value));
            if (suspended.getSuspendNodeId() != null) {
                context.getNodeOutputs().put(suspended.getSuspendNodeId(), submitted);
            }
        }

        workflowRunStateUtil.deleteSuspended(runId);

        List<Map<String, Object>> events = suspended.getWorkflowEvents() != null
                ? new ArrayList<>(suspended.getWorkflowEvents()) : new ArrayList<>();

        // 2. 将挂起的 confirm 节点标记为已提交，避免前端重复展示待确认态
        Map<String, Object> patchData = submitted;
        if (askUserResume && suspended.getSuspendNodeId() != null) {
            Map<String, Object> mergedForEvent = extractNodeOutputsMap(
                    context.getNodeOutputs(), suspended.getSuspendNodeId());
            if (mergedForEvent != null && !mergedForEvent.isEmpty()) {
                patchData = mergedForEvent;
            }
        }
        patchConfirmEventsOnResume(events, suspended.getSuspendNodeId(), patchData);

        LoopOutcome outcome = runExecutionLoop(
                agent, workflow, context,
                suspended.getNextNodeId(),
                suspended.getStepIndex(),
                new StringBuilder(),
                events,
                onEvent,
                suspended.getWorkflowGraphJson(),
                ExecutionOptions.fromSuspended(suspended));

        WorkflowTestResultVO.WorkflowTestResultVOBuilder builder = WorkflowTestResultVO.builder()
                .nodeEvents(events)
                .variables(new LinkedHashMap<>(context.getVariables()))
                .suspended(outcome.isSuspended());

        if (outcome.isSuspended()) {
            builder.runId(outcome.getRunId())
                    .confirmForm(outcome.getConfirmForm())
                    .output(outcome.getSuspendedMessage());
        } else {
            emitWorkflowEvent(events, onEvent, Map.of("type", "workflow_complete", "contentOffset", 0));
            String output = context.getVariables().containsKey("result")
                    ? WorkflowResultUtils.formatAsText(context.getVariables().get("result"))
                    : outcome.getStreamResult();
            builder.output(output);
        }
        return builder.build();
    }

    /**
     * 恢复执行前：将挂起中的 confirm 节点事件标记为已提交，写入用户选择
     */
    private void patchConfirmEventsOnResume(List<Map<String, Object>> events,
                                            String suspendNodeId,
                                            Map<String, Object> formData) {
        if (events == null || suspendNodeId == null) {
            return;
        }
        Map<String, Object> submitted = filterSubmittedFormData(formData);
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
            e.put("success", true);
            e.put("message", "用户已提交");
            if (!submitted.isEmpty()) {
                e.put("outputs", submitted);
            }
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
            if (!submitted.isEmpty()) {
                e.put("submittedData", submitted);
            }
        }
    }

    private Map<String, Object> filterSubmittedFormData(Map<String, Object> formData) {
        Map<String, Object> submitted = new LinkedHashMap<>();
        if (formData == null) {
            return submitted;
        }
        formData.forEach((key, value) -> {
            if (key == null || key.isBlank() || key.startsWith("_")) {
                return;
            }
            submitted.put(key, value);
        });
        return submitted;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractNodeOutputsMap(Map<String, Object> nodeOutputs, String nodeId) {
        if (nodeOutputs == null || nodeId == null) {
            return null;
        }
        Object prior = nodeOutputs.get(nodeId);
        if (!(prior instanceof Map<?, ?> priorMap)) {
            return null;
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        priorMap.forEach((k, v) -> copy.put(String.valueOf(k), v));
        return copy;
    }

    /**
     * 判断挂起恢复是否为 ask_user 工具节点（与 confirm 节点区分）
     */
    @SuppressWarnings("unchecked")
    private boolean isAskUserSuspend(WorkflowDefinition workflow, WorkflowSuspendedRun suspended) {
        if (suspended == null || suspended.getSuspendNodeId() == null) {
            return false;
        }
        if (suspended.getWorkflowEvents() != null) {
            for (int i = suspended.getWorkflowEvents().size() - 1; i >= 0; i--) {
                Map<String, Object> e = suspended.getWorkflowEvents().get(i);
                if (!"workflow_confirm_required".equals(e.get("type"))) {
                    continue;
                }
                if (!suspended.getSuspendNodeId().equals(String.valueOf(e.get("nodeId")))) {
                    continue;
                }
                Object confirmForm = e.get("confirmForm");
                if (confirmForm instanceof Map<?, ?> form) {
                    return WorkflowHitlPayloadBuilder.HITL_TYPE_ASK_USER.equals(form.get("hitlType"));
                }
            }
        }
        if (workflow != null) {
            WorkflowNode node = workflow.getNode(suspended.getSuspendNodeId());
            return node != null && node.getType() == NodeType.TOOL;
        }
        return false;
    }

    private String serializeWorkflow(WorkflowDefinition workflow) {
        try {
            return objectMapper.writeValueAsString(workflow);
        } catch (Exception e) {
            log.warn("[WorkflowExecutorService] 序列化工作流失败: {}", e.getMessage());
            return null;
        }
    }

    private WorkflowDefinition deserializeWorkflow(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, WorkflowDefinition.class);
        } catch (Exception e) {
            log.warn("[WorkflowExecutorService] 反序列化工作流失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从指定节点继续执行 DAG，遇到 confirm 节点会挂起
     */
    private LoopOutcome runExecutionLoop(Agent agent,
                                         WorkflowDefinition workflow,
                                         NodeExecutionContext context,
                                         String currentNodeId,
                                         int startStepIndex,
                                         StringBuilder result,
                                         List<Map<String, Object>> workflowEvents,
                                         Consumer<Map<String, Object>> onEvent,
                                         String workflowGraphJson,
                                         ExecutionOptions options) {
        Long agentId = agent.getId();
        int stepIndex = startStepIndex;

        while (currentNodeId != null) {
            stepIndex++;
            WorkflowNode node = workflow.getNode(currentNodeId);
            if (node == null) {
                log.warn("[WorkflowExecutorService] 节点不存在: nodeId={}", currentNodeId);
                break;
            }

            WorkflowNode eventNode = resolveEventNode(workflow, node, context);

            context.setCurrentNodeId(currentNodeId);
            context.setCurrentNodeData(node.getData());

            String nodeLabel = resolveNodeLabel(eventNode);
            String nodeTypeCode = eventNode.getType() != null ? eventNode.getType().getCode() : "";
            final String executingNodeId = currentNodeId;
            final String eventNodeId = eventNode.getId();

            long nodeStartMs = System.currentTimeMillis();
            Map<String, Object> startEvent = new LinkedHashMap<>();
            startEvent.put("type", "workflow_node_start");
            startEvent.put("nodeId", eventNodeId);
            startEvent.put("nodeType", nodeTypeCode);
            startEvent.put("nodeLabel", nodeLabel);
            startEvent.put("stepIndex", stepIndex);
            startEvent.put("startTime", nodeStartMs);
            startEvent.put("contentOffset", 0);
            Map<String, Object> inputPreview = buildNodeInputPreview(node, context);
            if (!inputPreview.isEmpty()) {
                startEvent.put("input", inputPreview);
            }
            emitWorkflowEvent(workflowEvents, onEvent, startEvent);

            boolean nodeSuccess = true;
            String completeMessage = "执行完成";
            String nextNodeId = null;
            NodeExecutionResult nodeResult = null;

            Exception nodeFailure = null;

            try {
                NodeProcessor processor = registry.getProcessor(node.getType());
                log.info("[WorkflowExecutorService] 执行节点: nodeId={}, type={}",
                        executingNodeId, node.getType());

                NodeResilienceEventContext resilienceCtx = NodeResilienceEventContext.builder()
                        .nodeId(eventNodeId)
                        .nodeLabel(nodeLabel)
                        .nodeType(nodeTypeCode)
                        .stepIndex(stepIndex)
                        .eventEmitter(e -> emitWorkflowEvent(workflowEvents, onEvent, e))
                        .build();

                nodeResult = NodeTimeoutRetryHelper.executeWithTimeoutAndRetry(
                        executingNodeId, node.getType(), node.getData(),
                        () -> processor.execute(context),
                        resilienceCtx);

                if (nodeResult.isSuspended()) {
                    // 挂起前仍 merge 第一阶段 outputs（question/options 等），便于 resume 前引用与恢复后合并
                    if (nodeResult.getOutputs() != null) {
                        context.getNodeOutputs().put(executingNodeId, nodeResult.getOutputs());
                        WorkflowVariableScope.mergeNodeOutputs(context, node, nodeResult.getOutputs());
                    }

                    String runId = options != null && options.assignedRunId != null
                            ? options.assignedRunId
                            : UUID.randomUUID().toString().replace("-", "");
                    String suspendSource = options != null && options.suspendSource != null
                            ? options.suspendSource : "chat";

                    Map<String, Object> confirmEvent = new LinkedHashMap<>();
                    confirmEvent.put("type", "workflow_confirm_required");
                    confirmEvent.put("runId", runId);
                    confirmEvent.put("nodeId", eventNodeId);
                    confirmEvent.put("nodeLabel", nodeLabel);
                    confirmEvent.put("confirmForm", nodeResult.getSuspendPayload());
                    emitWorkflowEvent(workflowEvents, onEvent, confirmEvent);

                    String suspendMessage = resolveSuspendMessage(nodeResult.getSuspendPayload());
                    Map<String, Object> completeEvent = buildCompleteEvent(
                            eventNodeId, nodeTypeCode, nodeLabel, true,
                            suspendMessage, stepIndex, nodeStartMs, nodeResult, eventNode, null);
                    completeEvent.put("suspended", true);
                    emitWorkflowEvent(workflowEvents, onEvent, completeEvent);

                    Map<String, Object> suspendedEvent = new LinkedHashMap<>();
                    suspendedEvent.put("type", "workflow_suspended");
                    suspendedEvent.put("runId", runId);
                    suspendedEvent.put("contentOffset", 0);
                    emitWorkflowEvent(workflowEvents, onEvent, suspendedEvent);

                    WorkflowSuspendedRun suspended = WorkflowSuspendedRun.builder()
                            .runId(runId)
                            .agentId(agentId)
                            .sessionId(context.getSessionId())
                            .userInput(context.getUserInput())
                            .workflowGraphJson(workflowGraphJson)
                            .variables(new LinkedHashMap<>(context.getVariables()))
                            .nodeOutputs(new LinkedHashMap<>(context.getNodeOutputs()))
                            .suspendNodeId(executingNodeId)
                            .nextNodeId(nodeResult.getNextNodeId())
                            .stepIndex(stepIndex)
                            .workflowEvents(workflowEvents != null ? new ArrayList<>(workflowEvents) : new ArrayList<>())
                            .source(suspendSource)
                            .build();
                    workflowRunStateUtil.saveSuspended(suspended);

                    return LoopOutcome.suspended(runId, nodeResult.getSuspendPayload(), result.toString());
                }

                if (nodeResult.getOutputs() != null) {
                    context.getNodeOutputs().put(executingNodeId, nodeResult.getOutputs());
                    WorkflowVariableScope.mergeNodeOutputs(context, node, nodeResult.getOutputs());
                }

                if (WorkflowChatExposure.shouldAppendStreamContentToChat(workflow, node, nodeResult)) {
                    result.append(nodeResult.getStreamContent());
                }

                if (nodeResult.isFinished() || node.getType() == NodeType.END) {
                    log.info("[WorkflowExecutorService] 工作流执行完成: agentId={}", agentId);
                    nextNodeId = null;
                } else {
                    nextNodeId = nodeResult.getNextNodeId();
                }
            } catch (Exception e) {
                nodeSuccess = false;
                nodeFailure = e;
                completeMessage = NodeResilienceMessageFormatter.formatUserMessage(e);
                log.error("[WorkflowExecutorService] 节点执行失败: nodeId={}, error={}",
                        executingNodeId, e.getMessage(), e);
                NodeExecutionResult observability = ParameterExtractParseException.toObservabilityResult(e);
                if (observability != null) {
                    nodeResult = observability;
                }
                nextNodeId = null;
            }

            Map<String, Object> completeEvent = buildCompleteEvent(
                    eventNodeId, nodeTypeCode, nodeLabel, nodeSuccess,
                    completeMessage, stepIndex, nodeStartMs, nodeResult, eventNode, nextNodeId);
            if (!nodeSuccess) {
                completeEvent.put("userMessage", completeMessage);
                completeEvent.put("failureReason", NodeResilienceMessageFormatter.resolveFailureReason(nodeFailure));
            }
            emitWorkflowEvent(workflowEvents, onEvent, completeEvent);

            currentNodeId = nextNodeId;
        }

        return LoopOutcome.completed(result.toString());
    }

    private String resolveSuspendMessage(Map<String, Object> suspendPayload) {
        if (suspendPayload != null
                && WorkflowHitlPayloadBuilder.HITL_TYPE_ASK_USER.equals(suspendPayload.get("hitlType"))) {
            return "等待用户回答";
        }
        return "等待人工确认";
    }

    private WorkflowNode resolveEventNode(WorkflowDefinition workflow, WorkflowNode node, NodeExecutionContext context) {
        if (workflow == null || node == null || context == null || context.getParentNodeId() != null) {
            return node;
        }
        NodeType type = node.getType();
        if ((type == NodeType.BATCH_START || type == NodeType.LOOP_START) && node.getParentNode() != null) {
            WorkflowNode parent = workflow.getNode(node.getParentNode());
            if (parent != null && (parent.getType() == NodeType.BATCH || parent.getType() == NodeType.LOOP)) {
                return parent;
            }
        }
        return node;
    }

    private Map<String, Object> buildCompleteEvent(String executingNodeId,
                                                   String nodeTypeCode,
                                                   String nodeLabel,
                                                   boolean nodeSuccess,
                                                   String completeMessage,
                                                   int stepIndex,
                                                   long nodeStartMs,
                                                   NodeExecutionResult nodeResult,
                                                   WorkflowNode node,
                                                   String nextNodeId) {
        Map<String, Object> completeEvent = new HashMap<>();
        completeEvent.put("type", "workflow_node_complete");
        completeEvent.put("nodeId", executingNodeId);
        completeEvent.put("nodeType", nodeTypeCode);
        completeEvent.put("nodeLabel", nodeLabel);
        completeEvent.put("message", completeMessage);
        completeEvent.put("success", nodeSuccess);
        completeEvent.put("durationMs", System.currentTimeMillis() - nodeStartMs);
        completeEvent.put("stepIndex", stepIndex);
        completeEvent.put("contentOffset", 0);
        if (nextNodeId != null) {
            completeEvent.put("nextNodeId", nextNodeId);
        }
        if (node != null && (node.getType() == NodeType.LOOP || node.getType() == NodeType.BATCH
                || node.getType() == NodeType.APP_COMPONENT)) {
            completeEvent.put("isContainer", true);
        }
        String detail = buildNodeDetail(node, nodeResult, nodeSuccess, completeMessage, nodeTypeCode);
        if (detail != null && !detail.isBlank()) {
            completeEvent.put("detail", detail);
        }
        if (nodeResult != null && nodeResult.getOutputs() != null && !nodeResult.getOutputs().isEmpty()) {
            // 工具节点：保留完整 toolResultText，供 Chat toolRegistry 渲染（生图/文件交付等）
            if ("tool".equals(nodeTypeCode)) {
                Map<String, Object> toolOutputs = WorkflowToolEventOutputs.build(nodeResult.getOutputs(), objectMapper);
                if (!toolOutputs.isEmpty()) {
                    completeEvent.put("outputs", toolOutputs);
                }
            } else {
                Map<String, Object> outputSummary = summarizeMap(nodeResult.getOutputs(), 10);
                if ("llm".equals(nodeTypeCode)) {
                    outputSummary.remove("llmOutput");
                }
                if (!outputSummary.isEmpty()) {
                    completeEvent.put("outputs", outputSummary);
                }
            }
        }
        if (nodeResult != null && nodeResult.getTraceData() != null && !nodeResult.getTraceData().isEmpty()) {
            completeEvent.put("traceData", nodeResult.getTraceData());
        }
        if (nodeResult != null && nodeResult.getOutputs() != null) {
            Object toolName = nodeResult.getOutputs().get("toolName");
            if (toolName != null && !String.valueOf(toolName).isBlank()) {
                completeEvent.put("toolName", toolName);
            }
        }
        return completeEvent;
    }

    private static final class LoopOutcome {
        private final boolean suspended;
        private final String runId;
        private final Map<String, Object> confirmForm;
        private final String streamResult;

        private LoopOutcome(boolean suspended, String runId, Map<String, Object> confirmForm, String streamResult) {
            this.suspended = suspended;
            this.runId = runId;
            this.confirmForm = confirmForm;
            this.streamResult = streamResult;
        }

        static LoopOutcome suspended(String runId, Map<String, Object> confirmForm, String streamResult) {
            return new LoopOutcome(true, runId, confirmForm, streamResult);
        }

        static LoopOutcome completed(String streamResult) {
            return new LoopOutcome(false, null, null, streamResult);
        }

        boolean isSuspended() {
            return suspended;
        }

        String getRunId() {
            return runId;
        }

        Map<String, Object> getConfirmForm() {
            return confirmForm;
        }

        String getStreamResult() {
            return streamResult != null ? streamResult : "";
        }

        String getSuspendedMessage() {
            return "工作流已暂停，等待人工确认后继续";
        }
    }

    /**
     * 执行单个节点（供子图迭代复用，不推送 workflow 事件）
     */
    public NodeExecutionResult executeNodeInContext(NodeExecutionContext context, String nodeId) {
        return nodeRunner.executeNodeInContext(context, nodeId);
    }

    /**
     * 构建节点入参预览，用于对话页展示链路传参。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> buildNodeInputPreview(WorkflowNode node, NodeExecutionContext context) {
        Map<String, Object> preview = new LinkedHashMap<>();
        if (node == null || context == null) {
            return preview;
        }

        if (node.getType() == NodeType.START) {
            preview.put("userInput", truncateDetail(context.getUserInput()));
            return preview;
        }

        Map<String, Object> nodeData = node.getData();
        if (nodeData != null && node.getType() == NodeType.RETRIEVAL) {
            String query = WorkflowVariableUtils.resolveInputText(
                    WorkflowNodeDataUtils.parseString(nodeData.get("inputVariable")),
                    context.getVariables(),
                    context.getUserInput());
            preview.put("query", summarizeValue(query));
            Object knowledgeId = nodeData.get("knowledgeId");
            if (knowledgeId != null) {
                preview.put("knowledgeId", summarizeValue(knowledgeId));
            }
            return preview;
        }
        if (nodeData != null && (node.getType() == NodeType.TOOL || node.getType() == NodeType.APP_COMPONENT)) {
            Map<String, Object> mappedArgs = WorkflowMappingUtils.buildInputArgs(nodeData, context);
            for (Map.Entry<String, Object> entry : mappedArgs.entrySet()) {
                preview.put(entry.getKey(), summarizeValue(entry.getValue()));
            }
            if (!preview.isEmpty()) {
                return preview;
            }
        }
        if (nodeData != null && node.getType() == NodeType.SUB_AGENT) {
            Object taskRaw = nodeData.get("task");
            if (taskRaw == null || String.valueOf(taskRaw).isBlank()) {
                taskRaw = "{{query}}";
            }
            Object task = WorkflowMappingUtils.resolveTemplateValue(taskRaw, context);
            preview.put("task", summarizeValue(task));
            Object subAgentId = nodeData.get("subAgentId");
            if (subAgentId != null) {
                preview.put("subAgentId", summarizeValue(subAgentId));
            }
            Object subAgentName = nodeData.get("subAgentName");
            if (subAgentName != null && !String.valueOf(subAgentName).isBlank()) {
                preview.put("subAgentName", summarizeValue(subAgentName));
            }
            return preview;
        }

        Object inputParamsObj = nodeData != null ? nodeData.get("input_params") : null;
        if (inputParamsObj == null && nodeData != null) {
            inputParamsObj = nodeData.get("inputParams");
        }
        if (inputParamsObj instanceof List<?> inputParamsList) {
            for (Object item : inputParamsList) {
                if (!(item instanceof Map<?, ?> paramMap)) {
                    continue;
                }
                Object keyVal = paramMap.get("key");
                String key = keyVal == null ? "" : String.valueOf(keyVal).trim();
                if (key.isEmpty()) {
                    continue;
                }
                Object rawValue = paramMap.get("value");
                Object value = resolveInputParamValue(rawValue, context);
                preview.put(key, summarizeValue(value));
            }
        }

        if (preview.isEmpty()) {
            addIfPresent(preview, "input", context.getVariables().get("input"));
            addIfPresent(preview, "query", context.getVariables().get("query"));
            addIfPresent(preview, "retrievalResult", context.getVariables().get("retrievalResult"));
            addIfPresent(preview, "llmOutput", context.getVariables().get("llmOutput"));
            addIfPresent(preview, "result", context.getVariables().get("result"));
        }
        return preview;
    }

    private Object resolveInputParamValue(Object rawValue, NodeExecutionContext context) {
        return WorkflowMappingUtils.resolveTemplateValue(rawValue, context);
    }

    private Map<String, Object> summarizeMap(Map<String, Object> source, int maxEntries) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (source == null || source.isEmpty()) {
            return out;
        }
        int count = 0;
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (count >= maxEntries) {
                out.put("_truncated", "其余字段已省略");
                break;
            }
            out.put(entry.getKey(), summarizeValue(entry.getValue()));
            count++;
        }
        return out;
    }

    private Object summarizeValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof String text) {
            return truncateDetail(text);
        }
        // List<Map> 结构（如 LLM 上下文消息列表）保留原始结构，便于前端渲染
        if (value instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map) {
            return value;
        }
        try {
            String json = objectMapper.writeValueAsString(value);
            return truncateDetail(json);
        } catch (Exception e) {
            return truncateDetail(String.valueOf(value));
        }
    }

    private void addIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, summarizeValue(value));
        }
    }

    /**
     * 将全局配置中的会话变量写入执行上下文
     */
    @SuppressWarnings("unchecked")
    /**
     * 将会话历史注入工作流变量，与对话型多轮一致（input / query / history_list）
     */
    private void injectSessionHistory(NodeExecutionContext context, Long sessionId, String userInput) {
        if (sessionId == null) {
            if (userInput != null) {
                context.getVariables().put("input", userInput);
                context.getVariables().put("query", userInput);
            }
            return;
        }
        List<Message> rows = messageService.listBySessionId(sessionId);
        List<Map<String, Object>> historyList = new ArrayList<>();
        for (Message row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            String role = row.getRole() != null ? row.getRole().getCode() : "user";
            item.put("role", role);
            item.put("content", row.getContent() != null ? row.getContent() : "");
            historyList.add(item);
        }
        context.getVariables().put("history_list", historyList);
        if (userInput != null) {
            context.getVariables().put("input", userInput);
            context.getVariables().put("query", userInput);
        }
    }

    private void applyGlobalConfig(NodeExecutionContext context, Map<String, Object> globalConfig) {
        if (globalConfig == null) {
            return;
        }
        Object variableConfig = globalConfig.get("variable_config");
        if (!(variableConfig instanceof Map<?, ?> varMap)) {
            return;
        }
        Object conversationParams = varMap.get("conversation_params");
        if (!(conversationParams instanceof List<?> params)) {
            return;
        }
        for (Object item : params) {
            if (item instanceof Map<?, ?> param) {
                Object key = param.get("key");
                if (key != null && !key.toString().isEmpty()) {
                    context.getVariables().putIfAbsent(key.toString(), param.get("default_value"));
                }
            }
        }
    }

    /**
     * 从节点 data 解析展示名称
     */
    private String resolveNodeLabel(WorkflowNode node) {
        if (node.getData() != null && node.getData().containsKey("label")) {
            Object label = node.getData().get("label");
            if (label != null && !label.toString().isEmpty()) {
                return label.toString();
            }
        }
        return node.getType() != null ? node.getType().getDesc() : "节点";
    }

    private void emitWorkflowEvent(List<Map<String, Object>> workflowEvents,
                                   Consumer<Map<String, Object>> onEvent,
                                   Map<String, Object> event) {
        if (workflowEvents != null) {
            synchronized (workflowEvents) {
                workflowEvents.add(event);
            }
        }
        if (onEvent != null) {
            synchronized (onEvent) {
                onEvent.accept(event);
            }
        }
    }

    /**
     * 节点完成后的可读摘要，供对话页展示节点间传递内容
     */
    private String buildNodeDetail(WorkflowNode node, NodeExecutionResult result,
                                   boolean success, String completeMessage, String nodeTypeCode) {
        if (!success) {
            return completeMessage;
        }
        if (result == null) {
            return null;
        }
        // LLM 节点：直接返回 llmOutput 文本（不走 JSON 序列化）
        if ("llm".equals(nodeTypeCode)) {
            Map<String, Object> llmOutputs = result.getOutputs();
            if (llmOutputs != null && llmOutputs.containsKey("llmOutput")) {
                return String.valueOf(llmOutputs.get("llmOutput"));
            }
            return result.getStreamContent();
        }
        if (result.getStreamContent() != null && !result.getStreamContent().isBlank()) {
            return truncateDetail(result.getStreamContent());
        }
        Map<String, Object> outputs = result.getOutputs();
        if (outputs == null || outputs.isEmpty()) {
            return null;
        }
        if (outputs.containsKey("retrievalResult")) {
            String text = String.valueOf(outputs.get("retrievalResult"));
            if (text.isBlank()) {
                return "知识检索：未命中相关内容";
            }
            return "知识检索命中 " + text.length() + " 字\n" + truncateDetail(text);
        }
        // LLM 节点的 llmOutput 即消息正文，跳过（避免对话页显示两遍）
        if (!"llm".equals(nodeTypeCode) && outputs.containsKey("llmOutput")) {
            return truncateDetail(String.valueOf(outputs.get("llmOutput")));
        }
        if (outputs.containsKey("result")) {
            return truncateDetail(String.valueOf(outputs.get("result")));
        }
        try {
            String json = objectMapper.writeValueAsString(outputs);
            return truncateDetail(json);
        } catch (Exception e) {
            return truncateDetail(outputs.toString());
        }
    }

    /**
     * 单节点测试主输出：优先 LLM/HTTP 等常见字段，否则序列化全部 outputs
     */
    private String formatSingleNodeOutput(NodeExecutionResult nodeResult) {
        if (nodeResult == null) {
            return "";
        }
        if (nodeResult.getStreamContent() != null && !nodeResult.getStreamContent().isBlank()) {
            return nodeResult.getStreamContent();
        }
        Map<String, Object> outputs = nodeResult.getOutputs();
        if (outputs == null || outputs.isEmpty()) {
            return "";
        }
        if (outputs.containsKey("llmOutput")) {
            return String.valueOf(outputs.get("llmOutput"));
        }
        if (outputs.containsKey("body")) {
            return String.valueOf(outputs.get("body"));
        }
        if (outputs.containsKey("result")) {
            return String.valueOf(outputs.get("result"));
        }
        if (outputs.containsKey("retrievalResult")) {
            return String.valueOf(outputs.get("retrievalResult"));
        }
        try {
            return objectMapper.writeValueAsString(outputs);
        } catch (Exception jsonEx) {
            return outputs.toString();
        }
    }

    private static String truncateDetail(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        int max = 400;
        if (trimmed.length() <= max) {
            return trimmed;
        }
        return trimmed.substring(0, max) + "…";
    }

    /**
     * 调试执行：返回输出内容 + 变量快照，供测试面板展示变量面板
     */
    public WorkflowTestResultVO executeForTest(Agent agent, WorkflowDefinition workflow,
                                               String userInput, List<Map<String, Object>> workflowEvents,
                                               Map<String, Object> initialVariables, String assignedRunId) {
        return executeForTest(agent, workflow, userInput, workflowEvents, initialVariables, assignedRunId, null);
    }

    /**
     * 调试执行：返回输出内容 + 变量快照，支持实时推送节点事件
     */
    public WorkflowTestResultVO executeForTest(Agent agent, WorkflowDefinition workflow,
                                               String userInput, List<Map<String, Object>> workflowEvents,
                                               Map<String, Object> initialVariables, String assignedRunId,
                                               Consumer<Map<String, Object>> onEvent) {
        NodeExecutionContext context = NodeExecutionContext.builder()
                .agentId(agent.getId())
                .userInput(userInput)
                .agentConfig(agent.getConfig())
                .workflow(workflow)
                .variables(new LinkedHashMap<>())
                .scopedVariables(new LinkedHashMap<>())
                .sysVariables(new LinkedHashMap<>())
                .nodeOutputs(new LinkedHashMap<>())
                .workflowEvents(workflowEvents)
                .build();
        WorkflowVariableScope.initContext(context);

        applyGlobalConfig(context, workflow.getGlobalConfig());
        injectSessionHistory(context, null, userInput);
        WorkflowVariableScope.syncSysBucket(context);
        if (initialVariables != null && !initialVariables.isEmpty()) {
            context.getVariables().putAll(initialVariables);
            WorkflowVariableScope.syncSysBucket(context);
        }

        String currentNodeId = workflow.getStartNodeId();
        if (currentNodeId == null) {
            return WorkflowTestResultVO.builder().output("工作流缺少开始节点").nodeEvents(workflowEvents).variables(context.getVariables()).build();
        }

        String workflowJson = serializeWorkflow(workflow);
        ExecutionOptions options = ExecutionOptions.test(assignedRunId);
        LoopOutcome outcome = runExecutionLoop(
                agent, workflow, context, currentNodeId, 0,
                new StringBuilder(), workflowEvents, onEvent, workflowJson, options);

        WorkflowTestResultVO.WorkflowTestResultVOBuilder builder = WorkflowTestResultVO.builder()
                .nodeEvents(workflowEvents)
                .variables(new LinkedHashMap<>(context.getVariables()))
                .suspended(outcome.isSuspended())
                .runId(assignedRunId);

        if (outcome.isSuspended()) {
            builder.runId(outcome.getRunId() != null ? outcome.getRunId() : assignedRunId)
                    .confirmForm(outcome.getConfirmForm())
                    .output(outcome.getSuspendedMessage());
            return builder.build();
        }

        emitWorkflowEvent(workflowEvents, onEvent, Map.of("type", "workflow_complete", "contentOffset", 0));

        String output = context.getVariables().containsKey("result")
                ? WorkflowResultUtils.formatAsText(context.getVariables().get("result"))
                : outcome.getStreamResult();

        return builder.output(output).build();
    }

    /**
     * 单节点调试：仅执行指定节点处理器，不跑完整 DAG
     */
    public WorkflowTestResultVO executeSingleNode(Agent agent, WorkflowDefinition workflow, String nodeId,
                                                  Map<String, Object> initialVariables) {
        WorkflowNode node = workflow.getNode(nodeId);
        if (node == null) {
            throw new IllegalArgumentException("节点不存在: " + nodeId);
        }
        if (node.getType() == NodeType.START || node.getType() == NodeType.END) {
            throw new IllegalArgumentException("开始/结束节点不支持单节点测试");
        }
        if (node.getType() == NodeType.CONFIRM) {
            throw new IllegalArgumentException("人工确认节点不支持单节点测试，请使用全流调试");
        }

        NodeExecutionContext context = NodeExecutionContext.builder()
                .agentId(agent.getId())
                .agentConfig(agent.getConfig())
                .workflow(workflow)
                .variables(new LinkedHashMap<>())
                .scopedVariables(new LinkedHashMap<>())
                .sysVariables(new LinkedHashMap<>())
                .nodeOutputs(new LinkedHashMap<>())
                .currentNodeId(nodeId)
                .currentNodeData(node.getData())
                .build();
        WorkflowVariableScope.initContext(context);

        applyGlobalConfig(context, workflow.getGlobalConfig());
        if (initialVariables != null && !initialVariables.isEmpty()) {
            context.getVariables().putAll(initialVariables);
        }

        List<Map<String, Object>> events = new ArrayList<>();
        String nodeLabel = resolveNodeLabel(node);
        String nodeTypeCode = node.getType().getCode();
        long nodeStartMs = System.currentTimeMillis();
        emitWorkflowEvent(events, null, Map.of(
                "type", "workflow_node_start",
                "nodeId", nodeId,
                "nodeType", nodeTypeCode,
                "nodeLabel", nodeLabel));

        boolean nodeSuccess = true;
        String completeMessage = "执行完成";
        NodeExecutionResult nodeResult = null;
        String output = "";
        Exception nodeFailure = null;

        try {
            NodeProcessor processor = registry.getProcessor(node.getType());
            NodeResilienceEventContext resilienceCtx = NodeResilienceEventContext.builder()
                    .nodeId(nodeId)
                    .nodeLabel(nodeLabel)
                    .nodeType(nodeTypeCode)
                    .eventEmitter(e -> emitWorkflowEvent(events, null, e))
                    .build();
            nodeResult = NodeTimeoutRetryHelper.executeWithTimeoutAndRetry(
                    nodeId, node.getType(), node.getData(),
                    () -> processor.execute(context),
                    resilienceCtx);
            if (nodeResult.getOutputs() != null) {
                context.getNodeOutputs().put(nodeId, nodeResult.getOutputs());
                WorkflowVariableScope.mergeNodeOutputs(context, node, nodeResult.getOutputs());
            }
            output = formatSingleNodeOutput(nodeResult);
        } catch (Exception e) {
            nodeSuccess = false;
            nodeFailure = e;
            completeMessage = NodeResilienceMessageFormatter.formatUserMessage(e);
            output = completeMessage;
            NodeExecutionResult observability = ParameterExtractParseException.toObservabilityResult(e);
            if (observability != null) {
                nodeResult = observability;
            }
            log.error("[WorkflowExecutorService] 单节点测试失败: nodeId={}, error={}", nodeId, e.getMessage(), e);
        }

        Map<String, Object> completeEvent = new HashMap<>();
        completeEvent.put("type", "workflow_node_complete");
        completeEvent.put("nodeId", nodeId);
        completeEvent.put("nodeType", nodeTypeCode);
        completeEvent.put("nodeLabel", nodeLabel);
        completeEvent.put("message", completeMessage);
        completeEvent.put("success", nodeSuccess);
        completeEvent.put("durationMs", System.currentTimeMillis() - nodeStartMs);
        if (!nodeSuccess) {
            completeEvent.put("userMessage", completeMessage);
            completeEvent.put("failureReason", NodeResilienceMessageFormatter.resolveFailureReason(nodeFailure));
        }
        String detail = buildNodeDetail(node, nodeResult, nodeSuccess, completeMessage, nodeTypeCode);
        if (detail != null && !detail.isBlank()) {
            completeEvent.put("detail", detail);
            if (output.isBlank()) {
                output = detail;
            }
        }
        if (nodeSuccess && nodeResult != null && nodeResult.getOutputs() != null && !nodeResult.getOutputs().isEmpty()) {
            Map<String, Object> filteredOutputs = new LinkedHashMap<>(nodeResult.getOutputs());
            // LLM 节点：llmOutput 即消息正文，从出参中移除避免对话页重复展示
            if ("llm".equals(nodeTypeCode)) {
                filteredOutputs.remove("llmOutput");
            }
            if (!filteredOutputs.isEmpty()) {
                completeEvent.put("outputs", filteredOutputs);
            }
        } else if (!nodeSuccess && nodeResult != null && nodeResult.getOutputs() != null && !nodeResult.getOutputs().isEmpty()) {
            completeEvent.put("outputs", new LinkedHashMap<>(nodeResult.getOutputs()));
        }
        // traceData 独立存放，不混入 outputs（避免 Chat 页组件误将其作为执行结果展示）
        if (nodeResult != null && nodeResult.getTraceData() != null && !nodeResult.getTraceData().isEmpty()) {
            completeEvent.put("traceData", nodeResult.getTraceData());
        }
        emitWorkflowEvent(events, null, completeEvent);

        return WorkflowTestResultVO.builder()
                .output(output)
                .nodeEvents(events)
                .usedDraft(true)
                .build();
    }

    /**
     * 嵌套执行已发布子工作流（app_component 节点调用）
     *
     * @param parentContext  父流程上下文
     * @param targetAgentId  目标 Workflow Agent ID
     * @param inputVariables 子流程入参
     * @param userInput      子流程用户输入（可为空）
     * @return 子流程变量快照与输出
     */
    public SubWorkflowExecutionResult executeSubWorkflow(NodeExecutionContext parentContext,
                                                         Long targetAgentId,
                                                         Map<String, Object> inputVariables,
                                                         String userInput) {
        if (parentContext.getSubWorkflowDepth() >= MAX_SUB_WORKFLOW_DEPTH) {
            throw new IllegalStateException("子工作流嵌套超过最大深度 " + MAX_SUB_WORKFLOW_DEPTH);
        }
        if (targetAgentId == null) {
            throw new IllegalArgumentException("未指定子工作流 Agent");
        }
        if (targetAgentId.equals(parentContext.getAgentId())) {
            throw new IllegalArgumentException("子工作流不能引用自身");
        }

        Agent targetAgent = agentService.getById(targetAgentId);
        if (targetAgent == null) {
            throw new IllegalArgumentException("子工作流 Agent 不存在: " + targetAgentId);
        }
        if (targetAgent.getAgentType() != AgentType.WORKFLOW) {
            throw new IllegalArgumentException("目标 Agent 不是工作流类型");
        }
        Integer publishedVersion = targetAgent.getVersion();
        if (publishedVersion == null || publishedVersion <= 0) {
            throw new IllegalStateException("子工作流尚未发布，无法调用");
        }

        WorkflowDefinition subWorkflow = agentVersionService.loadWorkflowDefinition(targetAgentId, false);
        if (subWorkflow == null || subWorkflow.getNodes() == null || subWorkflow.getNodes().isEmpty()) {
            throw new IllegalStateException("子工作流定义为空");
        }

        String subUserInput = userInput != null ? userInput : "";
        Map<String, Object> subVars = new LinkedHashMap<>();
        if (inputVariables != null) {
            subVars.putAll(inputVariables);
        }
        subVars.putIfAbsent("input", subUserInput);
        subVars.putIfAbsent("query", subVars.get("input"));

        NodeExecutionContext subContext = NodeExecutionContext.builder()
                .agentId(targetAgentId)
                .sessionId(parentContext.getSessionId())
                .userInput(subUserInput)
                .agentConfig(targetAgent.getConfig())
                .workflow(subWorkflow)
                .variables(subVars)
                .scopedVariables(new LinkedHashMap<>())
                .sysVariables(new LinkedHashMap<>())
                .nodeOutputs(new LinkedHashMap<>())
                .workflowEvents(parentContext.getWorkflowEvents())
                .onEvent(parentContext.getOnEvent())
                .onStreamChunk(parentContext.getOnStreamChunk())
                .parentNodeId(parentContext.getCurrentNodeId())
                .subWorkflowDepth(parentContext.getSubWorkflowDepth() + 1)
                .build();
        WorkflowVariableScope.initContext(subContext);
        WorkflowVariableScope.syncSysBucket(subContext);

        applyGlobalConfig(subContext, subWorkflow.getGlobalConfig());

        String startNodeId = subWorkflow.getStartNodeId();
        if (startNodeId == null) {
            throw new IllegalStateException("子工作流缺少开始节点");
        }

        LoopOutcome outcome = runExecutionLoop(
                targetAgent, subWorkflow, subContext, startNodeId, 0,
                new StringBuilder(), parentContext.getWorkflowEvents(),
                parentContext.getOnEvent(), serializeWorkflow(subWorkflow),
                ExecutionOptions.chat());

        if (outcome.isSuspended()) {
            throw new IllegalStateException("嵌套子工作流不支持人工确认挂起，请在子流程中移除 confirm 节点");
        }

        String output = subContext.getVariables().containsKey("result")
                ? WorkflowResultUtils.formatAsText(subContext.getVariables().get("result"))
                : outcome.getStreamResult();

        return SubWorkflowExecutionResult.builder()
                .output(output)
                .variables(new LinkedHashMap<>(subContext.getVariables()))
                .nodeOutputs(new LinkedHashMap<>(subContext.getNodeOutputs()))
                .build();
    }

    /**
     * 执行循环选项：测试 runId 与挂起来源
     */
    private static final class ExecutionOptions {
        private final String assignedRunId;
        private final String suspendSource;

        private ExecutionOptions(String assignedRunId, String suspendSource) {
            this.assignedRunId = assignedRunId;
            this.suspendSource = suspendSource;
        }

        static ExecutionOptions test(String runId) {
            return new ExecutionOptions(runId, "test");
        }

        static ExecutionOptions chat() {
            return new ExecutionOptions(null, "chat");
        }

        static ExecutionOptions fromSuspended(WorkflowSuspendedRun suspended) {
            String source = suspended.getSource() != null ? suspended.getSource() : "chat";
            return new ExecutionOptions(suspended.getRunId(), source);
        }
    }
}
