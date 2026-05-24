package com.lightbot.subagent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.entity.SubAgent;
import com.lightbot.mapper.SubAgentMapper;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 内置 SubAgent 配置
     */
    private static final List<Map<String, Object>> DEFAULT_SUBAGENTS = List.of(
            Map.of(
                    "name", "research-agent",
                    "displayName", "深度研究员",
                    "description", "利用搜索工具进行深入研究，将调研结果整理并返回。",
                    "systemPrompt", """
你是一位专注的研究员。你的工作是根据用户的问题进行深入研究。
进行彻底的研究，然后用详细的答案回复用户的问题。
你的最终报告应该包含：
1. 问题背景分析
2. 关键发现和洞察
3. 详细的数据和事实支撑
4. 总结和建议

请用结构化的方式呈现研究结果，确保内容详实、准确、有价值。""",
                    "tools", List.of("web_search")
            ),
            Map.of(
                    "name", "critique-agent",
                    "displayName", "内容审核员",
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
                subAgent.setDescription((String) data.get("description"));
                subAgent.setSystemPrompt((String) data.get("systemPrompt"));
                subAgent.setTools(toJson((List<String>) data.get("tools")));
                subAgent.setEnabled(1);
                subAgent.setIsBuiltin(1);
                subAgentMapper.insert(subAgent);
                log.info("[BuiltInSubAgentRegistrar] 注册内置 SubAgent: name={}", name);
            } else {
                // 已存在，更新 display_name、description、system_prompt、tools（保持代码定义同步）
                existing.setDisplayName((String) data.get("displayName"));
                existing.setDescription((String) data.get("description"));
                existing.setSystemPrompt((String) data.get("systemPrompt"));
                existing.setTools(toJson((List<String>) data.get("tools")));
                subAgentMapper.updateById(existing);
                log.info("[BuiltInSubAgentRegistrar] 更新内置 SubAgent: name={}", name);
            }
        }

        log.info("[BuiltInSubAgentRegistrar] 内置 SubAgent 注册完成: 共 {} 个", DEFAULT_SUBAGENTS.size());
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
}