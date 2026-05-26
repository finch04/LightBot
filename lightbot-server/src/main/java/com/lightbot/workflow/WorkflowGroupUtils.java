package com.lightbot.workflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.enums.NodeType;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 循环/批处理容器子图工具
 */
@Slf4j
public final class WorkflowGroupUtils {

    private static final int MAX_ITERATION_LIMIT = 500;

    private WorkflowGroupUtils() {
    }

    public static String getParentNodeId(WorkflowNode node) {
        if (node == null) {
            return null;
        }
        return node.getParentNode();
    }

    public static List<WorkflowNode> getGroupChildren(WorkflowDefinition workflow, String groupId) {
        if (workflow == null || workflow.getNodes() == null || groupId == null) {
            return List.of();
        }
        return workflow.getNodes().stream()
                .filter(n -> groupId.equals(getParentNodeId(n)))
                .collect(Collectors.toList());
    }

    public static Set<String> getGroupChildIds(WorkflowDefinition workflow, String groupId) {
        return getGroupChildren(workflow, groupId).stream()
                .map(WorkflowNode::getId)
                .collect(Collectors.toSet());
    }

    public static WorkflowNode findGroupContainer(WorkflowDefinition workflow, String groupId) {
        return workflow != null ? workflow.getNode(groupId) : null;
    }

    public static WorkflowNode findBuiltinNode(WorkflowDefinition workflow, String groupId, NodeType type) {
        return getGroupChildren(workflow, groupId).stream()
                .filter(n -> n.getType() == type)
                .findFirst()
                .orElse(null);
    }

  /**
     * 提取容器内子图（仅包含子节点及内部连线）
     */
    public static WorkflowDefinition buildSubDefinition(WorkflowDefinition workflow, String groupId) {
        Set<String> childIds = getGroupChildIds(workflow, groupId);
        List<WorkflowNode> subNodes = workflow.getNodes().stream()
                .filter(n -> childIds.contains(n.getId()))
                .collect(Collectors.toList());
        List<WorkflowEdge> subEdges = workflow.getEdges() == null ? List.of()
                : workflow.getEdges().stream()
                .filter(e -> childIds.contains(e.getSource()) && childIds.contains(e.getTarget()))
                .collect(Collectors.toList());
        return new WorkflowDefinition(subNodes, subEdges, workflow.getGlobalConfig());
    }

    /**
     * 从内置结束节点找到主流程下一节点
     */
    public static String findExitNodeAfterEnd(WorkflowDefinition workflow, String endNodeId, String groupId) {
        if (workflow.getEdges() == null) {
            return null;
        }
        for (WorkflowEdge edge : workflow.getOutEdges(endNodeId)) {
            WorkflowNode target = workflow.getNode(edge.getTarget());
            if (target == null) {
                continue;
            }
            String parent = getParentNodeId(target);
            if (parent == null || !groupId.equals(parent)) {
                return edge.getTarget();
            }
        }
        return null;
    }

