package com.lightbot.util;

import com.lightbot.vo.WorkflowExampleVO;

import java.util.*;

/**
 * 内置示例工作流模板定义
 * <p>10 个示例 Agent 覆盖工作流节点与工具节点（含 confirm / tool / ask_user / app_component / sub_agent），帮助用户快速学习</p>
 *
 * @author finch
 * @since 2026-05-31
 */
public final class WorkflowExampleTemplates {

    /** 子工作流占位符前缀，创建示例 Agent 时替换为真实 Agent ID */
    public static final String SUB_PLACEHOLDER_PREFIX = "__SUB:";
    /** 工具占位符前缀，创建示例 Agent 时按工具名解析 toolId */
    public static final String TOOL_PLACEHOLDER_PREFIX = "__TOOL:";
    /** SubAgent 能力占位符前缀，创建示例时替换为真实 SubAgent ID */
    public static final String CAPABILITY_SUBAGENT_PLACEHOLDER_PREFIX = "__SA:";

    private static final Map<String, String> SUB_WORKFLOW_NAMES = Map.of(
            "data_prep_sub", "示例子流程：结构化提取模块"
    );

    private static final Map<String, String> CAPABILITY_SUBAGENT_NAMES = Map.of(
            "example_summarizer", "wf_example_summarizer"
    );

