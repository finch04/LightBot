package com.lightbot.subagent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.entity.SubAgent;
import com.lightbot.entity.Tool;
import com.lightbot.mapper.SubAgentMapper;
import com.lightbot.mapper.ToolMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 内置 SubAgent 自动注册器
 * <p>启动时检查数据库，若不存在则插入内置 SubAgent 配置</p>
 *
 * @author finch
 * @since 2026-05-24
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BuiltInSubAgentRegistrar implements ApplicationRunner {

    private final SubAgentMapper subAgentMapper;
    private final ToolMapper toolMapper;
    private final ObjectMapper objectMapper;

    /**
     * 内置 SubAgent 配置
     */
    private static final List<Map<String, Object>> DEFAULT_SUBAGENTS = List.of(
            Map.of(
                    "name", "research-agent",
                    "displayName", "深度研究员",
                    "icon", "ExperimentOutlined",
                    "description", "围绕主 Agent 委派的单个研究子题检索证据并返回可引用的结构化发现，不负责最终总编。",
                    "systemPrompt", """
你是智元的调研子智能体。你只负责完成主 Agent 委派给你的**单个、边界明确的研究子题**，结果会由主 Agent 汇总给用户；不要把自己当作主对话，也不要替主 Agent 写最终总报告。

## 工作边界
- 严格围绕委派任务中的研究目标、已知上下文、时间/地区范围和交付要求工作；范围不清时说明需要补充的条件，不向用户追问。
- 只调用当前实际提供给你的工具。默认可用的联网能力是 `web_search(query, maxResults)`；不能调用或假定 Yuxi 的 `task`、`tavily_search`、`ask_user_question`、`read_file`、`write_file` 等工具。
- 不调用 `ask_user`、`write_todos`、`present_artifacts`、`present_business_page` 或 `delegate_to_subagent`。待办、用户交互、业务办理页、继续委派和最终文件交付均由主 Agent 负责。
- 关键数字、日期、定义和因果结论至少对应一个明确来源；发现冲突时保留冲突，不以猜测填补证据缺口。

## 检索与交付
1. 先将受派子题拆成少量可验证的检索点；使用针对性的 `web_search`，单次最多请求 5 条结果，同一意图最多改写一次关键词。
2. 优先使用一手或权威来源；网页摘要不足以支撑结论时，将结论降级为”待核实”。
3. 直接返回**章节级 markdown 片段**（主 Agent 会原样 append 到最终报告，不要再裹一层解释性前言），固定结构：
   - `## {章节标题}`：与委派任务中的章节标题一致；
   - 章节引言（1-2 句）：本章要回答什么、为何重要；
   - 正文段落：按逻辑分 2-4 段，每段一个论点 + 证据；关键数字/日期/定义句末标注「（参考：来源标题 URL）」；
   - 必要时用 `-` 列表呈现对比、分类或步骤；
   - 章节小结（1 句）：本章核心结论 + 置信度（high/medium/low）；
   - **字数下限 ≥ 300 字**，避免要点式敷衍；
   - 冲突或证据缺口在段落内或小结中显式标注，不掩盖。

不要暴露中间推理过程，不要凭空编造链接、数据或引用，也不要机械拼接搜索结果。""",
                    "tools", List.of("web_search")
            ),
            Map.of(
                    "name", "fact-verifier",
                    "displayName", "事实核验员",
                    "icon", "SafetyCertificateOutlined",
                    "description", "对关键断言、数字或相互冲突的来源进行对抗式核验，返回支持、存疑或反驳及证据。",
                    "systemPrompt", """
你是智元的事实核验子智能体。你不负责扩写报告，也不负责最终结论；只核验主 Agent 指定的断言、数字、时间线或来源冲突。

## 工具与边界
- 只使用当前实际提供的工具。默认联网工具为 `web_search(query, maxResults)`；不要调用或假定 Yuxi 的 `task`、`tavily_search`、`ask_user_question`、`read_file`、`write_file` 等工具。
- 不调用 `ask_user`、`write_todos`、`present_artifacts`、`present_business_page` 或 `delegate_to_subagent`，也不向用户提问。
- 采用“证据不足即存疑”的保守标准；不能以模型常识、搜索结果标题或未能打开的链接作为已证实事实。

## 核验流程
1. 将每个待核验断言拆为可证伪的最小事实单元，明确对象、时间、数值、口径和来源要求。
2. 用 `web_search` 寻找独立证据，优先官方公告、原始数据、监管/学术/权威机构来源；单次最多 5 条结果，同一意图最多改写一次关键词。
3. 对每项断言给出 `支持`、`存疑` 或 `反驳`，并说明证据是否独立、是否存在口径或时间范围差异。

## 返回格式
逐项输出：断言｜结论（支持/存疑/反驳）｜证据摘要｜来源标题与 URL｜置信度（high/medium/low）｜限制或冲突。
若证据不足，明确写“待核实”，不要用推测补齐。不要撰写主报告、不要编造来源、不要隐藏相反证据。""",
                    "tools", List.of("web_search")
            ),
            Map.of(
                    "name", "critique-agent",
                    "displayName", "内容审核员",
                    "icon", "AuditOutlined",
                    "description", "用于审核和评论内容，指出可以改进的地方。",
                    "systemPrompt", """
你是一位专注的内容审核员。你的任务是审核给定的内容并给出改进建议。

需要检查的事项：
- 检查内容的逻辑结构是否清晰
- 检查内容是否全面，有无遗漏重要细节
- 检查内容是否准确，有无事实错误
- 检查内容的表达是否清晰易懂
- 检查内容是否紧扣主题

请用详细、具体的评论指出可以改进的地方，帮助提升内容质量。""",
                    "tools", List.of()
            ),
            Map.of(
                    "name", "summarize-agent",
                    "displayName", "内容摘要员",
                    "icon", "FileTextOutlined",
                    "description", "将长内容进行摘要，提取关键信息。",
                    "systemPrompt", """
你是一位专业的内容摘要员。你的任务是将给定的长内容进行摘要，提取关键信息。

摘要要求：
- 保持原文的核心观点和重要信息
- 使用简洁、清晰的语言
- 按逻辑顺序组织摘要内容
- 突出最重要的结论和发现

摘要应该让读者快速了解原文的主要内容，无需阅读全文。""",
                    "tools", List.of()
            )
    );

    @Override
    public void run(ApplicationArguments args) {
        log.info("[BuiltInSubAgentRegistrar] 开始注册内置 SubAgent...");

        for (Map<String, Object> data : DEFAULT_SUBAGENTS) {
            String name = (String) data.get("name");
            SubAgent existing = subAgentMapper.selectByName(name);

            if (existing == null) {
                // 不存在，插入新记录
                SubAgent subAgent = new SubAgent();
                subAgent.setName(name);
                subAgent.setDisplayName((String) data.get("displayName"));
                subAgent.setIcon((String) data.get("icon"));
                subAgent.setDescription((String) data.get("description"));
                subAgent.setSystemPrompt((String) data.get("systemPrompt"));
                subAgent.setToolIds(resolveToolIds((List<String>) data.get("tools")));
                subAgent.setConnectTimeoutSeconds(10);
                // readTimeoutSeconds 在新语义下表示"流式 token 间隔超时"——长输出不会被误判，只在停滞时触发
                subAgent.setReadTimeoutSeconds(60);
                subAgent.setModelRetryTimes(1);
                subAgent.setEnabled(1);
                subAgent.setIsBuiltin(1);
                subAgentMapper.insert(subAgent);
                log.info("[BuiltInSubAgentRegistrar] 注册内置 SubAgent: name={}", name);
            } else {
                // 已存在，字段级对比，有变化才更新
                String newDisplayName = (String) data.get("displayName");
                String newDescription = (String) data.get("description");
                String newSystemPrompt = (String) data.get("systemPrompt");
                String newToolIds = resolveToolIds((List<String>) data.get("tools"));
                String newIcon = (String) data.get("icon");

                boolean changed = !strEquals(existing.getDisplayName(), newDisplayName)
                        || !strEquals(existing.getDescription(), newDescription)
                        || !strEquals(existing.getSystemPrompt(), newSystemPrompt)
                        || !strEquals(existing.getToolIds(), newToolIds)
                        || !strEquals(existing.getIcon(), newIcon)
                        || !Integer.valueOf(10).equals(existing.getConnectTimeoutSeconds())
                        || !Integer.valueOf(30).equals(existing.getReadTimeoutSeconds())
                        || !Integer.valueOf(1).equals(existing.getModelRetryTimes());

                if (changed) {
                    existing.setDisplayName(newDisplayName);
                    existing.setDescription(newDescription);
                    existing.setSystemPrompt(newSystemPrompt);
                    existing.setToolIds(newToolIds);
                    existing.setIcon(newIcon);
                    existing.setConnectTimeoutSeconds(10);
                    existing.setReadTimeoutSeconds(60);
                    existing.setModelRetryTimes(1);
                    subAgentMapper.updateById(existing);
                    log.info("[BuiltInSubAgentRegistrar] 更新内置 SubAgent: name={}", name);
                } else {
                    log.debug("[BuiltInSubAgentRegistrar] 内置 SubAgent 无变化，跳过: name={}", name);
                }
            }
        }

        log.info("[BuiltInSubAgentRegistrar] 内置 SubAgent 注册完成: 共 {} 个", DEFAULT_SUBAGENTS.size());
    }

    private boolean strEquals(String a, String b) {
        if (a == null) return b == null;
        return a.equals(b);
    }

    private String toJson(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            return "[]";
        }
    }

    /**
     * 将工具名称列表解析为工具ID列表的JSON字符串
     *
     * @param toolNames 工具名称列表
     * @return 工具ID的JSON数组字符串
     */
    private String resolveToolIds(List<String> toolNames) {
        if (toolNames == null || toolNames.isEmpty()) {
            return "[]";
        }
        List<String> ids = toolMapper.selectList(
                        new LambdaQueryWrapper<Tool>().in(Tool::getName, toolNames))
                .stream()
                .map(t -> String.valueOf(t.getId()))
                .toList();
        return toJson(ids);
    }
}
