package com.lightbot.service.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.enums.AgentType;
import com.lightbot.workflow.WorkflowExecutorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

        try {
            List<Map<String, Object>> workflowEvents = new ArrayList<>();
            String result = workflowExecutor.execute(
                    ctx.getAgent().getId(),
                    ctx.getSessionId(),
                    ctx.getRequest().getMessage(),
                    workflowEvents
            );

            ctx.getWorkflowEventsList().addAll(workflowEvents);
            ctx.getFullReply().append(result);

            // 序列化 metadata 供 TraceMiddleware 持久化
            Map<String, Object> metadataMap = new LinkedHashMap<>();
            metadataMap.put("workflowEvents", workflowEvents);
            ctx.getRagMetadataHolder()[0] = OBJECT_MAPPER.writeValueAsString(metadataMap);

            // 1. 推送工作流节点事件（STATUS 通道，前端与工具调用同路径解析）
            List<Flux<String>> fluxParts = new ArrayList<>();
            for (Map<String, Object> event : workflowEvents) {
                fluxParts.add(Flux.just(STATUS_PREFIX + OBJECT_MAPPER.writeValueAsString(event)));
            }
            // 2. 推送最终文本结果
            if (result != null && !result.isEmpty()) {
                fluxParts.add(Flux.just(result));
            }
            // 3. 推送 metadata
            fluxParts.add(Flux.just(METADATA_PREFIX + ctx.getRagMetadataHolder()[0]));

            log.info("[WorkflowMiddleware] 工作流执行完成: agentId={}, nodes={}, resultLength={}",
                    ctx.getAgent().getId(), workflowEvents.size(), result != null ? result.length() : 0);

            return Flux.concat(fluxParts);

        } catch (Exception e) {
            log.error("[WorkflowMiddleware] 工作流执行失败: agentId={}, error={}",
                    ctx.getAgent().getId(), e.getMessage(), e);
            return Flux.just("工作流执行失败: " + e.getMessage());
        }
    }
}
