package com.lightbot.service.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.enums.AgentType;
import com.lightbot.enums.MessageRole;
import com.lightbot.service.AgentVersionService;
import com.lightbot.util.SensitiveWordFilter;
import com.lightbot.workflow.WorkflowDefinition;
import com.lightbot.workflow.WorkflowExecutorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final TaskExecutor taskExecutor;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
            try {
                List<Map<String, Object>> workflowEvents = new ArrayList<>();
                Consumer<Map<String, Object>> emit = event -> {
                    ctx.getWorkflowEventsList().add(event);
                    try {
                        sink.next(STATUS_PREFIX + OBJECT_MAPPER.writeValueAsString(event));
                    } catch (Exception ex) {
                        sink.error(ex);
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
                            emit
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
                if (ctx.getRequestId() != null && !ctx.getRequestId().isBlank()) {
                    metadataMap.put("requestId", ctx.getRequestId());
                }
                if (ctx.getRequest().getConfigVersion() != null) {
                    metadataMap.put("configVersion", ctx.getRequest().getConfigVersion());
                }
                ctx.getRagMetadataHolder()[0] = OBJECT_MAPPER.writeValueAsString(metadataMap);

                if (result != null && !result.isEmpty()) {
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
                for (String key : List.of("result", "output", "text", "answer")) {
                    Object val = outputMap.get(key);
                    if (val != null && !val.toString().isBlank()) {
                        return val.toString();
                    }
                }
                return outputMap.values().iterator().next().toString();
            }
        }
        return "工作流已执行完成";
    }
}
