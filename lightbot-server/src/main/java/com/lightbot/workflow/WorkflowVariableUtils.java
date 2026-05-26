package com.lightbot.workflow;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 工作流变量解析工具
 */
public final class WorkflowVariableUtils {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{([^}]+)}}");

    private WorkflowVariableUtils() {
    }

    /**
     * 从 {{var}} 表达式解析变量值；非引用表达式则按模板渲染
     */
    public static Object resolveValue(String expression, Map<String, Object> variables) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        String trimmed = expression.trim();
        Matcher matcher = VAR_PATTERN.matcher(trimmed);
        if (matcher.matches()) {
            return getNestedValue(variables, matcher.group(1).trim());
        }
        String rendered = WorkflowPromptUtils.render(trimmed, variables);
        return rendered.equals(trimmed) ? trimmed : rendered;
    }

    /**
     * 解析文本变量，支持 fallback
     */
    public static String resolveText(String expression, Map<String, Object> variables, String fallback) {
        Object value = resolveValue(expression, variables);
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback;
        }
        return String.valueOf(value).trim();
    }

    /**
     * 从节点 inputVariable 配置解析输入文本
     */
    public static String resolveInputText(String inputVariable, Map<String, Object> variables, String userInput) {
        String expr = inputVariable != null && !inputVariable.isBlank() ? inputVariable : "{{input}}";
        String rendered = WorkflowPromptUtils.render(expr, variables);
        if (rendered != null && !rendered.isBlank() && !rendered.equals(expr)) {
            return rendered.trim();
        }
        Object resolved = resolveValue(expr, variables);
        if (resolved != null && !String.valueOf(resolved).isBlank()) {
            return String.valueOf(resolved).trim();
        }
        if (variables != null) {
            Object query = variables.get("query");
            if (query != null && !String.valueOf(query).isBlank()) {
                return String.valueOf(query).trim();
            }
            Object input = variables.get("input");
            if (input != null && !String.valueOf(input).isBlank()) {
                return String.valueOf(input).trim();
            }
        }
        return userInput != null ? userInput.trim() : (rendered != null ? rendered.trim() : "");
    }

    /**
     * 提取 {{key}} 中的 key
     */
    public static String extractVarKey(String expression) {
        if (expression == null) {
            return null;
        }
        Matcher matcher = VAR_PATTERN.matcher(expression.trim());
        return matcher.matches() ? matcher.group(1).trim() : null;
    }

    /**
     * 按点号路径读取嵌套变量（仅支持 Map 一层嵌套）
     */
    @SuppressWarnings("unchecked")
    public static Object getNestedValue(Map<String, Object> variables, String path) {
        if (variables == null || path == null || path.isBlank()) {
            return null;
        }
        if (!path.contains(".")) {
            return variables.get(path.trim());
        }
        String[] parts = path.split("\\.");
        Object current = variables.get(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = ((Map<String, Object>) map).get(parts[i]);
        }
        return current;
    }

    /**
     * 从 LLM 原始响应中提取 JSON 对象字符串
     */
    public static String extractJsonObject(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.trim();
        if (text.startsWith("```")) {
            int start = text.indexOf('{');
            int end = text.lastIndexOf('}');
            if (start >= 0 && end > start) {
                return text.substring(start, end + 1);
            }
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }
}
