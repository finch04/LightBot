package com.lightbot.workflow.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.dto.CodeExecResult;
import com.lightbot.enums.NodeType;
import com.lightbot.service.sandbox.SandboxService;
import com.lightbot.workflow.NodeExecutionContext;
import com.lightbot.workflow.NodeExecutionResult;
import com.lightbot.workflow.NodeProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 脚本节点：委托 {@link SandboxService} 执行 JavaScript main(params) 并写入输出变量
 * <p>向后兼容现有工作流脚本（JavaScript main(params) 模式）。</p>
 *
 * @author finch
 * @since 2026-06-24
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScriptNodeProcessor extends AbstractFlowNodeProcessor implements NodeProcessor {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{([^}]+)}}");

    private static final long SCRIPT_TIMEOUT_MS = 5000;

    private final SandboxService sandboxService;
    private final ObjectMapper objectMapper;

    @Override
    public NodeType getType() {
        return NodeType.SCRIPT;
    }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext context) {
        Map<String, Object> nodeData = context.getCurrentNodeData();
        if (nodeData == null) {
            return passThrough(context, "result", null);
        }

        Map<String, Object> params = buildScriptParams(nodeData, context.getVariables());
        String script = stringVal(nodeData.get("scriptContent"));
        String language = stringVal(nodeData.get("scriptLanguage"));
        if (script.isBlank()) {
            throw new IllegalArgumentException("脚本内容不能为空");
        }

        // 委托统一沙盒执行
        String lang = language.isBlank() ? "javascript" : language;
        CodeExecResult result = sandboxService.executeCode(script, lang, params, SCRIPT_TIMEOUT_MS);

        if (!result.isSuccess()) {
            throw new IllegalArgumentException("脚本执行失败: " + result.getError());
        }

        // 解析返回值为 Map（保持现有 output 映射逻辑）
        Object rawResult = parseReturnValue(result.getReturnValue());
        Map<String, Object> outputs = normalizeOutputs(rawResult, nodeData);
        context.getVariables().putAll(outputs);

        return NodeExecutionResult.builder()
                .nextNodeId(resolveNextNodeId(context))
                .outputs(outputs)
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildScriptParams(Map<String, Object> nodeData, Map<String, Object> variables) {
        Map<String, Object> params = new HashMap<>();
        Object inputParams = nodeData.get("inputParams");
        if (inputParams == null) {
            inputParams = nodeData.get("input_params");
        }
        if (inputParams instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> row)) continue;
                String key = stringVal(row.get("key"));
                if (key.isBlank()) continue;
                String valueExpr = stringVal(row.get("value"));
                params.put(key, resolveExpression(valueExpr, variables));
            }
        }
        if (params.isEmpty()) {
            params.put("query", variables.getOrDefault("query", variables.get("input")));
            params.put("input", variables.getOrDefault("input", variables.get("query")));
        }
        return params;
    }

    private Object resolveExpression(String expr, Map<String, Object> variables) {
        if (expr == null || expr.isBlank()) return null;
        Matcher m = VAR_PATTERN.matcher(expr.trim());
        if (m.matches()) {
            String key = m.group(1).trim();
            return variables.get(key);
        }
        return expr;
    }

    /**
     * 解析返回值：尝试 JSON 反序列化为 Map，否则作为原始值
     */
    private Object parseReturnValue(String returnValue) {
        if (returnValue == null || returnValue.isBlank()) return null;
        String trimmed = returnValue.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                return objectMapper.readValue(trimmed, Object.class);
            } catch (Exception ignored) {
            }
        }
        return trimmed;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeOutputs(Object rawResult, Map<String, Object> nodeData) {
        Map<String, Object> outputs = new HashMap<>();
        if (rawResult instanceof Map<?, ?> map) {
            map.forEach((k, v) -> outputs.put(String.valueOf(k), v));
        } else if (rawResult != null) {
            outputs.put("result", rawResult);
        }
        Object outputParams = nodeData.get("outputParams");
        if (outputParams == null) {
            outputParams = nodeData.get("output_params");
        }
        if (outputParams instanceof List<?> list && !list.isEmpty() && outputs.isEmpty()) {
            String key = stringVal(((Map<?, ?>) list.get(0)).get("key"));
            if (!key.isBlank()) {
                outputs.put(key, rawResult);
            }
        }
        if (outputs.isEmpty()) {
            outputs.put("result", rawResult);
        }
        return outputs;
    }

    private String stringVal(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }
}