    private static final Map<String, String> CAPABILITY_SUBAGENT_DISPLAY_NAMES = Map.of(
            "example_summarizer", "示例摘要助手"
    );

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
                        .description("批量拆分需求 + 并行评审 + 循环格式化，演示 batch/loop 容器节点的并行与迭代用法")
                        .nodeTypeTags(List.of("batch", "batch_start", "batch_end", "loop", "loop_start", "loop_end", "script"))
                        .build(),
                WorkflowExampleVO.builder()
                        .key("data_extract").name("示例：数据提取与转换助手")
                        .description("参数提取 + 变量处理 + 脚本执行，演示 parameter_extractor/variable_handle/script 节点的数据处理能力")
                        .nodeTypeTags(List.of("parameter_extractor", "variable_handle", "script"))
                        .build(),
                WorkflowExampleVO.builder()
                        .key("external_integration").name("示例：外部集成与 MCP 助手")
                        .description("API 调用 + MCP 工具 + 条件分支，演示 api/script/condition/mcp 节点的外部集成能力")
                        .nodeTypeTags(List.of("api", "script", "condition", "mcp"))
                        .build(),
                WorkflowExampleVO.builder()
                        .key("tool_multi").name("示例：多工具协作助手")
                        .description("参数提取 + 联网搜索 + 计算器工具 + LLM 汇总，演示 tool 节点与工具链组合")
                        .nodeTypeTags(List.of("parameter_extractor", "tool", "variable_handle", "llm", "output"))
                        .build(),
                WorkflowExampleVO.builder()
                        .key("human_review").name("示例：人工审核助手")
                        .description("LLM 生成草稿 → 人工确认表单（展示/文本/数字/单选/下拉/多行）→ 条件分支，演示 confirm 节点人机协同审核")
                        .nodeTypeTags(List.of("llm", "confirm", "condition", "variable", "output"))
                        .build(),
                WorkflowExampleVO.builder()
                        .key("sub_workflow_orchestrator").name("示例：子工作流编排助手")
                        .description("嵌套子工作流 + 人工复核 + LLM 定稿，演示 app_component 与 confirm 组合编排")
                        .nodeTypeTags(List.of("app_component", "confirm", "condition", "llm", "parameter_extractor", "output"))
                        .build(),
                WorkflowExampleVO.builder()
                        .key("gaokao_volunteer").name("示例：高考志愿填报助手")
                        .description("向用户提问工具链式反问省市、分数、选科，再生成志愿填报建议，演示 ask_user HITL 挂起与 resume")
                        .nodeTypeTags(List.of("input", "tool", "variable_handle", "llm", "output"))
                        .build(),
                WorkflowExampleVO.builder()
                        .key("sub_agent_delegate").name("示例：SubAgent 委派助手")
                        .description("输入需求后委派 SubAgent 摘要整理，再输出结果，演示 sub_agent 节点在 DAG 中的用法")
                        .nodeTypeTags(List.of("input", "sub_agent", "output"))
                        .build()
        );
    }

    /**
     * 主示例是否需要先创建并发布子工作流
     */
    public static List<String> getSubWorkflowKeys(String key) {
        if ("sub_workflow_orchestrator".equals(key)) {
            return List.of("data_prep_sub");
        }
        return List.of();
    }

    /**
     * 主示例是否需要先准备 SubAgent 能力实例
     */
    public static List<String> getCapabilitySubAgentKeys(String key) {
        if ("sub_agent_delegate".equals(key)) {
            return List.of("example_summarizer");
        }
        return List.of();
    }

    /**
     * 子工作流 Agent 名称
     */
    public static String getSubWorkflowName(String subKey) {
        return SUB_WORKFLOW_NAMES.getOrDefault(subKey, "示例子工作流");
    }

    /**
     * SubAgent 能力标识名（英文唯一名）
     */
    public static String getCapabilitySubAgentName(String saKey) {
        return CAPABILITY_SUBAGENT_NAMES.getOrDefault(saKey, saKey);
    }

    /**
     * SubAgent 能力显示名
     */
    public static String getCapabilitySubAgentDisplayName(String saKey) {
        return CAPABILITY_SUBAGENT_DISPLAY_NAMES.getOrDefault(saKey, saKey);
    }

    /**
     * 获取子工作流快照（未解析占位符）
     */
    public static Map<String, Object> getSubWorkflowSnapshot(String subKey) {
        return switch (subKey) {
            case "data_prep_sub" -> buildDataPrepSubWorkflow();
            default -> null;
        };
    }

    /**
     * 解析快照中的工具 ID / 子工作流 Agent ID / SubAgent 能力 ID 占位符（原地修改）
     */
    @SuppressWarnings("unchecked")
    public static void resolveBindings(Map<String, Object> snapshot,
                                       Map<String, Long> subWorkflowAgentIds,
                                       Map<String, Long> toolIds,
                                       Map<String, Long> capabilitySubAgentIds) {
        if (snapshot == null) {
            return;
        }
        Object graphObj = snapshot.get("graph");
        if (!(graphObj instanceof Map<?, ?> graph)) {
            return;
        }
        Object nodesObj = graph.get("nodes");
        if (!(nodesObj instanceof List<?> nodes)) {
            return;
        }
        Map<String, Long> safeSubWorkflowIds = subWorkflowAgentIds != null ? subWorkflowAgentIds : Map.of();
        Map<String, Long> safeToolIds = toolIds != null ? toolIds : Map.of();
        Map<String, Long> safeCapabilityIds = capabilitySubAgentIds != null ? capabilitySubAgentIds : Map.of();
        for (Object nodeObj : nodes) {
            if (!(nodeObj instanceof Map<?, ?> node)) {
                continue;
            }
            Object dataObj = node.get("data");
            if (!(dataObj instanceof Map<?, ?> data)) {
                continue;
            }
            Map<String, Object> dataMap = (Map<String, Object>) data;
            resolveToolPlaceholder(dataMap, safeToolIds);
            resolveSubWorkflowPlaceholder(dataMap, safeSubWorkflowIds);
            resolveCapabilitySubAgentPlaceholder(dataMap, safeCapabilityIds);
        }
    }

    /**
     * 获取解析占位符后的工作流快照
     */
    public static Map<String, Object> buildResolvedSnapshot(String key,
                                                            Map<String, Long> subWorkflowAgentIds,
                                                            Map<String, Long> toolIds,
                                                            Map<String, Long> capabilitySubAgentIds) {
        Map<String, Object> snapshot = getWorkflowSnapshot(key);
        if (snapshot == null) {
            return null;
        }
        Map<String, Object> mutable = deepCopyMap(snapshot);
        resolveBindings(mutable, subWorkflowAgentIds, toolIds, capabilitySubAgentIds);
        return mutable;
    }

    /**
     * 获取解析占位符后的子工作流快照
     */
    public static Map<String, Object> buildResolvedSubSnapshot(String subKey, Map<String, Long> toolIds) {
        Map<String, Object> snapshot = getSubWorkflowSnapshot(subKey);
        if (snapshot == null) {
            return null;
        }
        Map<String, Object> mutable = deepCopyMap(snapshot);
        resolveBindings(mutable, Map.of(), toolIds, Map.of());
        return mutable;
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
            case "tool_multi" -> buildToolMultiWorkflow();
            case "human_review" -> buildHumanReviewWorkflow();
            case "sub_workflow_orchestrator" -> buildSubWorkflowOrchestrator();
            case "gaokao_volunteer" -> buildGaokaoVolunteerWorkflow();
            case "sub_agent_delegate" -> buildSubAgentDelegateWorkflow();
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

    /**
     * 获取示例欢迎语
     */
    public static String getWelcomeMessage(String key) {
        return WELCOME_MAP.getOrDefault(key, "你好，有什么可以帮你的？");
    }

    /**
     * 获取示例推荐问题（JSON 数组字符串）
     */
    public static String getRecommendedQuestions(String key) {
        return QUESTIONS_MAP.getOrDefault(key, "[]");
    }

    // ========== 欢迎语 & 推荐问题 ==========

    private static final Map<String, String> WELCOME_MAP = Map.ofEntries(
            Map.entry("rag_qa", "## RAG 知识问答助手\n我可以从知识库中检索相关信息，为你提供准确的回答。\n\n> 请先在工作流中绑定知识库，然后开始提问。"),
            Map.entry("intent_router", "## 智能意图路由助手\n我会自动识别你的意图类型，分配给不同的处理模块来回答。\n\n> 支持信息查询、投诉建议等多种意图。"),
            Map.entry("batch_parallel", "## 批量并行处理助手\n我可以把多条需求或反馈拆成列表，并行完成初步评审，再逐条格式化为汇总报告。\n\n> 请输入多条需求、缺陷或用户反馈，每行一条即可体验批处理与循环容器。"),
            Map.entry("data_extract", "## 数据提取与转换助手\n我可以从自然语言中提取结构化信息（姓名、邮箱、电话等），并进行格式验证。\n\n> 试试用一句话描述你的个人信息。"),
            Map.entry("external_integration", "## 外部集成与 MCP 助手\n我可以调用外部 API、MCP 工具获取数据，并自动生成分析总结。\n\n> 已预置示例 API，直接对话即可体验。"),
            Map.entry("tool_multi", "## 多工具协作助手\n我会先提取你的问题与计算参数，再调用联网搜索和计算器工具，最后汇总成完整回答。\n\n> 请描述一个需要查资料并做简单计算的问题。"),
            Map.entry("human_review", "## 人工审核助手\n我会先生成草稿内容，然后暂停等待你在对话中完成人工确认（含展示信息、文本、数字、单选、下拉、多行意见等字段），通过后再输出正式版本。\n\n> 适合体验 confirm 人工确认节点的完整表单能力。"),
            Map.entry("sub_workflow_orchestrator", "## 子工作流编排助手\n我会调用内置子工作流做结构化提取，再请你复核子流程结果，最后生成定稿回复。\n\n> 创建本示例时会自动生成并发布子工作流 Agent。"),
            Map.entry("gaokao_volunteer", "## 高考志愿填报助手\n我会依次向您确认**高考省份、总分、选科组合**等信息（对话中会弹出提问表单），收集完整后再给出冲稳保方向的志愿建议。\n\n> 演示 ask_user 工具节点的工作流挂起与 resume；请以官方招生简章与考试院数据为准。"),
            Map.entry("sub_agent_delegate", "## SubAgent 委派助手\n我会把你的需求交给 SubAgent 做摘要整理，再把结果返回给流程。\n\n> 创建本示例时会自动准备示例 SubAgent（`wf_example_summarizer`）。")
    );

    private static final Map<String, String> QUESTIONS_MAP = Map.ofEntries(
            Map.entry("rag_qa", "[\"这个知识库包含哪些内容？\", \"帮我总结一下关键信息\", \"有哪些常见问题？\"]"),
            Map.entry("intent_router", "[\"我想查询一下产品价格\", \"我对服务不满意，要投诉\", \"你好，随便聊聊\"]"),
            Map.entry("batch_parallel", "[\"请批量评审这些需求：\\n1. 登录页偶发白屏，影响客户演示，需要今天修复\\n2. 希望知识库支持批量导入飞书文档\\n3. 工作流编排页节点太多时拖动画布卡顿\\n4. 新增对话消息导出为 Markdown\", \"请把这些用户反馈按优先级整理：\\n1. 客户说支付成功后订单状态偶尔不更新\\n2. 希望移动端对话页面支持语音输入\\n3. 上传大文件时进度提示不明显\\n4. 希望管理员能导出团队成员使用报表\", \"请分析下面的缺陷和优化建议：\\n1. 知识库检索结果偶尔为空，但文档里明明有相关内容\\n2. 希望工作流节点支持复制粘贴\\n3. 对话历史很多时页面滚动变卡\\n4. 发布工作流前希望增加配置完整性检查\"]"),
            Map.entry("data_extract", "[\"我叫张三，邮箱是zhangsan@example.com，电话13800138000\", \"帮我验证一下 test@domain.com 这个邮箱格式对不对\", \"从这段话里提取所有联系方式\"]"),
            Map.entry("external_integration", "[\"帮我调用API获取数据\", \"用MCP工具处理一下任务\", \"外部接口调用失败了怎么办？\"]"),
            Map.entry("tool_multi", "[\"查一下2024年全球AI市场规模，并计算1000×1.15\", \"搜索智元是什么，再算一下256+128\", \"帮我调研云原生趋势并计算增长率\"]"),
            Map.entry("human_review", "[\"帮我写一段产品发布公告\", \"起草一封客户道歉邮件\", \"生成一份活动邀请文案\"]"),
            Map.entry("sub_workflow_orchestrator", "[\"整理这段需求：我们要做智能客服，支持多轮对话和知识库\", \"提取下面方案的关键信息并复核\", \"分析这段产品介绍，输出结构化摘要\"]"),
            Map.entry("gaokao_volunteer", "[\"我是河南考生，想报计算机相关专业\", \"帮我看看600分左右能报哪些学校\", \"新高考选科物化生，分数630怎么填志愿\"]"),
            Map.entry("sub_agent_delegate", "[\"请帮我摘要：我们要做智能客服，支持多轮对话、知识库检索和工单流转\", \"把下面需求整理成三点：提升响应速度、降低人工成本、支持夜间值班\", \"总结这段话的核心目标与约束\"]")
    );

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
                        "temperature", 0.7,
                        "enableStreaming", true
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
                        "temperature", 0.3,
                        "enableStreaming", true
                )),
                node("llm_complaint", "llm", 550, 250, Map.of(
                        "label", "投诉处理",
                        "sysPrompt", "你是一个客服投诉处理专员。请认真倾听用户的投诉，表达歉意，并提供解决方案。",
                        "promptTemplate", "{{query}}",
                        "temperature", 0.5,
                        "enableStreaming", true
                )),
                node("variable_1", "variable", 550, 420, Map.of(
                        "label", "设置默认回复",
                        "variableName", "fallbackReply",
                        "variableValue", "感谢您的咨询，我暂时无法理解您的问题，能否换个方式描述一下？"
                )),
                node("llm_other", "llm", 780, 420, Map.of(
                        "label", "通用回复",
                        "sysPrompt", "你是一个友好的助手。请严格按下方「标准回复」的内容回复用户，不要自行发挥，可以适当补充礼貌用语。",
                        "promptTemplate", "【标准回复】：{{fallbackReply}}\n\n【用户消息】：{{query}}\n\n请将「标准回复」作为核心内容回复用户。",
                        "temperature", 0.3,
                        "enableStreaming", true
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
                        "label", "输入需求列表",
                        "outputParams", List.of(Map.of("key", "query", "type", "String", "defaultValue", "{{sys.query}}"))
                )),
                node("script_split", "script", 420, 250, Map.of(
                        "label", "拆分需求",
                        "scriptLanguage", "javascript",
                        "scriptContent", "function main(params) {\n  var input = String(params.input || params.query || '').trim();\n  try {\n    var arr = JSON.parse(input);\n    if (Array.isArray(arr) && arr.length > 0) {\n      return { items: arr.map(function(s) { return String(s).trim(); }).filter(function(s) { return s.length > 0; }) };\n    }\n  } catch (e) {}\n  var text = input.replace(/\\r/g, '\\n');\n  var parts = text.split(/[\\n；;]+/);\n  if (parts.length <= 1) {\n    parts = text.split(/(?=\\s*(?:\\d+[\\.、)]|[-*])\\s+)/);\n  }\n  var items = parts.map(function(s) {\n    return String(s).replace(/^\\s*(?:\\d+[\\.、)]|[-*])\\s*/, '').trim();\n  }).filter(function(s) {\n    return s.length > 0 && !/^请.*(需求|反馈|缺陷|建议).*[:：]?$/.test(s);\n  });\n  if (items.length === 0 && input.length > 0) items = [input];\n  return { items: items };\n}",
                        "inputParams", List.of(
                                Map.of("key", "input", "value", "{{query}}"),
                                Map.of("key", "query", "value", "{{sys.query}}")
                        ),
                        "outputParams", List.of(Map.of("key", "items"))
                )),
                node("batch_1", "batch", 680, 200, Map.of(
                        "label", "并行评审",
                        "inputParams", List.of(Map.of("key", "item", "value", "{{items}}")),
                        "batchSize", 10,
                        "concurrentSize", 3,
                        "errorStrategy", "continueOnError",
                        "outputParams", List.of(Map.of("key", "analysisItem", "type", "Array"))
                )),
                node("batch_start_1", "batch_start", 780, 280, Map.of(
                        "label", "并行开始"
                )),
                node("script_analyze", "script", 930, 280, Map.of(
                        "label", "评审单条需求",
                        "scriptLanguage", "javascript",
                        "scriptContent", "function main(params) {\n  var text = String(params.item || '').trim();\n  var type = /bug|错误|失败|异常|崩溃|报错/.test(text) ? '缺陷修复' : (/优化|提升|改进|体验|性能/.test(text) ? '体验优化' : (/新增|支持|接入|增加|实现/.test(text) ? '新功能' : '需求澄清'));\n  var priority = /紧急|马上|今天|阻塞|故障|崩溃|支付|登录/.test(text) ? 'P0' : (/重要|本周|核心|客户/.test(text) ? 'P1' : 'P2');\n  var estimate = text.length > 60 ? 'L' : (text.length > 28 ? 'M' : 'S');\n  var action = priority === 'P0' ? '立即排查并安排负责人' : (type === '需求澄清' ? '补充场景、目标用户和验收口径' : '进入需求池排期评审');\n  return { analysisItem: { text: text, type: type, priority: priority, estimate: estimate, action: action } };\n}",
                        "inputParams", List.of(Map.of("key", "item", "value", "{{item}}")),
                        "outputParams", List.of(Map.of("key", "analysisItem"))
                )),
                node("batch_end_1", "batch_end", 1130, 280, Map.of(
                        "label", "并行结束"
                )),
                node("loop_1", "loop", 1350, 200, Map.of(
                        "label", "循环格式化",
                        "iteratorType", "byArray",
                        "inputParams", List.of(Map.of("key", "analysis", "value", "{{analysisItem}}")),
                        "errorStrategy", "continueOnError",
                        "outputParams", List.of(Map.of("key", "line", "type", "Array"))
                )),
                node("loop_start_1", "loop_start", 1450, 280, Map.of(
                        "label", "迭代开始"
                )),
                node("script_format", "script", 1600, 280, Map.of(
                        "label", "格式化单条结果",
                        "scriptLanguage", "javascript",
                        "scriptContent", "function main(params) {\n  var item = params.analysis || {};\n  var line = '- [' + (item.priority || 'P2') + '] ' + (item.type || '需求') + ' / ' + (item.estimate || 'M') + ': ' + (item.text || '') + '; 建议: ' + (item.action || '进入排期评审');\n  return { line: line };\n}",
                        "inputParams", List.of(Map.of("key", "analysis", "value", "{{analysis}}")),
                        "outputParams", List.of(Map.of("key", "line"))
                )),
                node("loop_end_1", "loop_end", 1800, 280, Map.of(
                        "label", "迭代结束"
                )),
                node("script_join", "script", 2020, 250, Map.of(
                        "label", "汇总报告",
                        "scriptLanguage", "javascript",
                        "scriptContent", "function main(params) {\n  function parseMaybeJson(value) {\n    if (typeof value !== 'string') return value;\n    var text = value.trim();\n    if (text.charAt(0) !== '[' && text.charAt(0) !== '{') return value;\n    try { return JSON.parse(text); } catch (e) { return value; }\n  }\n  function isJavaList(value) {\n    return value != null && typeof value === 'object' && typeof value.size === 'function' && typeof value.get === 'function';\n  }\n  function flatten(value, acc) {\n    value = parseMaybeJson(value);\n    if (Array.isArray(value)) {\n      value.forEach(function(item) { flatten(item, acc); });\n      return acc;\n    }\n    if (isJavaList(value)) {\n      for (var i = 0; i < value.size(); i++) flatten(value.get(i), acc);\n      return acc;\n    }\n    if (value == null) return acc;\n    if (typeof value === 'object') {\n      var json = JSON.stringify(value);\n      if (json && json !== '{}') acc.push(json);\n    } else {\n      var text = String(value).trim();\n      if (text.length > 0) acc.push(text);\n    }\n    return acc;\n  }\n  var lines = flatten(params.lines || [], []);\n  var result = '## 批量需求评审结果\\n\\n共处理 ' + lines.length + ' 条输入：\\n\\n' + lines.join('\\n');\n  return { result: result };\n}",
                        "inputParams", List.of(Map.of("key", "lines", "value", "{{line}}")),
                        "outputParams", List.of(Map.of("key", "result"))
                )),
                node("output_1", "output", 2240, 250, Map.of(
                        "label", "输出汇总",
                        "output", "{{result}}"
                )),
                node("end_1", "end", 2480, 250, Map.of())
        );
        List<Map<String, Object>> edges = List.of(
                edge("e_start", "start_1", "input_1"),
                edge("e_input", "input_1", "script_split"),
                edge("e_split", "script_split", "batch_start_1"),
                edge("e_bs", "batch_start_1", "script_analyze"),
                edge("e_analyze", "script_analyze", "batch_end_1"),
                edge("e_batch_out", "batch_end_1", "loop_start_1"),
                edge("e_ls", "loop_start_1", "script_format"),
                edge("e_format", "script_format", "loop_end_1"),
                edge("e_loop_out", "loop_end_1", "script_join"),
                edge("e_join", "script_join", "output_1"),
                edge("e_output", "output_1", "end_1")
        );
        Map<String, Object> ws = workflowSnapshot(nodes, edges);
        // 设置 batch 和 loop 的父子关系
        setParentNode(nodes, "batch_start_1", "batch_1");
        setParentNode(nodes, "script_analyze", "batch_1");
        setParentNode(nodes, "batch_end_1", "batch_1");
        setParentNode(nodes, "loop_start_1", "loop_1");
        setParentNode(nodes, "script_format", "loop_1");
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
                        "templateContent", "姓名：{{extractor_1.name}}\n邮箱：{{extractor_1.email}}\n电话：{{extractor_1.phone}}"
                )),
                node("script_1", "script", 700, 250, Map.of(
                        "label", "生成摘要",
                        "scriptLanguage", "javascript",
                        "scriptContent", "function main(params) {\n  var name = String(params.name || '未提供');\n  var email = String(params.email || '未提供');\n  var phone = String(params.phone || '未提供');\n  var emailValid = /^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$/.test(email);\n  var emailStatus = emailValid ? '格式正确' : '格式无效';\n  var summary = '用户信息摘要：\\n';\n  summary += '- 姓名：' + name + '\\n';\n  summary += '- 邮箱：' + email + '（' + emailStatus + '）\\n';\n  summary += '- 电话：' + phone;\n  return { result: summary };\n}",
                        "inputParams", List.of(
                                Map.of("key", "name", "value", "{{extractor_1.name}}"),
                                Map.of("key", "email", "value", "{{extractor_1.email}}"),
                                Map.of("key", "phone", "value", "{{extractor_1.phone}}")
                        ),
                        "outputParams", List.of(Map.of("key", "result"))
                )),
                node("output_1", "output", 950, 250, Map.of(
                        "label", "输出结果",
                        "output", "{{script_1.result}}"
                )),
                node("end_1", "end", 1150, 250, Map.of(
                        "outputType", "text",
                        "textTemplate", "{{script_1.result}}"
                ))
        );
        List<Map<String, Object>> edges = List.of(
                edge("e_start", "start_1", "extractor_1"),
                edge("e_extract", "extractor_1", "varhandle_1"),
                edge("e_vh", "varhandle_1", "script_1"),
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
                        "scriptContent", "function main(params) {\n  try {\n    var body = JSON.parse(params.body || '{}');\n    var title = body.title || '';\n    var content = body.body || '';\n    var wordCount = content.split(/\\s+/).length;\n    var charCount = content.length;\n    var chartData = [\n      { name: 'Title', value: title.length },\n      { name: 'Word Count', value: wordCount },\n      { name: 'Char Count', value: charCount }\n    ];\n    return {\n      title: title,\n      chartData: JSON.stringify(chartData),\n      statusCode: params.statusCode\n    };\n  } catch (e) {\n    return { error: 'JSON解析失败: ' + e.message, statusCode: params.statusCode };\n  }\n}",
                        "inputParams", List.of(
                                Map.of("key", "body", "value", "{{body}}"),
                                Map.of("key", "statusCode", "value", "{{statusCode}}")
                        ),
                        "outputParams", List.of(Map.of("key", "title"), Map.of("key", "chartData"), Map.of("key", "statusCode"))
                )),
                node("condition_1", "condition", 650, 300, Map.of(
                        "label", "状态检查",
                        "conditionGroups", List.of(
                                conditionGroup("cg_ok", "成功", "and",
                                        List.of(Map.of("variable", "statusCode", "operator", "eq", "value", "200"))),
                                conditionGroup("cg_err", "有错误", "and",
                                        List.of(Map.of("variable", "error", "operator", "not_empty", "value", "")))
                        )
                )),
                node("mcp_1", "mcp", 900, 300, Map.of(
                        "label", "MCP工具调用",
                        "toolName", "generate_bar_chart",
                        "mcpServerName", "mcp-server-chart",
                        "inputParams", List.of(Map.of("key", "data", "value", "{{chartData}}"))
                )),
                node("llm_1", "llm", 1150, 200, Map.of(
                        "label", "生成总结",
                        "sysPrompt", "你是一个数据分析师。请根据API返回的数据和图表统计，用自然语言生成简洁的总结。",
                        "promptTemplate", "API返回的数据标题：{{title}}\n图表统计数据：{{chartData}}\n\n请根据以上信息生成一段简洁的总结，说明数据概况。",
                        "temperature", 0.5,
                        "enableStreaming", true
                )),
                node("output_ok", "output", 1400, 200, Map.of(
                        "label", "成功输出",
                        "output", "API调用成功！\n\n{{llmOutput}}"
                )),
                node("variable_err", "variable", 900, 480, Map.of(
                        "label", "错误信息",
                        "variableName", "errorMsg",
                        "variableValue", "API调用或解析失败：{{error}}"
                )),
                node("output_err", "output", 1150, 480, Map.of(
                        "label", "错误输出",
                        "output", "{{errorMsg}}"
                )),
                node("end_1", "end", 1400, 350, Map.of())
        );
        List<Map<String, Object>> edges = List.of(
                edge("e_start", "start_1", "api_1"),
                edge("e_api", "api_1", "script_parse"),
                edge("e_parse", "script_parse", "condition_1"),
                edgeHandle("e_ok", "condition_1", "mcp_1", "condition_1_cg_ok", "in"),
                edgeHandle("e_err", "condition_1", "variable_err", "condition_1_cg_err", "in"),
                edgeHandle("e_fallback", "condition_1", "variable_err", "condition_1_default", "in"),
                edge("e_mcp", "mcp_1", "llm_1"),
                edge("e_llm", "llm_1", "output_ok"),
                edge("e_out_ok", "output_ok", "end_1"),
                edge("e_var_err", "variable_err", "output_err"),
                edge("e_out_err", "output_err", "end_1")
        );
        return workflowSnapshot(nodes, edges);
    }

    // ========== 示例 6：多工具协作助手 ==========

    private static Map<String, Object> buildToolMultiWorkflow() {
        List<Map<String, Object>> nodes = List.of(
                node("start_1", "start", 50, 250, Map.of()),
                node("input_1", "input", 200, 250, Map.of(
                        "label", "用户输入",
                        "outputParams", List.of(Map.of("key", "query", "type", "String", "defaultValue", ""))
                )),
                node("extract_1", "parameter_extractor", 400, 250, Map.of(
                        "label", "提取搜索与计算参数",
                        "inputVariable", "{{query}}",
                        "instruction", "从用户描述中提取：search_query（用于联网搜索的关键词）、num_a、num_b（两个数字，缺省为10和5）、operation（add/subtract/multiply/divide 之一，缺省 add）",
                        "extractParams", List.of(
                                Map.of("key", "search_query", "type", "String", "desc", "搜索关键词", "required", true),
                                Map.of("key", "num_a", "type", "Number", "desc", "计算数字A", "required", false),
                                Map.of("key", "num_b", "type", "Number", "desc", "计算数字B", "required", false),
                                Map.of("key", "operation", "type", "String", "desc", "运算类型", "required", false)
                        )
                )),
                node("tool_search", "tool", 650, 150, Map.of(
                        "label", "联网搜索",
                        "toolName", "web_search",
                        "toolId", toolPlaceholder("web_search"),
                        "inputMappings", List.of(
                                Map.of("key", "query", "value", "{{search_query}}"),
                                Map.of("key", "maxResults", "value", "5")
                        ),
                        "outputMappings", List.of(
                                Map.of("key", "searchAnswer", "value", "{{answer}}"),
                                Map.of("key", "searchResults", "value", "{{results}}"),
                                Map.of("key", "toolResult", "value", "{{output}}")
                        )
                )),
                node("tool_calc", "tool", 650, 350, Map.of(
                        "label", "计算器",
                        "toolName", "calculator",
                        "toolId", toolPlaceholder("calculator"),
                        "inputMappings", List.of(
                                Map.of("key", "a", "value", "{{num_a}}"),
                                Map.of("key", "b", "value", "{{num_b}}"),
                                Map.of("key", "operation", "value", "{{operation}}")
                        ),
                        "outputMappings", List.of(
                                Map.of("key", "calcResult", "value", "{{result}}"),
                                Map.of("key", "toolResult", "value", "{{output}}")
                        )
                )),
                node("varhandle_1", "variable_handle", 900, 250, Map.of(
                        "label", "合并工具结果",
                        "handleType", "template",
                        "templateContent", "【联网搜索摘要】\n{{tool_search.searchAnswer}}\n\n【计算结果】\n{{tool_calc.calcResult}}"
                )),
                node("llm_1", "llm", 1100, 250, Map.of(
                        "label", "综合回答",
                        "sysPrompt", "你是研究助手。请结合联网搜索摘要与计算结果，用清晰结构回答用户的原始问题。",
                        "promptTemplate", "用户问题：{{sys.query}}\n\n工具汇总：\n{{output}}",
                        "temperature", 0.5,
                        "enableStreaming", true
                )),
                node("output_1", "output", 1300, 250, Map.of(
                        "label", "输出回答",
                        "output", "{{llmOutput}}"
                )),
                node("end_1", "end", 1500, 250, Map.of())
        );
        List<Map<String, Object>> edges = List.of(
                edge("e_start", "start_1", "input_1"),
                edge("e_input", "input_1", "extract_1"),
                edge("e_extract_search", "extract_1", "tool_search"),
                edge("e_search_calc", "tool_search", "tool_calc"),
                edge("e_calc_vh", "tool_calc", "varhandle_1"),
                edge("e_vh_llm", "varhandle_1", "llm_1"),
                edge("e_llm_out", "llm_1", "output_1"),
                edge("e_output_end", "output_1", "end_1")
        );
        return workflowSnapshot(nodes, edges);
    }

    // ========== 示例 7：高考志愿填报助手（ask_user 链式反问） ==========

    private static Map<String, Object> buildGaokaoVolunteerWorkflow() {
        List<Map<String, Object>> nodes = List.of(
                node("start_1", "start", 50, 280, Map.of()),
                node("input_1", "input", 200, 280, Map.of(
                        "label", "用户诉求",
                        "outputParams", List.of(Map.of("key", "query", "type", "String", "defaultValue", ""))
                )),
                node("ask_province", "tool", 420, 280, Map.of(
                        "label", "询问省市",
                        "toolName", "ask_user",
                        "toolId", toolPlaceholder("ask_user"),
                        "inputMappings", List.of(
                                Map.of("key", "question", "value", "请告诉我您参加高考所在的省份或直辖市："),
                                Map.of("key", "options", "value", "北京,上海,天津,广东,浙江,江苏,山东,河南,湖北,四川,其他省份")
                        ),
                        "outputMappings", List.of(
                                Map.of("key", "province", "value", "{{answer}}"),
                                Map.of("key", "answer", "value", "{{answer}}")
                        )
                )),
                node("ask_score", "tool", 640, 280, Map.of(
                        "label", "询问分数",
                        "toolName", "ask_user",
                        "toolId", toolPlaceholder("ask_user"),
                        "inputMappings", List.of(
                                Map.of("key", "question", "value", "请输入您的高考总分（如有加分请在回答中说明）：")
                        ),
                        "outputMappings", List.of(
                                Map.of("key", "gaokaoScore", "value", "{{answer}}"),
                                Map.of("key", "answer", "value", "{{answer}}")
                        )
                )),
                node("ask_subjects", "tool", 860, 280, Map.of(
                        "label", "询问选科",
                        "toolName", "ask_user",
                        "toolId", toolPlaceholder("ask_user"),
                        "inputMappings", List.of(
                                Map.of("key", "question", "value", "请选择您的选科组合（新高考）或传统文理类别："),
                                Map.of("key", "options", "value", "物理+化学+生物,物理+化学+地理,物理+生物+地理,历史+政治+地理,历史+地理+生物,理科（传统文理）,文科（传统文理）,其他组合（请在下方说明）")
                        ),
                        "outputMappings", List.of(
                                Map.of("key", "subjects", "value", "{{answer}}"),
                                Map.of("key", "answer", "value", "{{answer}}")
                        )
                )),
                node("varhandle_1", "variable_handle", 1080, 280, Map.of(
                        "label", "汇总考生信息",
                        "handleType", "template",
                        "templateContent", """
                                【考生档案】
                                省份/地区：{{province}}
                                高考分数：{{gaokaoScore}}
                                选科组合：{{subjects}}

                                用户补充诉求：{{query}}

                                请基于以上信息给出志愿填报建议（冲稳保院校方向、专业选择思路、注意事项）。"""
                )),
                node("llm_1", "llm", 1300, 280, Map.of(
                        "label", "生成志愿建议",
                        "sysPrompt", """
                                你是资深高考志愿填报顾问，熟悉各省录取规则、选科专业限制与平行志愿策略。
                                请根据考生档案给出结构化、务实的建议，包含：
                                1. 分数段定位与批次判断（定性描述即可）
                                2. 冲稳保院校/专业方向（各 2-3 条示例方向，勿编造具体位次）
                                3. 选科与专业匹配提醒
                                4. 填报注意事项与信息核实建议
                                免责声明：建议仅供参考，请以省教育考试院与高校招生简章为准。""",
                        "promptTemplate", "{{output}}",
                        "temperature", 0.4,
                        "enableStreaming", true
                )),
                node("output_1", "output", 1520, 280, Map.of(
                        "label", "输出建议",
                        "output", "{{llmOutput}}"
                )),
                node("end_1", "end", 1720, 280, Map.of())
        );
        List<Map<String, Object>> edges = List.of(
                edge("e_start", "start_1", "input_1"),
                edge("e_input", "input_1", "ask_province"),
                edge("e_province", "ask_province", "ask_score"),
                edge("e_score", "ask_score", "ask_subjects"),
                edge("e_subjects", "ask_subjects", "varhandle_1"),
                edge("e_vh", "varhandle_1", "llm_1"),
                edge("e_llm", "llm_1", "output_1"),
                edge("e_output", "output_1", "end_1")
        );
        return workflowSnapshot(nodes, edges);
    }

    // ========== 示例 8：人工审核助手 ==========

    private static Map<String, Object> buildHumanReviewWorkflow() {
        List<Map<String, Object>> nodes = List.of(
                node("start_1", "start", 50, 280, Map.of()),
                node("input_1", "input", 200, 280, Map.of(
                        "label", "用户需求",
                        "outputParams", List.of(Map.of("key", "query", "type", "String", "defaultValue", ""))
                )),
                node("llm_draft", "llm", 420, 280, Map.of(
                        "label", "生成草稿",
                        "sysPrompt", "你是专业文案助手。根据用户需求生成一版可直接发布的草稿，语气正式、结构清晰，控制在300字以内。",
                        "promptTemplate", "用户需求：{{query}}",
                        "temperature", 0.7,
                        "enableStreaming", false
                )),
                node("confirm_1", "confirm", 650, 280, Map.of(
                        "label", "人工审核",
                        "message", "请审核下方 AI 生成的草稿，填写审核信息并选择结论后提交",
                        "formFields", List.of(
                                Map.of(
                                        "key", "_draft",
                                        "label", "【待审核草稿】\n{{llmOutput}}",
                                        "type", "info"
                                ),
                                Map.of(
                                        "key", "_tip",
                                        "label", "请核对内容准确性、语气与合规性；驳回时请填写具体修改意见。",
                                        "type", "info"
                                ),
                                Map.of(
                                        "key", "reviewer",
                                        "label", "审核人",
                                        "type", "text",
                                        "required", true,
                                        "defaultValue", ""
                                ),
                                Map.of(
                                        "key", "score",
                                        "label", "内容评分（1-10）",
                                        "type", "number",
                                        "required", false,
                                        "defaultValue", "8"
                                ),
                                Map.of(
                                        "key", "confirmed",
                                        "label", "审核结论",
                                        "type", "radio",
                                        "required", true,
                                        "options", List.of("通过", "驳回")
                                ),
                                Map.of(
                                        "key", "priority",
                                        "label", "处理优先级",
                                        "type", "select",
                                        "required", false,
                                        "options", List.of("普通", "加急", "暂缓")
                                ),
                                Map.of(
                                        "key", "remark",
                                        "label", "审核意见",
                                        "type", "textarea",
                                        "required", false,
                                        "defaultValue", ""
                                )
                        )
                )),
                node("condition_1", "condition", 900, 280, Map.of(
                        "label", "审核分支",
                        "conditionGroups", List.of(
                                conditionGroup("cg_pass", "通过", "and",
                                        List.of(Map.of("variable", "confirmed", "operator", "eq", "value", "通过"))),
                                conditionGroup("cg_reject", "驳回", "and",
                                        List.of(Map.of("variable", "confirmed", "operator", "eq", "value", "驳回")))
                        )
                )),
                node("llm_final", "llm", 1150, 150, Map.of(
                        "label", "定稿润色",
                        "sysPrompt", "你是编辑。在草稿基础上结合审核意见输出最终定稿，直接给出正文，不要解释流程。",
                        "promptTemplate", "【草稿】\n{{llmOutput}}\n\n【审核人】{{reviewer}}\n【评分】{{score}}\n【优先级】{{priority}}\n【审核意见】\n{{remark}}\n\n请输出最终定稿：",
                        "temperature", 0.4,
                        "enableStreaming", false
                )),
                node("variable_reject", "variable", 1150, 420, Map.of(
                        "label", "驳回说明",
                        "variableName", "rejectReply",
                        "variableValue", "内容未通过审核（审核人：{{reviewer}}，评分：{{score}}，优先级：{{priority}}）。意见：{{remark}}"
                )),
                node("output_ok", "output", 1400, 150, Map.of(
                        "label", "输出定稿",
                        "output", "【审核通过 · 正式版本】\n审核人：{{reviewer}}\n\n{{llmOutput}}"
                )),
                node("output_reject", "output", 1400, 420, Map.of(
                        "label", "输出驳回",
                        "output", "{{rejectReply}}"
                )),
                node("output_fallback", "output", 1150, 550, Map.of(
                        "label", "默认输出",
                        "output", "审核结果未识别，请重新发起对话。"
                )),
                node("end_1", "end", 1620, 280, Map.of())
        );
        List<Map<String, Object>> edges = List.of(
                edge("e_start", "start_1", "input_1"),
                edge("e_input", "input_1", "llm_draft"),
                edge("e_draft_confirm", "llm_draft", "confirm_1"),
                edge("e_confirm_cond", "confirm_1", "condition_1"),
                edgeHandle("e_pass", "condition_1", "llm_final", "condition_1_cg_pass", "in"),
                edgeHandle("e_reject", "condition_1", "variable_reject", "condition_1_cg_reject", "in"),
                edgeHandle("e_fallback", "condition_1", "output_fallback", "condition_1_default", "in"),
                edge("e_final_out", "llm_final", "output_ok"),
                edge("e_reject_out", "variable_reject", "output_reject"),
                edge("e_ok_end", "output_ok", "end_1"),
                edge("e_rej_end", "output_reject", "end_1"),
                edge("e_fb_end", "output_fallback", "end_1")
        );
        return workflowSnapshot(nodes, edges);
    }

    // ========== 示例 8：子工作流编排助手 ==========

    /** 子工作流：结构化提取（由主示例创建时自动发布） */
    private static Map<String, Object> buildDataPrepSubWorkflow() {
        List<Map<String, Object>> nodes = List.of(
                node("start_1", "start", 50, 200, Map.of()),
                node("input_1", "input", 200, 200, Map.of(
                        "label", "接收输入",
                        "outputParams", List.of(Map.of("key", "query", "type", "String", "defaultValue", ""))
                )),
                node("extract_1", "parameter_extractor", 420, 200, Map.of(
                        "label", "提取结构化字段",
                        "inputVariable", "{{query}}",
                        "instruction", "从文本中提取 title（标题/主题）、summary（100字内摘要）、tags（逗号分隔关键词，最多5个）",
                        "extractParams", List.of(
                                Map.of("key", "title", "type", "String", "desc", "主题标题", "required", false),
                                Map.of("key", "summary", "type", "String", "desc", "内容摘要", "required", false),
                                Map.of("key", "tags", "type", "String", "desc", "关键词标签", "required", false)
                        )
                )),
                node("varhandle_1", "variable_handle", 650, 200, Map.of(
                        "label", "格式化输出",
                        "handleType", "template",
                        "templateContent", "标题：{{title}}\n摘要：{{summary}}\n标签：{{tags}}"
                )),
                node("output_1", "output", 880, 200, Map.of(
                        "label", "子流程结果",
                        "output", "{{output}}"
                )),
                node("end_1", "end", 1080, 200, Map.of())
        );
        List<Map<String, Object>> edges = List.of(
                edge("e_start", "start_1", "input_1"),
                edge("e_input", "input_1", "extract_1"),
                edge("e_extract", "extract_1", "varhandle_1"),
                edge("e_vh", "varhandle_1", "output_1"),
                edge("e_output", "output_1", "end_1")
        );
        return workflowSnapshot(nodes, edges);
    }

    private static Map<String, Object> buildSubWorkflowOrchestrator() {
        List<Map<String, Object>> nodes = List.of(
                node("start_1", "start", 50, 280, Map.of()),
                node("input_1", "input", 200, 280, Map.of(
                        "label", "原始需求",
                        "outputParams", List.of(Map.of("key", "query", "type", "String", "defaultValue", ""))
                )),
                node("sub_1", "app_component", 420, 280, Map.of(
                        "label", "调用子工作流",
                        "componentType", "workflow",
                        "componentCode", subPlaceholder("data_prep_sub"),
                        "componentName", "结构化提取模块",
                        "streamSwitch", false,
                        "inputMappings", List.of(Map.of("key", "query", "value", "{{query}}")),
                        "outputMappings", List.of(
                                Map.of("key", "subResult", "value", "{{output}}"),
                                Map.of("key", "result", "value", "{{result}}")
                        )
                )),
                node("confirm_1", "confirm", 680, 280, Map.of(
                        "label", "复核子流程结果",
                        "message", "请核对子工作流提取的结构化信息是否准确，确认后继续生成正式回复",
                        "formFields", List.of(
                                Map.of("key", "_sub_preview", "label", "【子工作流输出】\n{{subResult}}", "type", "info"),
                                Map.of("key", "confirmed", "label", "是否确认继续", "type", "radio", "required", true,
                                        "options", List.of("确认", "退回修改")),
                                Map.of("key", "remark", "label", "补充说明", "type", "textarea", "required", false, "defaultValue", "")
                        )
                )),
                node("condition_1", "condition", 940, 280, Map.of(
                        "label", "复核分支",
                        "conditionGroups", List.of(
                                conditionGroup("cg_pass", "确认", "and",
                                        List.of(Map.of("variable", "confirmed", "operator", "eq", "value", "确认"))),
                                conditionGroup("cg_reject", "退回修改", "and",
                                        List.of(Map.of("variable", "confirmed", "operator", "eq", "value", "退回修改")))
                        )
                )),
                node("llm_1", "llm", 1180, 150, Map.of(
                        "label", "生成正式回复",
                        "sysPrompt", "你是方案顾问。基于原始需求、子工作流结构化结果与用户复核意见，输出条理清晰的正式回复。",
                        "promptTemplate", "【原始需求】\n{{query}}\n\n【子工作流结果】\n{{subResult}}\n\n【复核意见】\n{{remark}}\n\n请输出正式回复：",
                        "temperature", 0.5,
                        "enableStreaming", true
                )),
                node("variable_reject", "variable", 1180, 420, Map.of(
                        "label", "退回说明",
                        "variableName", "rejectReply",
                        "variableValue", "已退回：子工作流结果需要调整。说明：{{remark}}"
                )),
                node("output_ok", "output", 1420, 150, Map.of(
                        "label", "正式输出",
                        "output", "{{llmOutput}}"
                )),
                node("output_reject", "output", 1420, 420, Map.of(
                        "label", "退回输出",
                        "output", "{{rejectReply}}"
                )),
                node("output_fallback", "output", 1180, 550, Map.of(
                        "label", "默认输出",
                        "output", "复核结果未识别，请重新发起。"
                )),
                node("end_1", "end", 1640, 280, Map.of())
        );
        List<Map<String, Object>> edges = List.of(
                edge("e_start", "start_1", "input_1"),
                edge("e_input", "input_1", "sub_1"),
                edge("e_sub_confirm", "sub_1", "confirm_1"),
                edge("e_confirm_cond", "confirm_1", "condition_1"),
                edgeHandle("e_pass", "condition_1", "llm_1", "condition_1_cg_pass", "in"),
                edgeHandle("e_reject", "condition_1", "variable_reject", "condition_1_cg_reject", "in"),
                edgeHandle("e_fallback", "condition_1", "output_fallback", "condition_1_default", "in"),
                edge("e_llm_out", "llm_1", "output_ok"),
                edge("e_rej_out", "variable_reject", "output_reject"),
                edge("e_ok_end", "output_ok", "end_1"),
                edge("e_rej_end", "output_reject", "end_1"),
                edge("e_fb_end", "output_fallback", "end_1")
        );
        return workflowSnapshot(nodes, edges);
    }

    // ========== 示例 10：SubAgent 委派助手 ==========

    private static Map<String, Object> buildSubAgentDelegateWorkflow() {
        List<Map<String, Object>> nodes = List.of(
                node("start_1", "start", 50, 200, Map.of()),
                node("input_1", "input", 220, 200, Map.of(
                        "label", "用户需求",
                        "outputParams", List.of(Map.of("key", "query", "type", "String", "defaultValue", ""))
                )),
                node("sa_1", "sub_agent", 460, 200, Map.of(
                        "label", "SubAgent 摘要",
                        "subAgentId", capabilitySubAgentPlaceholder("example_summarizer"),
                        "subAgentName", "示例摘要助手",
                        "task", "请将以下内容整理为简洁摘要（3-5 条要点）：\n{{query}}",
                        "streamSwitch", false,
                        "outputMappings", List.of(Map.of("key", "reply", "value", "{{reply}}"))
                )),
                node("output_1", "output", 720, 200, Map.of(
                        "label", "输出摘要",
                        "output", "{{reply}}"
                )),
                node("end_1", "end", 940, 200, Map.of())
        );
        List<Map<String, Object>> edges = List.of(
                edge("e_start", "start_1", "input_1"),
                edge("e_input", "input_1", "sa_1"),
                edge("e_sa", "sa_1", "output_1"),
                edge("e_out", "output_1", "end_1")
        );
        return workflowSnapshot(nodes, edges);
    }

    // ========== 工具方法 ==========

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopyMap(Map<?, ?> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            copy.put(String.valueOf(entry.getKey()), deepCopyValue(entry.getValue()));
        }
        return copy;
    }

    private static Object deepCopyValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return deepCopyMap(map);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object item : list) {
                copy.add(deepCopyValue(item));
            }
            return copy;
        }
        return value;
    }

    private static String toolPlaceholder(String toolName) {
        return TOOL_PLACEHOLDER_PREFIX + toolName + "__";
    }

    private static String subPlaceholder(String subKey) {
        return SUB_PLACEHOLDER_PREFIX + subKey + "__";
    }

    private static String capabilitySubAgentPlaceholder(String saKey) {
        return CAPABILITY_SUBAGENT_PLACEHOLDER_PREFIX + saKey + "__";
    }

    @SuppressWarnings("unchecked")
    private static void resolveToolPlaceholder(Map<String, Object> dataMap, Map<String, Long> toolIds) {
        Object raw = dataMap.get("toolId");
        if (!(raw instanceof String placeholder) || !placeholder.startsWith(TOOL_PLACEHOLDER_PREFIX)) {
            return;
        }
        String toolName = placeholder.substring(TOOL_PLACEHOLDER_PREFIX.length()).replace("__", "");
        Long toolId = toolIds.get(toolName);
        if (toolId != null) {
            dataMap.put("toolId", String.valueOf(toolId));
        }
    }

    @SuppressWarnings("unchecked")
    private static void resolveSubWorkflowPlaceholder(Map<String, Object> dataMap, Map<String, Long> subWorkflowAgentIds) {
        Object raw = dataMap.get("componentCode");
        if (!(raw instanceof String placeholder) || !placeholder.startsWith(SUB_PLACEHOLDER_PREFIX)) {
            return;
        }
        String subKey = placeholder.substring(SUB_PLACEHOLDER_PREFIX.length()).replace("__", "");
        Long subAgentId = subWorkflowAgentIds.get(subKey);
        if (subAgentId != null) {
            dataMap.put("componentCode", String.valueOf(subAgentId));
        }
    }

    private static void resolveCapabilitySubAgentPlaceholder(Map<String, Object> dataMap,
                                                            Map<String, Long> capabilitySubAgentIds) {
        Object raw = dataMap.get("subAgentId");
        if (!(raw instanceof String placeholder)
                || !placeholder.startsWith(CAPABILITY_SUBAGENT_PLACEHOLDER_PREFIX)) {
            return;
        }
        String saKey = placeholder.substring(CAPABILITY_SUBAGENT_PLACEHOLDER_PREFIX.length()).replace("__", "");
        Long subAgentId = capabilitySubAgentIds.get(saKey);
        if (subAgentId != null) {
            dataMap.put("subAgentId", String.valueOf(subAgentId));
            String displayName = CAPABILITY_SUBAGENT_DISPLAY_NAMES.get(saKey);
            if (displayName != null && !displayName.isBlank()) {
                dataMap.put("subAgentName", displayName);
            }
        }
    }

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

    /** 条件组（右侧动态出口，handle = {nodeId}_{id}） */
    private static Map<String, Object> conditionGroup(String id, String label, String relation,
                                                      List<Map<String, Object>> rules) {
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("id", id);
        g.put("label", label);
        g.put("relation", relation);
        g.put("rules", rules);
        return g;
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
        Map<String, Object> parent = null;
        for (Map<String, Object> n : nodes) {
            if (parentId.equals(n.get("id"))) {
                parent = n;
                break;
            }
        }
        for (Map<String, Object> n : nodes) {
            if (childId.equals(n.get("id"))) {
                if (parent != null) {
                    normalizeChildPosition(n, parent);
                }
                n.put("parentNode", parentId);
                n.put("extent", "parent");
                break;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void normalizeChildPosition(Map<String, Object> child, Map<String, Object> parent) {
        Object childPositionObj = child.get("position");
        Object parentPositionObj = parent.get("position");
        if (!(childPositionObj instanceof Map<?, ?> childPosition)
                || !(parentPositionObj instanceof Map<?, ?> parentPosition)) {
            return;
        }
        double childX = parseDouble(childPosition.get("x"));
        double childY = parseDouble(childPosition.get("y"));
        double parentX = parseDouble(parentPosition.get("x"));
        double parentY = parseDouble(parentPosition.get("y"));
        child.put("position", Map.of(
                "x", Math.max(24, childX - parentX),
                "y", Math.max(56, childY - parentY)
        ));
    }

    private static double parseDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(value.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return 0D;
    }
}
