package com.lightbot.util;

import com.lightbot.dto.WorkflowExampleVO;

import java.util.*;

/**
 * 内置示例工作流模板定义
 * <p>5 个示例 Agent 覆盖全部 22 种节点类型，帮助用户快速学习工作流节点使用</p>
 *
 * @author finch
 * @since 2026-05-31
 */
public final class WorkflowExampleTemplates {

    private WorkflowExampleTemplates() {}

    // ========== 公开 API ==========

    /**
     * 获取所有示例列表（不含 workflow 详情，用于前端展示）
     */
    public static List<WorkflowExampleVO> listExamples() {
        return List.of(
                WorkflowExampleVO.builder()
                        .key("rag_qa").name("示例：RAG 知识问答助手")
                        .description("知识库检索 + LLM 问答的标准 RAG 流程，演示 input/retrieval/variable_handle/llm/output 节点")
                        .nodeTypeTags(List.of("input", "output", "retrieval", "llm", "variable_handle"))
                        .build(),
                WorkflowExampleVO.builder()
                        .key("intent_router").name("示例：智能意图路由助手")
                        .description("意图分类 + 条件分支 + 多路处理，演示 classifier/condition/variable 节点的路由组合")
                        .nodeTypeTags(List.of("classifier", "condition", "variable", "llm"))
                        .build(),
                WorkflowExampleVO.builder()
                        .key("batch_parallel").name("示例：批量并行处理助手")
                        .description("批处理容器 + 循环容器，演示 batch/loop 容器节点的并行与迭代用法")
                        .nodeTypeTags(List.of("batch", "batch_start", "batch_end", "loop", "loop_start", "loop_end", "script"))
                        .build(),
                WorkflowExampleVO.builder()
                        .key("data_extract").name("示例：数据提取与转换助手")
                        .description("参数提取 + 脚本处理 + 代码执行，演示 parameter_extractor/script/code 节点的数据处理能力")
                        .nodeTypeTags(List.of("parameter_extractor", "variable_handle", "code", "script"))
                        .build(),
                WorkflowExampleVO.builder()
                        .key("external_integration").name("示例：外部集成与 MCP 助手")
                        .description("API 调用 + MCP 工具 + 条件分支，演示 api/mcp/tool/app_component 节点的外部集成能力")
                        .nodeTypeTags(List.of("api", "script", "condition", "mcp", "tool", "app_component"))
                        .build()
        );
    }

    /**
     * 根据 key 获取示例的完整工作流快照（用于写入 agent_version.config）
     *
     * @param key 示例标识
     * @return agent_version.config 的完整 JSON 结构 Map
     */
    public static Map<String, Object> getWorkflowSnapshot(String key) {
        return switch (key) {
            case "rag_qa" -> buildRagQaWorkflow();
            case "intent_router" -> buildIntentRouterWorkflow();
            case "batch_parallel" -> buildBatchParallelWorkflow();
            case "data_extract" -> buildDataExtractWorkflow();
            case "external_integration" -> buildExternalIntegrationWorkflow();
            default -> null;
        };
    }

    /**
     * 获取示例 Agent 名称
     */
    public static String getExampleName(String key) {
        return listExamples().stream()
                .filter(e -> e.getKey().equals(key))
                .map(WorkflowExampleVO::getName)
                .findFirst().orElse(null);
    }

    // ========== 示例 1：RAG 知识问答助手 ==========

