package com.lightbot.workflow.processor;

import com.lightbot.enums.NodeType;
import com.lightbot.workflow.NodeExecutionContext;
import com.lightbot.workflow.NodeExecutionResult;
import com.lightbot.workflow.NodeProcessor;
import com.lightbot.workflow.WorkflowEdge;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 开始节点处理器
 * <p>工作流的入口节点，将用户输入传递给下一个节点</p>
 *
 * @author finch
 * @since 2026-05-24
 */
@Component
public class StartNodeProcessor implements NodeProcessor {

    @Override
    public NodeType getType() {
        return NodeType.START;
    }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext context) {
        // 1. 获取出边，确定下一个节点
        List<WorkflowEdge> outEdges = context.getWorkflow().getOutEdges(context.getCurrentNodeId());
        String nextNodeId = outEdges.isEmpty() ? null : outEdges.get(0).getTarget();

        // 2. 将用户输入作为节点输出
        Map<String, Object> outputs = new HashMap<>();
        outputs.put("input", context.getUserInput());

        return NodeExecutionResult.builder()
                .nextNodeId(nextNodeId)
                .outputs(outputs)
                .finished(false)
                .build();
    }
}