package com.lightbot.workflow.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.enums.NodeType;
import com.lightbot.workflow.NodeExecutionContext;
import com.lightbot.workflow.NodeExecutionResult;
import com.lightbot.workflow.NodeProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.script.Bindings;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 脚本节点：执行 JavaScript main(params) 并写入输出变量
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScriptNodeProcessor extends AbstractFlowNodeProcessor implements NodeProcessor {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{([^}]+)}}");

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
        if (!"javascript".equalsIgnoreCase(language) && !language.isBlank()) {
            log.warn("[ScriptNodeProcessor] 暂仅支持 javascript，当前: {}", language);
        }

        Object rawResult = runJavaScript(script, params);
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

    private Object runJavaScript(String script, Map<String, Object> params) {
        ScriptEngine engine = new ScriptEngineManager().getEngineByName("javascript");
        if (engine == null) {
            throw new IllegalStateException("未找到 JavaScript 引擎，请确认运行环境支持脚本执行");
        }
        try {
            Bindings bindings = engine.createBindings();
            bindings.put("params", params);
            engine.eval(script, bindings);
            if (engine instanceof Invocable invocable) {
                try {
                    return invocable.invokeFunction("main", params);
                } catch (NoSuchMethodException e) {
                    Object main = bindings.get("main");
                    if (main instanceof java.util.function.Function<?, ?> fn) {
                        return ((java.util.function.Function<Object, Object>) fn).apply(params);
                    }
                }
            }
            return bindings.get("result");
        } catch (Exception e) {
            throw new IllegalArgumentException("脚本执行失败: " + e.getMessage(), e);
        }
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
