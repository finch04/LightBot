package com.lightbot.service.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.enums.AgentType;
import com.lightbot.workflow.WorkflowExecutorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * <p>WORKFLOW 类型 Agent 执行工作流 DAG，跳过后续中间件</p>
 *
 * @author finch
 * @since 2026-05-24
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowMiddleware implements ChatMiddleware {

    private final WorkflowExecutorService workflowExecutor;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public Flux<String> execute(ChatContext ctx, ChatMiddlewareChain next) {
        if (ctx.getAgent() == null || ctx.getAgent().getAgentType() != AgentType.WORKFLOW) {
            return next.proceed(ctx);
        }

        log.info("[WorkflowMiddleware] 开始执行工作流: agentId={}, sessionId={}",
                ctx.getAgent().getId(), ctx.getSessionId());

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

                String result = workflowExecutor.execute(
                        ctx.getAgent().getId(),
                        ctx.getSessionId(),
                        ctx.getRequest().getMessage(),
                        workflowEvents,
                        emit
                );

                if (result != null) {
                    ctx.getFullReply().append(result);
                }

                Map<String, Object> metadataMap = new LinkedHashMap<>();
                metadataMap.put("workflowEvents", workflowEvents);
                ctx.getRagMetadataHolder()[0] = OBJECT_MAPPER.writeValueAsString(metadataMap);

                if (result != null && !result.isEmpty()) {
                    sink.next(result);
                }
                sink.next(METADATA_PREFIX + ctx.getRagMetadataHolder()[0]);
                sink.complete();

                log.info("[WorkflowMiddleware] 工作流执行完成: agentId={}, nodes={}, resultLength={}",
                        ctx.getAgent().getId(), workflowEvents.size(), result != null ? result.length() : 0);
            } catch (Exception e) {
                log.error("[WorkflowMiddleware] 工作流执行失败: agentId={}, error={}",
                        ctx.getAgent().getId(), e.getMessage(), e);
                sink.next("工作流执行失败: " + e.getMessage());
                sink.complete();
            }
        })).subscribeOn(Schedulers.boundedElastic());
    }
}
