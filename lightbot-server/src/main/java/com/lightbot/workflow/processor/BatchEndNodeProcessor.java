package com.lightbot.workflow.processor;

import com.lightbot.enums.NodeType;
import com.lightbot.workflow.NodeExecutionContext;
import com.lightbot.workflow.NodeExecutionResult;
import com.lightbot.workflow.NodeProcessor;
import org.springframework.stereotype.Component;

/**
 * 并行结束节点：标记单批子图结束
 */
@Component
public class BatchEndNodeProcessor extends AbstractFlowNodeProcessor implements NodeProcessor {

    @Override
    public NodeType getType() {
        return NodeType.BATCH_END;
    }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext context) {
        Object input = context.getVariables().getOrDefault("result", context.getVariables().get("output"));
        return passThrough(context, "result", input);
    }
}
