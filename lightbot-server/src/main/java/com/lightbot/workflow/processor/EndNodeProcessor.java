package com.lightbot.workflow.processor;

import com.lightbot.enums.NodeType;
import com.lightbot.workflow.NodeExecutionContext;
import com.lightbot.workflow.NodeExecutionResult;
import com.lightbot.workflow.NodeProcessor;
import com.lightbot.workflow.WorkflowNode;
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
        Map<String, Object> outputs = new HashMap<>();

        // 1. 优先从 variables 中取 LLM 输出
        Object llmOutput = context.getVariables().get("llmOutput");
        if (llmOutput != null) {
            outputs.put("result", llmOutput);
            return NodeExecutionResult.builder()
                    .outputs(outputs)
                    .finished(true)
                    .build();
        }

        // 2. 遍历工作流节点定义，找到最后一个 LLM 节点的输出
        if (context.getWorkflow() != null && context.getWorkflow().getNodes() != null) {
            for (int i = context.getWorkflow().getNodes().size() - 1; i >= 0; i--) {
                WorkflowNode node = context.getWorkflow().getNodes().get(i);
                if (node.getType() == NodeType.LLM) {
                    Object nodeOutput = context.getNodeOutputs().get(node.getId());
                    if (nodeOutput instanceof Map<?, ?> map && map.containsKey("llmOutput")) {
                        outputs.put("result", map.get("llmOutput"));
                        return NodeExecutionResult.builder()
                                .outputs(outputs)
                                .finished(true)
                                .build();
                    }
                }
            }
        }

        // 3. 优先取 variables 中已有的 result（脚本/代码等节点已设置）
        Object existingResult = context.getVariables().get("result");
        if (existingResult != null) {
            outputs.put("result", existingResult);
            return NodeExecutionResult.builder()
                    .outputs(outputs)
                    .finished(true)
                    .build();
        }

        // 4. 兜底：取最后一个节点的输出
        Object lastOutput = context.getNodeOutputs().values().stream()
                .reduce((first, second) -> second)
                .orElse(null);
        outputs.put("result", lastOutput);

        return NodeExecutionResult.builder()
                .outputs(outputs)
                .finished(true)
                .build();
    }
}
