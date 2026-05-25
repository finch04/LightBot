package com.lightbot.workflow.processor;

import com.lightbot.enums.NodeType;
import com.lightbot.workflow.NodeExecutionContext;
import com.lightbot.workflow.NodeExecutionResult;
import com.lightbot.workflow.NodeProcessor;
import com.lightbot.workflow.WorkflowEdge;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 条件分支节点处理器
 * <p>支持 conditionGroups（条件组 + 规则）与画布出口 sourceHandle 联动</p>
 *
 * @author finch
 * @since 2026-05-24
 */
@Slf4j
@Component
public class ConditionNodeProcessor implements NodeProcessor {

    @Override
    public NodeType getType() {
        return NodeType.CONDITION;
    }

    @Override
    @SuppressWarnings("unchecked")
    public NodeExecutionResult execute(NodeExecutionContext context) {
        Map<String, Object> nodeData = context.getCurrentNodeData();
        Map<String, Object> variables = context.getVariables();

        // 1. 优先 conditionGroups（与前端条件组 UI 对齐）
        if (nodeData != null) {
            List<Map<String, Object>> groups = (List<Map<String, Object>>) nodeData.get("conditionGroups");
            if (groups != null && !groups.isEmpty()) {
                for (Map<String, Object> group : groups) {
                    List<Map<String, Object>> rules = (List<Map<String, Object>>) group.get("rules");
                    if (rules == null || rules.isEmpty()) {
                        continue;
                    }
                    if (evaluateGroup(group, variables)) {
                        String handle = group.get("sourceHandle") != null
                                ? group.get("sourceHandle").toString()
                                : null;
                        String next = resolveTargetByHandle(context, handle);
                        log.info("[ConditionNodeProcessor] 条件组命中: nodeId={}, handle={}, next={}",
                                context.getCurrentNodeId(), handle, next);
                        return NodeExecutionResult.builder()
                                .nextNodeId(next)
                                .finished(false)
                                .build();
                    }
                }
                // 否则分支：out_c 或最后一条出边
                String elseNext = resolveTargetByHandle(context, "out_c");
                if (elseNext == null) {
                    elseNext = resolveDefaultOutEdge(context);
                }
                return NodeExecutionResult.builder()
                        .nextNodeId(elseNext)
                        .finished(false)
                        .build();
            }
        }

        // 2. 兼容旧 branches 配置
        if (nodeData != null) {
            List<Map<String, Object>> branches = (List<Map<String, Object>>) nodeData.get("branches");
            if (branches != null && !branches.isEmpty()) {
                for (Map<String, Object> branch : branches) {
                    String condition = branch.get("condition") != null ? branch.get("condition").toString() : null;
                    if (condition == null || condition.isBlank()) {
                        continue;
                    }
                    if (evaluateConditionExpression(condition, variables)) {
                        String handle = branch.get("sourceHandle") != null
                                ? branch.get("sourceHandle").toString()
                                : null;
                        String target = branch.get("targetNodeId") != null
                                ? branch.get("targetNodeId").toString()
                                : null;
                        if (target == null || target.isBlank()) {
                            target = resolveTargetByHandle(context, handle);
                        }
                        return NodeExecutionResult.builder()
                                .nextNodeId(target)
                                .finished(false)
                                .build();
                    }
                }
            }
        }

        String defaultNext = resolveDefaultOutEdge(context);
        return NodeExecutionResult.builder()
                .nextNodeId(defaultNext)
                .finished(false)
                .build();
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
            return resolveDefaultOutEdge(context);
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

    private boolean evaluateConditionExpression(String condition, Map<String, Object> variables) {
        if (variables == null || condition == null) {
            return false;
        }
        try {
            condition = condition.trim();
            if (condition.contains(" AND ")) {
                String[] parts = condition.split(" AND ");
                for (String part : parts) {
                    if (!evaluateConditionExpression(part.trim(), variables)) {
                        return false;
                    }
                }
                return true;
            }
            if (condition.contains(" OR ")) {
                String[] parts = condition.split(" OR ");
                for (String part : parts) {
                    if (evaluateConditionExpression(part.trim(), variables)) {
                        return true;
                    }
                }
                return false;
            }
            if (condition.contains("==")) {
                String[] parts = condition.split("==", 2);
                String varName = resolveVariableKey(parts[0].trim());
                String expectedValue = parts[1].trim();
                Object actualValue = variables.get(varName);
                return expectedValue.equals(String.valueOf(actualValue));
            }
            if (condition.contains("!=")) {
                String[] parts = condition.split("!=", 2);
                String varName = resolveVariableKey(parts[0].trim());
                String expectedValue = parts[1].trim();
                Object actualValue = variables.get(varName);
                return !expectedValue.equals(String.valueOf(actualValue));
            }
            if (condition.contains("not_contains")) {
                String[] parts = condition.split("not_contains", 2);
                String varName = resolveVariableKey(parts[0].trim());
                String searchValue = parts[1].trim();
                Object actualValue = variables.get(varName);
                if (actualValue == null) {
                    return true;
                }
                return !String.valueOf(actualValue).contains(searchValue);
            }
            if (condition.contains("contains")) {
                String[] parts = condition.split("contains", 2);
                String varName = resolveVariableKey(parts[0].trim());
                String searchValue = parts[1].trim();
                Object actualValue = variables.get(varName);
                if (actualValue == null) {
                    return false;
                }
                return String.valueOf(actualValue).contains(searchValue);
            }
            if (variables.containsKey(condition)) {
                Object value = variables.get(condition);
                return Boolean.TRUE.equals(value) || "true".equals(String.valueOf(value));
            }
        } catch (Exception e) {
            log.warn("[ConditionNodeProcessor] 条件评估失败: condition={}, error={}", condition, e.getMessage());
        }
        return false;
    }
}
