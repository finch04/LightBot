package com.lightbot.workflow.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.enums.NodeType;
import com.lightbot.workflow.NodeExecutionContext;
import com.lightbot.workflow.NodeExecutionResult;
import com.lightbot.workflow.WorkflowSubgraphExecutor;
import org.springframework.stereotype.Component;

/**
 * 并行处理开始节点：触发批处理容器完整执行
 */
@Component
public class BatchStartNodeProcessor extends AbstractGroupContainerProcessor {

    public BatchStartNodeProcessor(WorkflowSubgraphExecutor subgraphExecutor, ObjectMapper objectMapper) {
        super(subgraphExecutor, objectMapper);
    }

    @Override
    public NodeType getType() {
        return NodeType.BATCH_START;
    }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext context) {
        return executeContainer(context, NodeType.BATCH_START, NodeType.BATCH_END, true);
    }
}
