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
 * <p>根据条件表达式选择下一个节点</p>
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
    public NodeExecutionResult execute(NodeExecutionContext context) {
        // 1. 从节点数据获取条件分支配置
        Map<String, Object> nodeData = context.getCurrentNodeData();
        if (nodeData == null) {
            log.warn("[ConditionNodeProcessor] 节点数据为空: nodeId={}", context.getCurrentNodeId());
            // 默认选择第一条出边
            List<WorkflowEdge> outEdges = context.getWorkflow().getOutEdges(context.getCurrentNodeId());
            String defaultNext = outEdges.isEmpty() ? null : outEdges.get(0).getTarget();
            return NodeExecutionResult.builder()
                    .nextNodeId(defaultNext)
                    .finished(false)
                    .build();
        }

        // 2. 获取分支配置
        List<Map<String, Object>> branches = (List<Map<String, Object>>) nodeData.get("branches");
        if (branches == null || branches.isEmpty()) {
            log.warn("[ConditionNodeProcessor] 无分支配置: nodeId={}", context.getCurrentNodeId());
            List<WorkflowEdge> outEdges = context.getWorkflow().getOutEdges(context.getCurrentNodeId());
            String defaultNext = outEdges.isEmpty() ? null : outEdges.get(0).getTarget();
            return NodeExecutionResult.builder()
                    .nextNodeId(defaultNext)
                    .finished(false)
                    .build();
        }

        // 3. 评估条件，选择分支
        String selectedNodeId = evaluateBranches(branches, context.getVariables());

        // 4. 如果没有匹配的分支，使用默认分支
        if (selectedNodeId == null) {
            List<WorkflowEdge> outEdges = context.getWorkflow().getOutEdges(context.getCurrentNodeId());
            selectedNodeId = outEdges.isEmpty() ? null : outEdges.get(0).getTarget();
            log.info("[ConditionNodeProcessor] 无匹配分支，使用默认: nodeId={}", selectedNodeId);
        }

        log.info("[ConditionNodeProcessor] 条件评估完成: nodeId={}, selected={}",
                context.getCurrentNodeId(), selectedNodeId);

        return NodeExecutionResult.builder()
                .nextNodeId(selectedNodeId)
                .finished(false)
                .build();
    }

    /**
     * 评估分支条件
     *
     * @param branches  分支配置列表
     * @param variables 当前变量
     * @return 匹配的目标节点 ID
     */
    private String evaluateBranches(List<Map<String, Object>> branches, Map<String, Object> variables) {
        for (Map<String, Object> branch : branches) {
            String condition = (String) branch.get("condition");
            String targetNodeId = (String) branch.get("targetNodeId");

            if (condition == null || targetNodeId == null) {
                continue;
            }

            // 简单条件评估：支持变量比较
            if (evaluateCondition(condition, variables)) {
                return targetNodeId;
            }
        }
        return null;
    }

    /**
     * 评估单个条件表达式
     * <p>支持简单表达式：variable == value、variable != value、variable contains value</p>
     */
    private boolean evaluateCondition(String condition, Map<String, Object> variables) {
        if (variables == null || condition == null) {
            return false;
        }

        try {
            // 解析条件表达式
            condition = condition.trim();

            // == 判断
            if (condition.contains("==")) {
                String[] parts = condition.split("==");
                String varName = parts[0].trim();
                String expectedValue = parts[1].trim();
                Object actualValue = variables.get(varName);
                return expectedValue.equals(String.valueOf(actualValue));
            }

            // != 判断
            if (condition.contains("!=")) {
                String[] parts = condition.split("!=");
                String varName = parts[0].trim();
                String expectedValue = parts[1].trim();
                Object actualValue = variables.get(varName);
                return !expectedValue.equals(String.valueOf(actualValue));
            }

            // contains 判断
            if (condition.contains("contains")) {
                String[] parts = condition.split("contains");
                String varName = parts[0].trim();
                String searchValue = parts[1].trim();
                Object actualValue = variables.get(varName);
                if (actualValue == null) {
                    return false;
                }
                return String.valueOf(actualValue).contains(searchValue);
            }

            // 默认：直接判断变量是否为 true
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