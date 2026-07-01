package com.lightbot.service.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.dto.LlmTraceSpan;
import com.lightbot.entity.LlmTrace;
import com.lightbot.enums.AgentType;
import com.lightbot.enums.MessageRole;
import com.lightbot.service.AgentVersionService;
import com.lightbot.service.LlmTraceService;
import com.lightbot.util.SensitiveWordFilter;
import com.lightbot.workflow.WorkflowDefinition;
import com.lightbot.workflow.WorkflowEdge;
import com.lightbot.workflow.WorkflowExecutorService;
import com.lightbot.workflow.WorkflowNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.function.Consumer;

import static com.lightbot.service.chat.ToolEventGenerator.*;

/**
 * 工作流中间件
 * <p>WORKFLOW 类型 Agent 执行工作流 DAG，跳过后续 LLM 中间件</p>
 *
 * @author finch
 * @since 2026-05-24
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowMiddleware implements ChatMiddleware {

    private final WorkflowExecutorService workflowExecutor;
    private final AgentVersionService agentVersionService;
    private final MessageMiddleware messageMiddleware;
    private final TraceMiddleware traceMiddleware;
    private final LlmTraceService llmTraceService;
    private final ThreadPoolTaskExecutor taskExecutor;
    private final ObjectMapper objectMapper;

    @Override
    public Flux<String> execute(ChatContext ctx, ChatMiddlewareChain next) {
        if (ctx.getAgent() == null || ctx.getAgent().getAgentType() != AgentType.WORKFLOW) {
            return next.proceed(ctx);
        }

        log.info("[WorkflowMiddleware] 开始执行工作流: agentId={}, sessionId={}, configVersion={}",
                ctx.getAgent().getId(), ctx.getSessionId(), ctx.getRequest().getConfigVersion());

        // 1. 持久化用户消息（工作流不走 MessageMiddleware 链）；重新生成时不重复落库
        if (Boolean.TRUE.equals(ctx.getRequest().getRegenerate())) {
            messageMiddleware.deleteLastAssistantMessage(ctx.getSessionId());
        } else {
            messageMiddleware.saveMessage(ctx.getSessionId(), MessageRole.USER, ctx.getRequest().getMessage());
        }

        return Flux.<String>create(sink -> Schedulers.boundedElastic().schedule(() -> {
            long t0 = System.currentTimeMillis();
            try {
                List<Map<String, Object>> workflowEvents = new ArrayList<>();
                Consumer<Map<String, Object>> emit = event -> {
                    ctx.getWorkflowEventsList().add(event);
                    try {
                        sink.next(STATUS_PREFIX + objectMapper.writeValueAsString(event));
                    } catch (Exception ex) {
                        sink.error(ex);
                    }
                };

                // LLM 流式回调：逐 token 推送到前端
                final boolean[] streamed = {false};
                Consumer<String> streamChunk = chunk -> {
                    streamed[0] = true;
                    try {
                        Map<String, Object> chunkEvent = new LinkedHashMap<>();
                        chunkEvent.put("type", "workflow_llm_chunk");
                        chunkEvent.put("content", chunk);
                        sink.next(STATUS_PREFIX + objectMapper.writeValueAsString(chunkEvent));
                    } catch (Exception ex) {
                        log.warn("[WorkflowMiddleware] 流式 chunk 推送失败: {}", ex.getMessage());
                    }
                };

                WorkflowDefinition workflow = agentVersionService.loadWorkflowDefinitionForChat(
                        ctx.getAgent().getId(), ctx.getRequest().getConfigVersion());

                String result;
                if (workflow == null || workflow.getNodes() == null || workflow.getNodes().isEmpty()) {
                    result = "工作流尚未发布或为空，请先在编排页发布工作流，或切换到暂存草稿调试";
                } else {
                    result = workflowExecutor.executeWithDefinition(
                            ctx.getAgent(),
                            workflow,
                            ctx.getSessionId(),
                            ctx.getRequest().getMessage(),
                            workflowEvents,
                            emit,
                            null,
                            streamChunk
                    );
                }

                result = resolveAssistantContent(result, workflowEvents);
                if (result != null) {
                    SensitiveWordFilter.FilterResult filtered = SensitiveWordFilter.filterAiOutput(
                            result, ctx.getConfigMap(), ctx.getAgent().getId(), ctx.getSessionId());
                    result = filtered.text();
                    ctx.getFullReply().append(result);
                }

                Map<String, Object> metadataMap = new LinkedHashMap<>();
                metadataMap.put("workflowEvents", workflowEvents);
                boolean workflowSuspended = workflowEvents.stream()
                        .anyMatch(e -> "workflow_suspended".equals(e.get("type")));
                if (workflowSuspended) {
                    metadataMap.put("workflowSuspended", true);
                    for (int i = workflowEvents.size() - 1; i >= 0; i--) {
                        Map<String, Object> ev = workflowEvents.get(i);
                        if ("workflow_suspended".equals(ev.get("type")) && ev.get("runId") != null) {
                            metadataMap.put("workflowRunId", ev.get("runId"));
                            break;
                        }
                    }
                }
                if (ctx.getRequestId() != null && !ctx.getRequestId().isBlank()) {
                    metadataMap.put("requestId", ctx.getRequestId());
                }
                if (ctx.getRequest().getConfigVersion() != null) {
                    metadataMap.put("configVersion", ctx.getRequest().getConfigVersion());
                }
                ctx.getRagMetadataHolder()[0] = objectMapper.writeValueAsString(metadataMap);

                // 流式已逐 token 推送，不再重复发送完整结果
                if (!streamed[0] && result != null && !result.isEmpty()) {
                    sink.next(result);
                }
                sink.next(METADATA_PREFIX + ctx.getRagMetadataHolder()[0]);
                sink.complete();

                // 2. 持久化助手回复（含工作流事件 metadata，与对话型一致落库）
                String contentToSave = ctx.getFullReply().toString();
                if (contentToSave.isEmpty() && !workflowEvents.isEmpty()) {
                    contentToSave = resolveAssistantContent(null, workflowEvents);
                }
                messageMiddleware.saveMessage(ctx.getSessionId(), MessageRole.ASSISTANT,
                        contentToSave, ctx.getRagMetadataHolder()[0], 0);
                taskExecutor.execute(() -> traceMiddleware.generateTitle(ctx.getSessionId(), ctx.getAgent(), ctx.getConfigMap()));

                // 3. 异步写入工作流调用链 trace
                final String traceResult = result;
                taskExecutor.execute(() -> buildWorkflowTrace(ctx, workflow, workflowEvents, traceResult, t0));

                log.info("[WorkflowMiddleware] 工作流执行完成: agentId={}, nodes={}, resultLength={}",
                        ctx.getAgent().getId(), workflowEvents.size(), contentToSave.length());
            } catch (Exception e) {
                log.error("[WorkflowMiddleware] 工作流执行失败: agentId={}, error={}",
                        ctx.getAgent().getId(), e.getMessage(), e);
                String err = "工作流执行失败: " + e.getMessage();
                ctx.getFullReply().append(err);
                messageMiddleware.saveMessage(ctx.getSessionId(), MessageRole.ASSISTANT, err);
                sink.next(err);
                sink.complete();

                // 异常时也记录 trace
                taskExecutor.execute(() -> {
                    try {
                        LlmTrace trace = new LlmTrace();
                        trace.setRequestId(ctx.getRequestId());
                        trace.setSessionId(ctx.getSessionId());
                        trace.setUserId(ctx.getAgent().getUserId());
                        trace.setAgentId(ctx.getAgent().getId());
                        trace.setAgentName(ctx.getAgent().getName());
                        trace.setTraceSource("workflow");
                        trace.setStatus("failed");
                        trace.setTotalDurationMs(System.currentTimeMillis() - t0);
                        trace.setReplyContent(err);
                        trace.setErrorMessage(e.getMessage());
                        trace.setSpans("[]");
                        llmTraceService.recordTrace(trace);
                    } catch (Exception ex) {
                        log.error("[WorkflowMiddleware] 异常 trace 记录失败", ex);
                    }
                });
            }
        })).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 无模型文本输出时，从工作流节点结果生成可展示的回复摘要
     */
    @SuppressWarnings("unchecked")
    static String resolveAssistantContent(String result, List<Map<String, Object>> events) {
        if (result != null && !result.isBlank()) {
            return result;
        }
        if (events == null || events.isEmpty()) {
            return "";
        }
        // 1. 优先返回最后一个失败节点的错误信息
        for (int i = events.size() - 1; i >= 0; i--) {
            Map<String, Object> e = events.get(i);
            if (!"workflow_node_complete".equals(e.get("type"))) {
                continue;
            }
            if (Boolean.FALSE.equals(e.get("success"))) {
                String nodeLabel = e.get("nodeLabel") != null ? e.get("nodeLabel").toString() : "";
                String message = e.get("message") != null ? e.get("message").toString() : "未知错误";
                return "节点【" + nodeLabel + "】执行失败：" + message;
            }
        }
        // 2. 从最后一个成功节点提取输出
        for (int i = events.size() - 1; i >= 0; i--) {
            Map<String, Object> e = events.get(i);
            if (!"workflow_node_complete".equals(e.get("type"))) {
                continue;
            }
            if (Boolean.FALSE.equals(e.get("success"))) {
                continue;
            }
            Object detail = e.get("detail");
            if (detail != null && !detail.toString().isBlank()) {
                return detail.toString();
            }
            Object outputs = e.get("outputs");
            if (outputs instanceof Map<?, ?> outputMap && !outputMap.isEmpty()) {
                for (String key : List.of("result", "output", "text", "answer", "llmOutput")) {
                    Object val = outputMap.get(key);
                    if (val != null && !val.toString().isBlank()) {
                        return val.toString();
                    }
                }
                // 跳过 classifier 等中间节点的结构化输出，继续向上查找
                String nodeType = e.get("nodeType") != null ? e.get("nodeType").toString() : "";
                if ("classifier".equals(nodeType) || "condition".equals(nodeType)) {
                    continue;
                }
                return outputMap.values().iterator().next().toString();
            }
        }
        return "工作流已执行完成";
    }

    // ==================== 工作流 Trace 构建 ====================

    /**
     * 从工作流执行事件构建 trace spans 并异步持久化
     */
    @SuppressWarnings("unchecked")
    private void buildWorkflowTrace(ChatContext ctx, WorkflowDefinition workflow,
                                    List<Map<String, Object>> events, String result, long startTime) {
        try {
            // 1. 构建节点定义映射（用于提取配置）
            Map<String, WorkflowNode> nodeDefMap = new HashMap<>();
            if (workflow != null && workflow.getNodes() != null) {
                for (WorkflowNode node : workflow.getNodes()) {
                    nodeDefMap.put(node.getId(), node);
                }
            }

            // 2. 构建根 span
            List<LlmTraceSpan> spans = new ArrayList<>();
            long totalDurationMs = System.currentTimeMillis() - startTime;
            boolean hasError = events.stream().anyMatch(e ->
                    "workflow_node_complete".equals(e.get("type")) && Boolean.FALSE.equals(e.get("success")));
            String rootStatus = hasError ? "failed" : "completed";
            Map<String, Object> rootAttrs = new HashMap<>();
            rootAttrs.put("nodeCount", nodeDefMap.size());
            rootAttrs.put("eventCount", events.size());
            rootAttrs.put("resultPreview", result != null ? truncate(result, 200) : "");
            // 保存用户原始问题（供前端展示）
            String userInput = ctx.getRequest() != null ? ctx.getRequest().getMessage() : null;
            if (userInput != null && !userInput.isBlank()) {
                rootAttrs.put("userInput", userInput);
            }
            // 保存工作流边定义（用于前端 DAG 渲染）
            if (workflow != null && workflow.getEdges() != null) {
                List<Map<String, Object>> edgeList = new ArrayList<>();
                for (WorkflowEdge edge : workflow.getEdges()) {
                    Map<String, Object> edgeMap = new LinkedHashMap<>();
                    edgeMap.put("id", edge.getId());
                    edgeMap.put("source", edge.getSource());
                    edgeMap.put("target", edge.getTarget());
                    if (edge.getLabel() != null) edgeMap.put("label", edge.getLabel());
                    if (edge.getSourceHandle() != null) edgeMap.put("sourceHandle", edge.getSourceHandle());
                    edgeList.add(edgeMap);
                }
                rootAttrs.put("edges", edgeList);
            }
            // 保存所有节点定义（含 position，用于前端布局）
            if (workflow != null && workflow.getNodes() != null) {
                List<Map<String, Object>> nodeList = new ArrayList<>();
                for (WorkflowNode n : workflow.getNodes()) {
                    Map<String, Object> nodeMap = new LinkedHashMap<>();
                    nodeMap.put("id", n.getId());
                    nodeMap.put("type", n.getType() != null ? n.getType().getCode() : "");
                    if (n.getPosition() != null) nodeMap.put("position", n.getPosition());
                    String label = n.getData() != null ? (String) n.getData().get("label") : null;
                    nodeMap.put("label", label != null ? label : n.getId());
                    nodeList.add(nodeMap);
                }
                rootAttrs.put("nodes", nodeList);
            }
            spans.add(LlmTraceSpan.of("workflow_run", null, "workflow_run",
                    startTime, totalDurationMs, rootStatus, rootAttrs));

            // 3. 收集每个节点的 start/complete 事件
            Map<String, Map<String, Object>> startEvents = new HashMap<>();
            Map<String, Map<String, Object>> completeEvents = new HashMap<>();
            for (Map<String, Object> event : events) {
                String type = (String) event.get("type");
                String nodeId = (String) event.get("nodeId");
                if (nodeId == null) continue;
                if ("workflow_node_start".equals(type)) {
                    startEvents.put(nodeId, event);
                } else if ("workflow_node_complete".equals(type)) {
                    completeEvents.put(nodeId, event);
                }
            }

            // 4. 为每个完成的节点构建 span
            int[] llmTokenAgg = {0, 0};
            for (Map.Entry<String, Map<String, Object>> entry : completeEvents.entrySet()) {
                String nodeId = entry.getKey();
                Map<String, Object> complete = entry.getValue();
                Map<String, Object> start = startEvents.get(nodeId);
                buildNodeSpan(nodeId, start, complete, nodeDefMap.get(nodeId), spans, llmTokenAgg);
            }

            // 5. 收集错误信息
            String errorMessage = null;
            if ("failed".equals(rootStatus)) {
                for (Map<String, Object> event : events) {
                    if ("workflow_node_complete".equals(event.get("type")) && Boolean.FALSE.equals(event.get("success"))) {
                        Object msg = event.get("message");
                        if (msg != null && !String.valueOf(msg).isBlank()) {
                            errorMessage = String.valueOf(msg);
                            break;
                        }
                    }
                }
            }

            // 6. 构建并持久化 LlmTrace
            LlmTrace trace = new LlmTrace();
            trace.setRequestId(ctx.getRequestId());
            trace.setSessionId(ctx.getSessionId());
            trace.setUserId(ctx.getAgent().getUserId());
            trace.setAgentId(ctx.getAgent().getId());
            trace.setAgentName(ctx.getAgent().getName());
            trace.setModel(null);
            trace.setTraceSource("workflow");
            trace.setStatus(rootStatus);
            trace.setInputTokens(llmTokenAgg[0]);
            trace.setOutputTokens(llmTokenAgg[1]);
            trace.setTotalTokens(llmTokenAgg[0] + llmTokenAgg[1]);
            trace.setToolCallCount(0);
            trace.setTotalDurationMs(totalDurationMs);
            trace.setReplyContent(result);
            trace.setErrorMessage(errorMessage);
            trace.setSpans(objectMapper.writeValueAsString(spans));
            llmTraceService.recordTrace(trace);
        } catch (Exception e) {
            log.error("[WorkflowMiddleware] 工作流 trace 构建失败: agentId={}, error={}",
                    ctx.getAgent().getId(), e.getMessage(), e);
        }
    }

    /**
     * 为单个节点构建 span（含 LLM 子 span）
     */
    @SuppressWarnings("unchecked")
    private void buildNodeSpan(String nodeId, Map<String, Object> start, Map<String, Object> complete,
                               WorkflowNode nodeDef, List<LlmTraceSpan> spans, int[] llmTokenAgg) {
        String nodeType = (String) complete.get("nodeType");
        String nodeLabel = (String) complete.get("nodeLabel");
        boolean success = Boolean.TRUE.equals(complete.get("success"));
        Object durationObj = complete.get("durationMs");
        long durationMs = durationObj instanceof Number ? ((Number) durationObj).longValue() : 0;
        long nodeStartMs = start != null ? ((Number) start.getOrDefault("startTime", System.currentTimeMillis())).longValue()
                : System.currentTimeMillis() - durationMs;

        // 节点配置（从定义中提取）
        Map<String, Object> nodeConfig = extractNodeConfig(nodeDef, nodeType);

        // 节点属性
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("nodeType", nodeType);
        attrs.put("nodeLabel", nodeLabel != null ? nodeLabel : nodeId);
        if (!nodeConfig.isEmpty()) {
            attrs.put("config", nodeConfig);
        }
        if (start != null && start.containsKey("input")) {
            attrs.put("input", start.get("input"));
        }
        if (complete.containsKey("outputs")) {
            attrs.put("outputs", complete.get("outputs"));
        }
        // traceData 合并到 outputs（仅用于 trace span，不影响 Chat 页展示）
        if (complete.containsKey("traceData")) {
            Object existing = attrs.get("outputs");
            if (existing instanceof Map<?, ?> existingMap) {
                Map<String, Object> merged = new LinkedHashMap<>((Map<String, Object>) existingMap);
                merged.putAll((Map<String, Object>) complete.get("traceData"));
                attrs.put("outputs", merged);
            } else {
                attrs.put("outputs", complete.get("traceData"));
            }
        }
        if (complete.containsKey("detail")) {
            attrs.put("detail", truncate(String.valueOf(complete.get("detail")), 500));
        }
        attrs.put("success", success);
        if (complete.containsKey("message")) {
            attrs.put("message", complete.get("message"));
        }

        String spanId = "node:" + nodeId;
        spans.add(LlmTraceSpan.of(spanId, "workflow_run", "node:" + nodeType,
                nodeStartMs, durationMs, success ? "completed" : "failed", attrs));

        // LLM / Classifier 节点：构建嵌套 llm_call 子 span
        if ("llm".equals(nodeType) || "classifier".equals(nodeType)) {
            buildLlmSpan(nodeId, nodeDef, nodeConfig, complete, nodeStartMs, durationMs, success, spans, llmTokenAgg);
        }
    }

    /**
     * 为 LLM 节点构建嵌套的 llm_call 子 span
     */
    private void buildLlmSpan(String nodeId, WorkflowNode nodeDef, Map<String, Object> nodeConfig,
                              Map<String, Object> complete,
                              long nodeStartMs, long durationMs, boolean success,
                              List<LlmTraceSpan> spans, int[] llmTokenAgg) {
        String model = extractString(nodeConfig, "model");
        String sysPrompt = extractString(nodeConfig, "sysPrompt");
        String promptTemplate = extractString(nodeConfig, "promptTemplate");

        Map<String, Object> llmAttrs = new HashMap<>();
        if (model != null) llmAttrs.put("model", model);
        if (sysPrompt != null) llmAttrs.put("sysPrompt", truncate(sysPrompt, 300));
        if (promptTemplate != null) llmAttrs.put("promptTemplate", truncate(promptTemplate, 300));
        llmAttrs.put("streaming", Boolean.TRUE.equals(nodeConfig.get("enableStreaming")));

        // LLM token 统计（从节点 traceData 中提取）
        int inputTokens = 0;
        int outputTokens = 0;
        Object traceDataObj = complete.get("traceData");
        if (traceDataObj instanceof Map<?, ?> traceMap) {
            Object inVal = traceMap.get("inputTokens");
            Object outVal = traceMap.get("outputTokens");
            if (inVal instanceof Number n) inputTokens = n.intValue();
            if (outVal instanceof Number n) outputTokens = n.intValue();
        }
        llmAttrs.put("inputTokens", inputTokens);
        llmAttrs.put("outputTokens", outputTokens);
        llmTokenAgg[0] += inputTokens;
        llmTokenAgg[1] += outputTokens;

        String llmSpanId = "llm:" + nodeId;
        String parentSpanId = "node:" + nodeId;
        spans.add(LlmTraceSpan.of(llmSpanId, parentSpanId, "llm_call",
                nodeStartMs, durationMs, success ? "completed" : "failed", llmAttrs));
    }

    /**
     * 从节点定义中提取关键配置（排除 UI 相关字段）
     */
    private Map<String, Object> extractNodeConfig(WorkflowNode nodeDef, String nodeType) {
        if (nodeDef == null || nodeDef.getData() == null) {
            return Map.of();
        }
        Map<String, Object> data = nodeDef.getData();
        Map<String, Object> config = new LinkedHashMap<>();
        // 按节点类型提取关键配置
        List<String> keys = getConfigKeys(nodeType);
        if (!keys.isEmpty()) {
            for (String key : keys) {
                Object val = data.get(key);
                if (val != null) {
                    config.put(key, val);
                }
            }
        } else {
            // 未知节点类型：排除 UI 字段后全部保留
            for (Map.Entry<String, Object> e : data.entrySet()) {
                if (!UI_KEYS.contains(e.getKey()) && e.getValue() != null) {
                    config.put(e.getKey(), e.getValue());
                }
            }
        }
        return config;
    }

    private static final Set<String> UI_KEYS = Set.of("label", "description", "icon", "color", "position");

    private static List<String> getConfigKeys(String nodeType) {
        return switch (nodeType != null ? nodeType : "") {
            case "llm" -> List.of("model", "modelId", "modelName", "sysPrompt", "promptTemplate", "enableStreaming");
            case "classifier" -> List.of("model", "modelId", "modelName", "inputVariable", "conditions", "mode_switch", "instruction");
            case "retrieval" -> List.of("knowledgeId", "inputVariable", "topK", "threshold", "overrideConfig");
            case "api" -> List.of("url", "method", "timeout", "headers");
            case "script" -> List.of("scriptContent", "scriptLanguage", "codeContent", "code");
            case "variable" -> List.of("variableName", "variableValue");
            case "variable_handle" -> List.of("handleType", "type", "templateContent", "template_content", "groupStrategy", "groups");
            case "condition" -> List.of("conditionGroups", "branches");
            case "tool" -> List.of("toolId");
            case "mcp" -> List.of("mcpServerId", "mcpServerName", "serverName", "toolName");
            case "parameter_extractor" -> List.of("inputVariable", "extractParams", "instruction", "model", "modelId");
            case "input" -> List.of("outputParams", "output_params");
            case "confirm" -> List.of("message", "formFields", "form_fields");
            case "output" -> List.of("output");
            case "app_component" -> List.of("componentCode", "componentType");
            case "batch" -> List.of("concurrentSize");
            case "loop" -> List.of("loopCondition", "maxIterations");
            default -> List.of();
        };
    }

    private static String extractString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? String.valueOf(val) : null;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