    private static Map<String, Object> buildRagQaWorkflow() {
        List<Map<String, Object>> nodes = List.of(
                node("start_1", "start", 50, 200, Map.of()),
                node("input_1", "input", 200, 200, Map.of(
                        "label", "用户输入",
                        "outputParams", List.of(Map.of("key", "query", "type", "String", "defaultValue", ""))
                )),
                node("retrieval_1", "retrieval", 400, 200, Map.of(
                        "label", "知识检索",
                        "knowledgeId", 0,
                        "overrideConfig", true,
                        "topK", 5,
                        "threshold", 0.5,
                        "inputVariable", "{{query}}"
                )),
                node("varhandle_1", "variable_handle", 600, 200, Map.of(
                        "label", "拼接上下文",
                        "handleType", "template",
                        "templateContent", "以下是知识库检索到的相关内容：\n\n{{retrievalResult}}\n\n请根据以上内容回答用户问题。"
                )),
                node("llm_1", "llm", 800, 200, Map.of(
                        "label", "生成回答",
                        "sysPrompt", "你是一个专业的知识库问答助手。请严格根据提供的知识库内容回答用户问题，如果知识库中没有相关内容，请如实告知。",
                        "promptTemplate", "用户问题：{{query}}\n\n{{output}}",
                        "temperature", 0.7
                )),
                node("output_1", "output", 1000, 200, Map.of(
                        "label", "输出回答",
                        "output", "{{llmOutput}}"
                )),
                node("end_1", "end", 1200, 200, Map.of())
        );
        List<Map<String, Object>> edges = List.of(
                edge("e_start", "start_1", "input_1"),
                edge("e_input", "input_1", "retrieval_1"),
                edge("e_retrieval", "retrieval_1", "varhandle_1"),
                edge("e_varhandle", "varhandle_1", "llm_1"),
                edge("e_llm", "llm_1", "output_1"),
                edge("e_output", "output_1", "end_1")
        );
        return workflowSnapshot(nodes, edges);
    }

    // ========== 示例 2：智能意图路由助手 ==========

    private static Map<String, Object> buildIntentRouterWorkflow() {
        List<Map<String, Object>> nodes = List.of(
                node("start_1", "start", 50, 250, Map.of()),
                node("classifier_1", "classifier", 250, 250, Map.of(
                        "label", "意图分类",
                        "inputVariable", "{{query}}",
                        "mode_switch", "efficient",
                        "instruction", "根据用户输入判断意图类别",
                        "conditions", List.of(
                                Map.of("id", "intent_query", "subject", "信息查询：用户想查询某种信息"),
                                Map.of("id", "intent_complaint", "subject", "投诉建议：用户要投诉或提建议"),
                                Map.of("id", "intent_other", "subject", "其他：无法归类的通用对话")
                        )
                )),
                node("llm_query", "llm", 550, 80, Map.of(
                        "label", "查询处理",
                        "sysPrompt", "你是一个信息查询助手。请根据用户的问题提供准确、简洁的信息。",
                        "promptTemplate", "{{query}}",
                        "temperature", 0.3
                )),
                node("llm_complaint", "llm", 550, 250, Map.of(
                        "label", "投诉处理",
                        "sysPrompt", "你是一个客服投诉处理专员。请认真倾听用户的投诉，表达歉意，并提供解决方案。",
                        "promptTemplate", "{{query}}",
                        "temperature", 0.5
                )),
                node("variable_1", "variable", 550, 420, Map.of(
                        "label", "设置默认回复",
                        "variableName", "fallbackReply",
                        "variableValue", "感谢您的咨询，我暂时无法理解您的问题，能否换个方式描述一下？"
                )),
                node("llm_other", "llm", 780, 420, Map.of(
                        "label", "通用回复",
                        "sysPrompt", "你是一个友好的助手。请用温和的语气与用户对话，尝试理解用户的真实需求。",
                        "promptTemplate", "{{fallbackReply}}\n\n用户说：{{query}}",
                        "temperature", 0.8
                )),
                node("end_1", "end", 1000, 250, Map.of())
        );
        List<Map<String, Object>> edges = List.of(
                edge("e_start", "start_1", "classifier_1"),
                edgeHandle("e_c_query", "classifier_1", "llm_query", "classifier_1_intent_query", "in"),
                edgeHandle("e_c_complaint", "classifier_1", "llm_complaint", "classifier_1_intent_complaint", "in"),
                edgeHandle("e_c_other", "classifier_1", "variable_1", "classifier_1_intent_other", "in"),
                edge("e_q_end", "llm_query", "end_1"),
                edge("e_c_end", "llm_complaint", "end_1"),
                edge("e_var", "variable_1", "llm_other"),
                edge("e_o_end", "llm_other", "end_1")
        );
        return workflowSnapshot(nodes, edges);
    }

    // ========== 示例 3：批量并行处理助手 ==========

