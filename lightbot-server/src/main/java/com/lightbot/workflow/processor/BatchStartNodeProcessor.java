package com.lightbot.workflow.processor;

import com.lightbot.enums.NodeType;
import com.lightbot.workflow.NodeExecutionContext;
import com.lightbot.workflow.NodeExecutionResult;
import com.lightbot.workflow.NodeProcessor;
import org.springframework.stereotype.Component;

/**
 * 并行处理开始节点：仅作为子图内部的起始标记，不做任何业务处理
 * <p>容器的完整执行由 {@link BatchNodeProcessor} 负责，此节点仅在子图迭代中被跳过</p>
 *
 * @author finch
 * @since 2026-06-15
 */
@Component
public class BatchStartNodeProcessor extends AbstractFlowNodeProcessor implements NodeProcessor {

    @Override
    public NodeType getType() {
        return NodeType.BATCH_START;
    }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext context) {
        return passThrough(context, "output", "success");
    }
}
