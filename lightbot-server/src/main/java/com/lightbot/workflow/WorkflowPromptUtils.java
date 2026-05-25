package com.lightbot.workflow;

import java.util.Map;

/**
 * 工作流 Prompt 模板渲染
 */
public final class WorkflowPromptUtils {

    private WorkflowPromptUtils() {
    }

    public static String render(String template, Map<String, Object> variables) {
        if (template == null) {
            return "";
        }
        String result = template;
        if (variables != null) {
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                String placeholder = "{{" + entry.getKey() + "}}";
                String value = entry.getValue() != null ? entry.getValue().toString() : "";
                result = result.replace(placeholder, value);
            }
        }
        return result;
    }
}
