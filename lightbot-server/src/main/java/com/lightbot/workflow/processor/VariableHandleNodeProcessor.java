package com.lightbot.workflow.processor;

import com.lightbot.enums.NodeType;
import com.lightbot.workflow.NodeExecutionContext;
import com.lightbot.workflow.NodeExecutionResult;
import com.lightbot.workflow.NodeProcessor;
import com.lightbot.workflow.WorkflowNodeDataUtils;
import com.lightbot.workflow.WorkflowPromptUtils;
import com.lightbot.workflow.WorkflowVariableUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 变量处理节点：模板渲染 / 分组聚合
 */
@Slf4j
@Component
public class VariableHandleNodeProcessor extends AbstractFlowNodeProcessor implements NodeProcessor {

    @Override
    public NodeType getType() {
        return NodeType.VARIABLE_HANDLE;
    }

    @Override
    @SuppressWarnings("unchecked")
    public NodeExecutionResult execute(NodeExecutionContext context) {
        Map<String, Object> nodeData = context.getCurrentNodeData() != null
                ? context.getCurrentNodeData() : Map.of();
        Map<String, Object> variables = context.getVariables();

        String handleType = WorkflowNodeDataUtils.parseString(nodeData.get("handleType"));
        if (handleType == null) {
            handleType = WorkflowNodeDataUtils.parseString(nodeData.get("type"));
        }
        if (handleType == null) {
            handleType = "template";
        }

        Map<String, Object> outputs = new HashMap<>();
        Object primaryOutput;

        if ("group".equalsIgnoreCase(handleType)) {
            String groupStrategy = WorkflowNodeDataUtils.parseString(nodeData.get("groupStrategy"));
            if (groupStrategy == null) {
                groupStrategy = "firstNotNull";
            }
            List<Map<String, Object>> groups = (List<Map<String, Object>>) nodeData.get("groups");
            Map<String, Object> groupResult = buildGroupResult(groups, groupStrategy, variables);
            outputs.putAll(groupResult);
            primaryOutput = groupResult.isEmpty() ? null : groupResult.values().iterator().next();
        } else {
            String template = WorkflowNodeDataUtils.parseString(nodeData.get("templateContent"));
            if (template == null) {
                template = WorkflowNodeDataUtils.parseString(nodeData.get("template_content"));
            }
            if (template == null || template.isBlank()) {
                throw new IllegalArgumentException("变量处理节点模板内容不能为空");
            }
            primaryOutput = WorkflowPromptUtils.render(template, variables);
            outputs.put("output", primaryOutput);
        }

        if (primaryOutput != null && !outputs.containsKey("output")) {
            outputs.put("output", primaryOutput);
        }

        log.info("[VariableHandleNodeProcessor] 处理完成: type={}, outputKeys={}", handleType, outputs.keySet());

        return NodeExecutionResult.builder()
                .nextNodeId(resolveNextNodeId(context))
                .outputs(outputs)
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildGroupResult(List<Map<String, Object>> groups,
                                                 String groupStrategy,
                                                 Map<String, Object> variables) {
        Map<String, Object> result = new HashMap<>();
        if (groups == null || groups.isEmpty()) {
            return result;
        }
        boolean lastNotNull = "lastNotNull".equalsIgnoreCase(groupStrategy);
        for (Map<String, Object> group : groups) {
            if (group == null) {
                continue;
            }
            String groupName = WorkflowNodeDataUtils.parseString(group.get("groupName"));
            if (groupName == null) {
                groupName = WorkflowNodeDataUtils.parseString(group.get("group_name"));
            }
            if (groupName == null) {
                groupName = "output";
            }
            List<Map<String, Object>> groupVars = (List<Map<String, Object>>) group.get("variables");
            if (groupVars == null || groupVars.isEmpty()) {
                continue;
            }
            Object selected = null;
            if (lastNotNull) {
                for (Map<String, Object> variable : groupVars) {
                    Object value = resolveGroupVariable(variable, variables);
                    if (value != null) {
                        selected = value;
                    }
                }
            } else {
                for (Map<String, Object> variable : groupVars) {
                    Object value = resolveGroupVariable(variable, variables);
                    if (value != null) {
                        selected = value;
                        break;
                    }
                }
            }
            if (selected != null) {
                result.put(groupName, selected);
            }
        }
        return result;
    }

    private Object resolveGroupVariable(Map<String, Object> variable, Map<String, Object> variables) {
        if (variable == null) {
            return null;
        }
        Object raw = variable.get("value");
        if (raw == null) {
            return null;
        }
        Object resolved = WorkflowVariableUtils.resolveValue(raw.toString(), variables);
        if (resolved == null || String.valueOf(resolved).isBlank()) {
            return null;
        }
        return resolved;
    }
}