    private static Map<String, Object> buildBatchParallelWorkflow() {
        List<Map<String, Object>> nodes = List.of(
                node("start_1", "start", 50, 250, Map.of()),
                node("input_1", "input", 200, 250, Map.of(
                        "label", "输入参数",
                        "outputParams", List.of(Map.of("key", "questions", "type", "Array", "defaultValue", "[]"))
                )),
                node("batch_1", "batch", 400, 200, Map.of(
                        "label", "批量处理",
                        "arrayVariable", "questions",
                        "batchSize", 10,
                        "concurrentSize", 3,
                        "errorStrategy", "continueOnError"
                )),
                node("batch_start_1", "batch_start", 500, 280, Map.of(
                        "label", "并行开始"
                )),
                node("retrieval_1", "retrieval", 650, 280, Map.of(
                        "label", "检索相关文档",
                        "knowledgeId", 0,
                        "overrideConfig", true,
                        "topK", 3,
                        "threshold", 0.5
                )),
                node("llm_1", "llm", 850, 280, Map.of(
                        "label", "生成回答",
                        "sysPrompt", "根据检索内容回答问题，简洁准确。",
                        "promptTemplate", "问题：{{query}}\n\n参考内容：{{retrievalResult}}",
                        "temperature", 0.5
                )),
                node("batch_end_1", "batch_end", 1050, 280, Map.of(
                        "label", "并行结束"
                )),
                node("loop_1", "loop", 1250, 200, Map.of(
                        "label", "结果汇总",
                        "iteratorType", "byCount",
                        "countLimit", 1,
                        "errorStrategy", "continueOnError"
                )),
                node("loop_start_1", "loop_start", 1350, 280, Map.of(
                        "label", "迭代开始"
                )),
                node("script_1", "script", 1500, 280, Map.of(
                        "label", "拼接结果",
                        "scriptLanguage", "javascript",
                        "scriptContent", "function main(params) {\n  var results = params.results || [];\n  var summary = '共处理 ' + results.length + ' 个问题：\\n';\n  for (var i = 0; i < results.length; i++) {\n    summary += (i + 1) + '. ' + results[i] + '\\n';\n  }\n  return { summary: summary };\n}",
                        "inputParams", List.of(Map.of("key", "results", "value", "{{llmOutput}}")),
                        "outputParams", List.of(Map.of("key", "summary"))
                )),
                node("loop_end_1", "loop_end", 1700, 280, Map.of(
                        "label", "迭代结束"
                )),
                node("output_1", "output", 1900, 250, Map.of(
                        "label", "输出汇总",
                        "output", "{{summary}}"
                )),
                node("end_1", "end", 2100, 250, Map.of())
        );
        List<Map<String, Object>> edges = List.of(
                edge("e_start", "start_1", "input_1"),
                edge("e_input", "input_1", "batch_1"),
                edge("e_batch_in", "batch_1", "batch_start_1"),
                edge("e_bs", "batch_start_1", "retrieval_1"),
                edge("e_ret", "retrieval_1", "llm_1"),
                edge("e_llm", "llm_1", "batch_end_1"),
                edge("e_batch_out", "batch_1", "loop_1"),
                edge("e_loop_in", "loop_1", "loop_start_1"),
                edge("e_ls", "loop_start_1", "script_1"),
                edge("e_script", "script_1", "loop_end_1"),
                edge("e_loop_out", "loop_1", "output_1"),
                edge("e_output", "output_1", "end_1")
        );
        Map<String, Object> ws = workflowSnapshot(nodes, edges);
        // 设置 batch 和 loop 的父子关系
        setParentNode(nodes, "batch_start_1", "batch_1");
        setParentNode(nodes, "retrieval_1", "batch_1");
        setParentNode(nodes, "llm_1", "batch_1");
        setParentNode(nodes, "batch_end_1", "batch_1");
        setParentNode(nodes, "loop_start_1", "loop_1");
        setParentNode(nodes, "script_1", "loop_1");
        setParentNode(nodes, "loop_end_1", "loop_1");
        return ws;
    }

    // ========== 示例 4：数据提取与转换助手 ==========

