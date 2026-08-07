package com.lightbot.workflow.processor;

import com.lightbot.enums.NodeType;
import com.lightbot.workflow.NodeExecutionContext;
import com.lightbot.workflow.NodeExecutionResult;
import com.lightbot.workflow.NodeProcessor;
import com.lightbot.workflow.WorkflowEdge;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 条件分支节点处理器
 * <p>
 * 按 conditionGroups 顺序匹配；命中走 {@code {nodeId}_{groupId}} 出口；
 * 均未命中走 {@code {nodeId}_default} 兜底出口。
 * </p>
 *
 * @author finch
 * @since 2026-05-24
 */
@Slf4j
@Component
public class ConditionNodeProcessor implements NodeProcessor {

    private static final String DEFAULT_HANDLE_SUFFIX = "_default";

    @Override
    public NodeType getType() {
        return NodeType.CONDITION;
    }

    @Override
    @SuppressWarnings("unchecked")
    public NodeExecutionResult execute(NodeExecutionContext context) {
        Map<String, Object> nodeData = context.getCurrentNodeData();
        Map<String, Object> variables = context.getVariables();
        String nodeId = context.getCurrentNodeId();

        if (nodeData != null) {
            List<Map<String, Object>> groups = (List<Map<String, Object>>) nodeData.get("conditionGroups");
            if (groups != null && !groups.isEmpty()) {
                for (Map<String, Object> group : groups) {
                    List<Map<String, Object>> rules = (List<Map<String, Object>>) group.get("rules");
                    if (rules == null || rules.isEmpty()) {
                        continue;
                    }
                    if (!evaluateGroup(group, variables)) {
                        continue;
                    }
                    String handle = resolveGroupHandle(nodeId, group);
                    String next = resolveTargetByHandle(context, handle);
                    log.info("[ConditionNodeProcessor] 条件组命中: nodeId={}, handle={}, next={}",
                            nodeId, handle, next);
                    return routeResult(context, next, handle, resolveGroupLabel(group));
                }
                // 均未命中 → 默认兜底口
                String defaultHandle = defaultHandleId(nodeId);
                String elseNext = resolveTargetByHandle(context, defaultHandle);
                if (elseNext == null) {
                    elseNext = resolveDefaultOutEdge(context);
                }
                return routeResult(context, elseNext, defaultHandle, "都未命中");
            }
        }

        String defaultHandle = defaultHandleId(nodeId);
        String defaultNext = resolveTargetByHandle(context, defaultHandle);
        if (defaultNext == null) {
            defaultNext = resolveDefaultOutEdge(context);
        }
        return routeResult(context, defaultNext, defaultHandle, "都未命中");
    }

    private String resolveGroupHandle(String nodeId, Map<String, Object> group) {
        Object id = group.get("id");
        if (id != null && !String.valueOf(id).isBlank()) {
            return nodeId + "_" + id;
        }
        // 无 id 时退化为顺序无关的不可用口，避免误连
        return nodeId + "_unknown";
    }

    private String defaultHandleId(String nodeId) {
        return nodeId + DEFAULT_HANDLE_SUFFIX;
    }

    private NodeExecutionResult routeResult(NodeExecutionContext context, String nextNodeId,
                                          String matchedHandle, String matchedGroupLabel) {
        Map<String, Object> outputs = new HashMap<>();
        if (matchedHandle != null && !matchedHandle.isBlank()) {
            outputs.put("matchedHandle", matchedHandle);
        }
        if (matchedGroupLabel != null && !matchedGroupLabel.isBlank()) {
            outputs.put("matchedGroupLabel", matchedGroupLabel);
        }
        return NodeExecutionResult.builder()
                .nextNodeId(nextNodeId)
                .outputs(outputs)
                .finished(false)
                .build();
    }

    private String resolveGroupLabel(Map<String, Object> group) {
        if (group == null) {
            return null;
        }
        Object label = group.get("label");
        if (label != null && !String.valueOf(label).isBlank()) {
            return String.valueOf(label);
        }
        Object id = group.get("id");
        return id != null ? String.valueOf(id) : null;
    }

    @SuppressWarnings("unchecked")
    private boolean evaluateGroup(Map<String, Object> group, Map<String, Object> variables) {
        List<Map<String, Object>> rules = (List<Map<String, Object>>) group.get("rules");
        if (rules == null || rules.isEmpty()) {
            return false;
        }
        String relation = group.get("relation") != null ? group.get("relation").toString() : "and";
        boolean isOr = "or".equalsIgnoreCase(relation);
        if (isOr) {
            for (Map<String, Object> rule : rules) {
                if (evaluateRule(rule, variables)) {
                    return true;
                }
            }
            return false;
        }
        for (Map<String, Object> rule : rules) {
            if (!evaluateRule(rule, variables)) {
                return false;
            }
        }
        return true;
    }

    private boolean evaluateRule(Map<String, Object> rule, Map<String, Object> variables) {
        if (rule == null) {
            return false;
        }
        String variable = rule.get("variable") != null ? rule.get("variable").toString() : "";
        String operator = rule.get("operator") != null ? rule.get("operator").toString() : "contains";
        String value = rule.get("value") != null ? rule.get("value").toString() : "";
        String key = resolveVariableKey(variable);
        Object actual = variables != null ? variables.get(key) : null;
        String actualStr = actual != null ? String.valueOf(actual) : "";

        return switch (operator) {
            case "eq" -> actualStr.equals(value);
            case "neq" -> !actualStr.equals(value);
            case "contains" -> actualStr.contains(value);
            case "not_contains" -> !actualStr.contains(value);
            case "empty" -> actualStr.isBlank();
            case "not_empty" -> !actualStr.isBlank();
            default -> actualStr.contains(value);
        };
    }

    private String resolveVariableKey(String variable) {
        if (variable == null) {
            return "";
        }
        String v = variable.trim();
        if (v.startsWith("{{") && v.endsWith("}}")) {
            return v.substring(2, v.length() - 2).trim();
        }
        return v;
    }

    private String resolveTargetByHandle(NodeExecutionContext context, String sourceHandle) {
        if (sourceHandle == null || sourceHandle.isBlank()) {
            return null;
        }
        List<WorkflowEdge> outEdges = context.getWorkflow().getOutEdges(context.getCurrentNodeId());
        for (WorkflowEdge edge : outEdges) {
            if (sourceHandle.equals(edge.getSourceHandle())) {
                return edge.getTarget();
            }
        }
        return null;
    }

    private String resolveDefaultOutEdge(NodeExecutionContext context) {
        List<WorkflowEdge> outEdges = context.getWorkflow().getOutEdges(context.getCurrentNodeId());
        return outEdges.isEmpty() ? null : outEdges.get(0).getTarget();
    }
}
