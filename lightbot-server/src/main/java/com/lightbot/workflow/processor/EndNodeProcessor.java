package com.lightbot.workflow.processor;

import com.lightbot.enums.NodeType;
import com.lightbot.workflow.NodeExecutionContext;
import com.lightbot.workflow.NodeExecutionResult;
import com.lightbot.workflow.NodeProcessor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 结束节点处理器
 * <p>工作流的出口节点，返回最终结果</p>
 *
 * @author finch
 * @since 2026-05-24
 */
@Component
public class EndNodeProcessor implements NodeProcessor {

    @Override
    public NodeType getType() {
        return NodeType.END;
    }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext context) {
        // 1. 获取最后一个节点的输出作为最终结果
        Map<String, Object> outputs = new HashMap<>();
        Object lastOutput = context.getNodeOutputs().values().stream()
                .reduce((first, second) -> second)
                .orElse(null);
        outputs.put("result", lastOutput);

        // 2. 如果有 llmOutput，直接作为结果
        if (context.getVariables().containsKey("llmOutput")) {
            outputs.put("result", context.getVariables().get("llmOutput"));
        }

        return NodeExecutionResult.builder()
                .outputs(outputs)
                .finished(true)
                .build();
    }
}