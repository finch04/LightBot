package com.lightbot.workflow.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.enums.NodeType;
import com.lightbot.workflow.NodeExecutionContext;
import com.lightbot.workflow.NodeExecutionResult;
import com.lightbot.workflow.WorkflowSubgraphExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 循环容器节点：按数组/次数迭代执行子图
 */
@Slf4j
@Component
public class LoopNodeProcessor extends AbstractGroupContainerProcessor {

    public LoopNodeProcessor(WorkflowSubgraphExecutor subgraphExecutor, ObjectMapper objectMapper) {
        super(subgraphExecutor, objectMapper);
    }

    @Override
    public NodeType getType() {
        return NodeType.LOOP;
    }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext context) {
        return executeContainer(context, NodeType.LOOP_START, NodeType.LOOP_END, false);
    }
}
