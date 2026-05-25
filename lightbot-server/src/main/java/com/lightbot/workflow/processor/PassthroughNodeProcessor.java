package com.lightbot.workflow.processor;

import com.lightbot.enums.NodeType;
import com.lightbot.workflow.NodeExecutionContext;
import com.lightbot.workflow.NodeExecutionResult;
import com.lightbot.workflow.NodeProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 占位节点处理器：API / 循环 / 变量 / 意图分类 / 批处理 / MCP / 脚本 / 代码 / 工具
 * <p>当前实现为透传到下一节点，后续可逐步补全业务逻辑</p>
 */
@Slf4j
public abstract class PassthroughNodeProcessor extends AbstractFlowNodeProcessor implements NodeProcessor {

    @Override
    public NodeExecutionResult execute(NodeExecutionContext context) {
        Object input = context.getVariables().getOrDefault("input", context.getUserInput());
        log.info("[PassthroughNodeProcessor] 节点透传: type={}, nodeId={}", getType(), context.getCurrentNodeId());
        return passThrough(context, "output", input);
    }

    @Component
    public static class ApiNodeProcessor extends PassthroughNodeProcessor {
        @Override public NodeType getType() { return NodeType.API; }
    }

    @Component
    public static class LoopNodeProcessor extends PassthroughNodeProcessor {
        @Override public NodeType getType() { return NodeType.LOOP; }
    }

    @Component
    public static class VariableNodeProcessor extends PassthroughNodeProcessor {

        @Override
        public NodeExecutionResult execute(NodeExecutionContext context) {
            Map<String, Object> nodeData = context.getCurrentNodeData();
            if (nodeData != null && nodeData.get("variableName") != null) {
                String name = nodeData.get("variableName").toString();
                Object value = nodeData.get("variableValue");
                context.getVariables().put(name, value);
            }
            return super.execute(context);
        }

        @Override public NodeType getType() { return NodeType.VARIABLE; }
    }

    @Component
    public static class ClassifierNodeProcessor extends PassthroughNodeProcessor {
        @Override public NodeType getType() { return NodeType.CLASSIFIER; }
    }

    @Component
    public static class BatchNodeProcessor extends PassthroughNodeProcessor {
        @Override public NodeType getType() { return NodeType.BATCH; }
    }

    @Component
    public static class McpNodeProcessor extends PassthroughNodeProcessor {
        @Override public NodeType getType() { return NodeType.MCP; }
    }

    @Component
    public static class ScriptNodeProcessor extends PassthroughNodeProcessor {
        @Override public NodeType getType() { return NodeType.SCRIPT; }
    }

    @Component
    public static class CodeNodeProcessor extends PassthroughNodeProcessor {
        @Override public NodeType getType() { return NodeType.CODE; }
    }

    @Component
    public static class ToolNodeProcessor extends PassthroughNodeProcessor {
        @Override public NodeType getType() { return NodeType.TOOL; }
    }

    @Component
    public static class InputNodeProcessor extends PassthroughNodeProcessor {
        @Override public NodeType getType() { return NodeType.INPUT; }
    }

    @Component
    public static class VariableHandleNodeProcessor extends PassthroughNodeProcessor {
        @Override public NodeType getType() { return NodeType.VARIABLE_HANDLE; }
    }

    @Component
    public static class ParameterExtractorNodeProcessor extends PassthroughNodeProcessor {
        @Override public NodeType getType() { return NodeType.PARAMETER_EXTRACTOR; }
    }

    @Component
    public static class AppComponentNodeProcessor extends PassthroughNodeProcessor {
        @Override public NodeType getType() { return NodeType.APP_COMPONENT; }
    }
}
