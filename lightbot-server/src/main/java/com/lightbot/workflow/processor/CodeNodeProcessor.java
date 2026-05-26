package com.lightbot.workflow.processor;

import com.lightbot.enums.NodeType;
import com.lightbot.workflow.NodeExecutionContext;
import com.lightbot.workflow.NodeExecutionResult;
import com.lightbot.workflow.NodeProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 代码节点：复用脚本节点执行逻辑（JavaScript main(params)）
 */
@Component
@RequiredArgsConstructor
public class CodeNodeProcessor extends AbstractFlowNodeProcessor implements NodeProcessor {

    private final ScriptNodeProcessor scriptNodeProcessor;

    @Override
    public NodeType getType() {
        return NodeType.CODE;
    }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext context) {
        Map<String, Object> nodeData = context.getCurrentNodeData();
        if (nodeData == null) {
            return passThrough(context, "result", null);
        }
        Map<String, Object> mappedData = new HashMap<>(nodeData);
        if (!mappedData.containsKey("scriptContent")) {
            Object codeContent = mappedData.get("codeContent");
            if (codeContent == null) {
                codeContent = mappedData.get("code");
            }
            if (codeContent != null) {
                mappedData.put("scriptContent", codeContent);
            }
        }
        if (!mappedData.containsKey("scriptLanguage")) {
            mappedData.putIfAbsent("scriptLanguage", "javascript");
        }

        NodeExecutionContext mappedContext = NodeExecutionContext.builder()
                .currentNodeId(context.getCurrentNodeId())
                .currentNodeData(mappedData)
                .variables(context.getVariables())
                .userInput(context.getUserInput())
                .agent(context.getAgent())
                .workflow(context.getWorkflow())
                .nodeOutputs(context.getNodeOutputs())
                .build();
        return scriptNodeProcessor.execute(mappedContext);
    }
}