    /**
     * 内置开始节点后的第一个子图节点
     */
    public static String findFirstInnerNode(WorkflowDefinition workflow, String startNodeId, String groupId) {
        Set<String> childIds = getGroupChildIds(workflow, groupId);
        for (WorkflowEdge edge : workflow.getOutEdges(startNodeId)) {
            if (childIds.contains(edge.getTarget())) {
                WorkflowNode target = workflow.getNode(edge.getTarget());
                if (target != null && target.getType() != NodeType.LOOP_END
                        && target.getType() != NodeType.BATCH_END) {
                    return edge.getTarget();
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, List<Object>> buildItemListMap(Map<String, Object> containerData,
                                                             Map<String, Object> variables,
                                                             ObjectMapper objectMapper) {
        Map<String, List<Object>> result = new HashMap<>();
        Object inputParams = containerData.get("input_params");
        if (inputParams == null) {
            inputParams = containerData.get("inputParams");
        }
        if (inputParams instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> row)) {
                    continue;
                }
                String key = row.get("key") != null ? row.get("key").toString() : null;
                if (key == null || key.isBlank()) {
                    continue;
                }
                Object raw = row.get("value");
                Object resolved = WorkflowVariableUtils.resolveValue(
                        raw != null ? raw.toString() : null, variables);
                result.put(key, toObjectList(resolved, objectMapper));
            }
        }
        if (result.isEmpty()) {
            String arrayVar = WorkflowNodeDataUtils.parseString(containerData.get("arrayVariable"));
            if (arrayVar == null) {
                Object params = containerData.get("input_params");
                if (params instanceof List<?> pl && !pl.isEmpty() && pl.get(0) instanceof Map<?, ?> first) {
                    Object val = first.get("value");
                    if (val != null) {
                        arrayVar = val.toString();
                    }
                }
            }
            Object resolved = WorkflowVariableUtils.resolveValue(arrayVar, variables);
            result.put("item", toObjectList(resolved, objectMapper));
        }
        return result;
    }

    public static int resolveLoopCount(Map<String, Object> containerData, Map<String, List<Object>> itemListMap) {
        String iteratorType = WorkflowNodeDataUtils.parseString(containerData.get("iterator_type"));
        if (iteratorType == null) {
            iteratorType = WorkflowNodeDataUtils.parseString(containerData.get("iteratorType"));
        }
        if (iteratorType == null) {
            iteratorType = "byArray";
        }
        if ("byCount".equalsIgnoreCase(iteratorType)) {
            int count = 100;
            Object limit = containerData.get("count_limit");
            if (limit == null) {
                limit = containerData.get("countLimit");
            }
            if (limit instanceof Number number) {
                count = number.intValue();
            } else if (limit != null) {
                try {
                    count = Integer.parseInt(limit.toString());
                } catch (NumberFormatException ignored) {
                }
            }
            return Math.min(Math.max(count, 0), MAX_ITERATION_LIMIT);
        }
        if (itemListMap.isEmpty()) {
            return 0;
        }
        int max = MAX_ITERATION_LIMIT;
        for (List<Object> list : itemListMap.values()) {
            max = Math.min(max, list.size());
        }
        return max;
    }

    public static int resolveBatchCount(Map<String, Object> containerData, Map<String, List<Object>> itemListMap) {
        if (itemListMap.isEmpty()) {
            return 0;
        }
        int maxIndex = MAX_ITERATION_LIMIT;
        for (List<Object> list : itemListMap.values()) {
            maxIndex = Math.min(maxIndex, list.size());
        }
        int batchCap = 200;
        Object bs = containerData.get("batch_size");
        if (bs == null) {
            bs = containerData.get("batchSize");
        }
        if (bs instanceof Number number && number.intValue() > 0) {
            batchCap = Math.min(number.intValue(), MAX_ITERATION_LIMIT);
        }
        return Math.min(maxIndex, batchCap);
    }

    public static int resolveConcurrentSize(Map<String, Object> containerData) {
        Object cs = containerData.get("concurrent_size");
        if (cs == null) {
            cs = containerData.get("concurrentSize");
        }
        int concurrent = 5;
        if (cs instanceof Number number) {
            concurrent = number.intValue();
        } else if (cs != null) {
            try {
                concurrent = Integer.parseInt(cs.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return Math.min(Math.max(concurrent, 1), 10);
    }

    public static boolean isContinueOnError(Map<String, Object> containerData) {
        String strategy = WorkflowNodeDataUtils.parseString(containerData.get("error_strategy"));
        if (strategy == null) {
            strategy = WorkflowNodeDataUtils.parseString(containerData.get("errorStrategy"));
        }
        return strategy == null || "continueOnError".equalsIgnoreCase(strategy);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> buildIterationOverlay(String groupId, int index,
                                                            Map<String, List<Object>> itemListMap,
                                                            Map<String, Object> variables) {
        Map<String, Object> overlay = new HashMap<>();
        overlay.put("index", index + 1);
        for (Map.Entry<String, List<Object>> entry : itemListMap.entrySet()) {
            List<Object> list = entry.getValue();
            if (index < list.size()) {
                overlay.put(entry.getKey(), list.get(index));
            }
        }
        Map<String, Object> scoped = new HashMap<>(overlay);
        scoped.putAll(variables);
        overlay.put(groupId, scoped);
        return overlay;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> aggregateOutputs(Map<String, Object> containerData,
                                                       List<Map<String, Object>> iterationSnapshots) {
        Map<String, Object> outputs = new HashMap<>();
        Object outputParams = containerData.get("output_params");
        if (outputParams == null) {
            outputParams = containerData.get("outputParams");
        }
        List<String> keys = new ArrayList<>();
        if (outputParams instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> row && row.get("key") != null) {
                    keys.add(row.get("key").toString());
                }
            }
        }
        if (keys.isEmpty()) {
            keys.add("result");
        }
        for (String key : keys) {
            List<Object> collected = new ArrayList<>();
            for (Map<String, Object> snapshot : iterationSnapshots) {
                if (snapshot.containsKey(key)) {
                    collected.add(snapshot.get(key));
                } else if (snapshot.get("variables") instanceof Map<?, ?> vars) {
                    Object val = ((Map<String, Object>) vars).get(key);
                    if (val != null) {
                        collected.add(val);
                    }
                }
            }
            outputs.put(key, collected);
        }
        outputs.put("iterations", iterationSnapshots.size());
        return outputs;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> toObjectList(Object value, ObjectMapper objectMapper) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return new ArrayList<>((List<Object>) list);
        }
        if (value instanceof String str) {
            String trimmed = str.trim();
            if (trimmed.startsWith("[")) {
                try {
                    return objectMapper.readValue(trimmed, new TypeReference<List<Object>>() {});
                } catch (Exception e) {
                    log.warn("[WorkflowGroupUtils] 解析数组失败: {}", e.getMessage());
                }
            }
            return List.of(str);
        }
        return List.of(value);
    }
}
