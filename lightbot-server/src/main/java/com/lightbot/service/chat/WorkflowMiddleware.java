package com.lightbot.service.chat;

import com.lightbot.enums.AgentType;
import com.lightbot.workflow.WorkflowExecutorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

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

    @Override
    public Flux<String> execute(ChatContext ctx, ChatMiddlewareChain next) {
        // 1. 检查是否为 WORKFLOW 类型
        if (ctx.getAgent() == null || ctx.getAgent().getAgentType() != AgentType.WORKFLOW) {
            // 非 WORKFLOW 类型，继续执行后续中间件
            return next.proceed(ctx);
        }

        // 2. WORKFLOW 类型，执行工作流
        log.info("[WorkflowMiddleware] 开始执行工作流: agentId={}, sessionId={}",
                ctx.getAgent().getId(), ctx.getSessionId());

        try {
            String result = workflowExecutor.execute(
                    ctx.getAgent().getId(),
                    ctx.getSessionId(),
                    ctx.getRequest().getMessage()
            );

            // 3. 返回工作流执行结果
            log.info("[WorkflowMiddleware] 工作流执行完成: agentId={}, resultLength={}",
                    ctx.getAgent().getId(), result.length());

            // 将结果设置到 fullReply
            ctx.getFullReply().append(result);

            // 返回单条流式消息（工作流不支持真正的流式，直接返回完整结果）
            return Flux.just(result);

        } catch (Exception e) {
            log.error("[WorkflowMiddleware] 工作流执行失败: agentId={}, error={}",
                    ctx.getAgent().getId(), e.getMessage(), e);
            return Flux.just("工作流执行失败: " + e.getMessage());
        }
    }
}