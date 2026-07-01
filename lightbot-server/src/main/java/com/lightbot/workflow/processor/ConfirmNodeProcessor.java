package com.lightbot.workflow.processor;

import com.lightbot.enums.NodeType;
import com.lightbot.workflow.NodeExecutionContext;
import com.lightbot.workflow.NodeExecutionResult;
import com.lightbot.workflow.NodeProcessor;
import com.lightbot.workflow.WorkflowNodeDataUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 人工确认节点：执行时挂起工作流，等待用户填写表单后 resume 继续
 */
@Slf4j
@Component
public class ConfirmNodeProcessor extends AbstractFlowNodeProcessor implements NodeProcessor {

    @Override
    public NodeType getType() {
        return NodeType.CONFIRM;
    }

    @Override
    @SuppressWarnings("unchecked")
    public NodeExecutionResult execute(NodeExecutionContext context) {
        Map<String, Object> nodeData = context.getCurrentNodeData() != null
                ? context.getCurrentNodeData() : Map.of();

        String message = WorkflowNodeDataUtils.parseString(nodeData.get("message"));
        if (message == null || message.isBlank()) {
            message = "请填写以下信息并确认后继续";
        }

        List<Map<String, Object>> formFields = resolveFormFields(nodeData);

        Map<String, Object> suspendPayload = new HashMap<>();
        suspendPayload.put("nodeId", context.getCurrentNodeId());
        suspendPayload.put("message", message);
        suspendPayload.put("formFields", formFields);

        log.info("[ConfirmNodeProcessor] 挂起等待人工确认: nodeId={}, fields={}",
                context.getCurrentNodeId(), formFields.size());

        return NodeExecutionResult.builder()
                .suspended(true)
                .nextNodeId(resolveNextNodeId(context))
                .suspendPayload(suspendPayload)
                .outputs(Map.of())
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> resolveFormFields(Map<String, Object> nodeData) {
        Object raw = nodeData.get("formFields");
        if (raw == null) {
            raw = nodeData.get("form_fields");
        }
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return defaultFormFields();
        }
        List<Map<String, Object>> fields = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> field = new HashMap<>();
            String key = WorkflowNodeDataUtils.parseString(map.get("key"));
            if (key == null || key.isBlank()) {
                continue;
            }
            field.put("key", key);
            field.put("label", WorkflowNodeDataUtils.parseString(map.get("label")));
            field.put("type", normalizeFieldType(map.get("type")));
            field.put("required", Boolean.TRUE.equals(map.get("required")));
            Object defaultValue = map.get("defaultValue");
            if (defaultValue == null) {
                defaultValue = map.get("default_value");
            }
            if (defaultValue != null) {
                field.put("defaultValue", defaultValue);
            }
            Object options = map.get("options");
            if (options instanceof List<?> optList && !optList.isEmpty()) {
                field.put("options", optList);
            }
            fields.add(field);
        }
        return fields.isEmpty() ? defaultFormFields() : fields;
    }

    private List<Map<String, Object>> defaultFormFields() {
        List<Map<String, Object>> fields = new ArrayList<>();
        Map<String, Object> confirm = new HashMap<>();
        confirm.put("key", "confirmed");
        confirm.put("label", "确认继续");
        confirm.put("type", "select");
        confirm.put("required", true);
        confirm.put("options", List.of("是", "否"));
        fields.add(confirm);
        return fields;
    }

    private String normalizeFieldType(Object type) {
        String t = type != null ? String.valueOf(type).trim().toLowerCase() : "text";
        return switch (t) {
            case "textarea", "number", "select" -> t;
            default -> "text";
        };
    }
}
