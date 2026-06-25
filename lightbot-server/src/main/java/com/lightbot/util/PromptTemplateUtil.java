package com.lightbot.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 提示词模板变量替换（{{var}} 占位符）
 */
public final class PromptTemplateUtil {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{([^{}]+?)}}");

    private PromptTemplateUtil() {
    }

    /**
     * 将模板中的 {{key}} 替换为变量值
     */
    public static String render(String template, Map<String, ?> variables) {
        if (template == null || template.isBlank()) {
            return template;
        }
        if (variables == null || variables.isEmpty()) {
            return template;
        }
        Matcher matcher = VAR_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object val = variables.get(key);
            String replacement = val != null ? String.valueOf(val) : "";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 合并变量默认值与 biz_params（请求传入优先）
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> mergeVariableValues(Object promptVariablesDef, Map<String, Object> bizParams) {
        Map<String, Object> merged = new HashMap<>();
        if (promptVariablesDef instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> def) {
                    Object keyObj = def.get("key");
                    if (keyObj == null || String.valueOf(keyObj).isBlank()) {
                        continue;
                    }
                    String key = String.valueOf(keyObj).trim();
                    Object defaultValue = def.get("defaultValue");
                    if (defaultValue != null && !String.valueOf(defaultValue).isBlank()) {
                        merged.put(key, defaultValue);
                    }
                }
            }
        }
        if (bizParams != null) {
            merged.putAll(bizParams);
        }
        return merged;
    }
}