    private static Map<String, Object> buildDataExtractWorkflow() {
        List<Map<String, Object>> nodes = List.of(
                node("start_1", "start", 50, 250, Map.of()),
                node("extractor_1", "parameter_extractor", 250, 250, Map.of(
                        "label", "提取用户信息",
                        "inputVariable", "{{query}}",
                        "instruction", "从用户输入中提取个人信息，如果某个字段未提及则留空",
                        "extractParams", List.of(
                                Map.of("key", "name", "type", "String", "desc", "用户姓名", "required", false),
                                Map.of("key", "email", "type", "String", "desc", "邮箱地址", "required", false),
                                Map.of("key", "phone", "type", "String", "desc", "电话号码", "required", false)
                        )
                )),
                node("varhandle_1", "variable_handle", 500, 250, Map.of(
                        "label", "格式化信息",
                        "handleType", "template",
                        "templateContent", "姓名：{{name}}\n邮箱：{{email}}\n电话：{{phone}}"
                )),
                node("code_1", "code", 700, 250, Map.of(
                        "label", "验证邮箱格式",
                        "scriptLanguage", "javascript",
                        "codeContent", "function main(params) {\n  var email = params.email || '';\n  var valid = /^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$/.test(email);\n  return {\n    emailValid: valid,\n    emailStatus: valid ? '格式正确' : '格式无效'\n  };\n}",
                        "inputParams", List.of(Map.of("key", "email", "value", "{{email}}")),
                        "outputParams", List.of(Map.of("key", "emailValid"), Map.of("key", "emailStatus"))
                )),
                node("script_1", "script", 900, 250, Map.of(
                        "label", "生成摘要",
                        "scriptLanguage", "javascript",
                        "scriptContent", "function main(params) {\n  var name = params.name || '未提供';\n  var email = params.email || '未提供';\n  var phone = params.phone || '未提供';\n  var emailStatus = params.emailStatus || '未知';\n  var summary = '用户信息摘要：\\n';\n  summary += '- 姓名：' + name + '\\n';\n  summary += '- 邮箱：' + email + '（' + emailStatus + '）\\n';\n  summary += '- 电话：' + phone;\n  return { result: summary };\n}",
                        "inputParams", List.of(
                                Map.of("key", "name", "value", "{{name}}"),
                                Map.of("key", "email", "value", "{{email}}"),
                                Map.of("key", "phone", "value", "{{phone}}"),
                                Map.of("key", "emailStatus", "value", "{{emailStatus}}")
                        ),
                        "outputParams", List.of(Map.of("key", "result"))
                )),
                node("output_1", "output", 1100, 250, Map.of(
                        "label", "输出结果",
                        "output", "{{result}}"
                )),
                node("end_1", "end", 1300, 250, Map.of())
        );
        List<Map<String, Object>> edges = List.of(
                edge("e_start", "start_1", "extractor_1"),
                edge("e_extract", "extractor_1", "varhandle_1"),
                edge("e_vh", "varhandle_1", "code_1"),
                edge("e_code", "code_1", "script_1"),
                edge("e_script", "script_1", "output_1"),
                edge("e_output", "output_1", "end_1")
        );
        return workflowSnapshot(nodes, edges);
    }

    // ========== 示例 5：外部集成与 MCP 助手 ==========

    private static Map<String, Object> buildExternalIntegrationWorkflow() {
        List<Map<String, Object>> nodes = List.of(
                node("start_1", "start", 50, 300, Map.of()),
                node("api_1", "api", 220, 300, Map.of(
                        "label", "调用外部API",
                        "url", "https://jsonplaceholder.typicode.com/posts/1",
                        "method", "GET",
                        "timeout", 30
                )),
                node("script_parse", "script", 420, 300, Map.of(
                        "label", "解析响应",
                        "scriptLanguage", "javascript",
                        "scriptContent", "function main(params) {\n  try {\n    var body = JSON.parse(params.body || '{}');\n    return {\n      title: body.title || '',\n      content: body.body || '',\n      statusCode: params.statusCode\n    };\n  } catch (e) {\n    return { error: 'JSON解析失败: ' + e.message, statusCode: params.statusCode };\n  }\n}",
                        "inputParams", List.of(
                                Map.of("key", "body", "value", "{{body}}"),
                                Map.of("key", "statusCode", "value", "{{statusCode}}")
                        ),
                        "outputParams", List.of(Map.of("key", "title"), Map.of("key", "content"), Map.of("key", "statusCode"))
                )),
                node("condition_1", "condition", 650, 300, Map.of(
                        "label", "状态检查",
                        "conditionGroups", List.of(
                                Map.of(
                                        "relation", "and",
                                        "sourceHandle", "out_a",
                                        "rules", List.of(Map.of("variable", "statusCode", "operator", "eq", "value", "200"))
                                ),
                                Map.of(
                                        "relation", "and",
                                        "sourceHandle", "out_b",
                                        "rules", List.of(Map.of("variable", "error", "operator", "not_empty", "value", ""))
                                )
                        )
                )),
                node("tool_1", "tool", 900, 120, Map.of(
                        "label", "工具处理",
                        "toolId", 0,
                        "inputParams", List.of(Map.of("key", "text", "value", "{{title}}"))
                )),
                node("mcp_1", "mcp", 900, 300, Map.of(
                        "label", "MCP工具调用",
                        "toolName", "example_tool",
                        "inputParams", List.of(Map.of("key", "data", "value", "{{content}}"))
                )),
                node("llm_1", "llm", 1150, 200, Map.of(
                        "label", "生成总结",
                        "sysPrompt", "你是一个数据分析师。请根据API返回的数据，用自然语言生成简洁的总结。",
                        "promptTemplate", "API返回的数据标题：{{title}}\n内容：{{content}}\n\n请生成一段简洁的总结。",
                        "temperature", 0.5
                )),
                node("output_ok", "output", 1400, 200, Map.of(
                        "label", "成功输出",
                        "output", "API调用成功！\n\n{{llmOutput}}"
                )),
                node("app_component_1", "app_component", 900, 480, Map.of(
                        "label", "应用组件示例",
                        "componentCode", "example_workflow",
                        "componentType", "workflow"
                )),
                node("variable_err", "variable", 1150, 420, Map.of(
                        "label", "错误信息",
                        "variableName", "errorMsg",
                        "variableValue", "API调用或解析失败：{{error}}"
                )),
                node("output_err", "output", 1400, 420, Map.of(
                        "label", "错误输出",
                        "output", "{{errorMsg}}"
                )),
                node("end_1", "end", 1650, 300, Map.of())
        );
        List<Map<String, Object>> edges = List.of(
                edge("e_start", "start_1", "api_1"),
                edge("e_api", "api_1", "script_parse"),
                edge("e_parse", "script_parse", "condition_1"),
                edgeHandle("e_ok", "condition_1", "tool_1", "out_a", "in"),
                edgeHandle("e_err", "condition_1", "app_component_1", "out_b", "in"),
                edge("e_tool", "tool_1", "mcp_1"),
                edge("e_mcp", "mcp_1", "llm_1"),
                edge("e_llm", "llm_1", "output_ok"),
                edge("e_out_ok", "output_ok", "end_1"),
                edge("e_app", "app_component_1", "variable_err"),
                edge("e_var_err", "variable_err", "output_err"),
                edge("e_out_err", "output_err", "end_1")
        );
        return workflowSnapshot(nodes, edges);
    }

    // ========== 工具方法 ==========

    private static Map<String, Object> node(String id, String type, double x, double y, Map<String, Object> data) {
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("id", id);
        n.put("type", type);
        n.put("position", Map.of("x", x, "y", y));
        n.put("data", data);
        n.put("parentNode", null);
        return n;
    }

    private static Map<String, Object> edge(String id, String source, String target) {
        return edgeHandle(id, source, target, "out", "in");
    }

    private static Map<String, Object> edgeHandle(String id, String source, String target,
                                                   String sourceHandle, String targetHandle) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("id", id);
        e.put("source", source);
        e.put("target", target);
        e.put("label", null);
        e.put("sourceHandle", sourceHandle);
        e.put("targetHandle", targetHandle);
        return e;
    }

    private static Map<String, Object> workflowSnapshot(List<Map<String, Object>> nodes,
                                                         List<Map<String, Object>> edges) {
        Map<String, Object> graph = new LinkedHashMap<>();
        graph.put("nodes", nodes);
        graph.put("edges", edges);
        graph.put("globalConfig", Map.of(
                "history_config", Map.of("history_switch", true, "history_max_round", 5),
                "variable_config", Map.of("conversation_params", List.of(
                        Map.of("key", "query", "default_value", "")
                ))
        ));

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("kind", "workflow");
        snapshot.put("graph", graph);
        return snapshot;
    }

    @SuppressWarnings("unchecked")
    private static void setParentNode(List<Map<String, Object>> nodes, String childId, String parentId) {
        for (Map<String, Object> n : nodes) {
            if (childId.equals(n.get("id"))) {
                n.put("parentNode", parentId);
                n.put("extent", "parent");
                break;
            }
        }
    }
}
