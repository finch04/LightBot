package com.lightbot.subagent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.entity.SubAgent;
import com.lightbot.service.SubAgentService;
import com.lightbot.service.chat.ChatContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SubAgent 委派工具
 * <p>对标 Yuxi 的 {@code task} 工具：主 Agent 通过调用本工具把子任务交给 SubAgent 执行，
 * 工具内部使用 {@link SubAgentRuntime} 完成非流式工具循环并返回最终回答。</p>
 *
 * <p>工具的 description 会列出当前 Agent 实际绑定且启用的 SubAgent 名称与用途，
 * 帮助主 Agent 在 prompt 中判断「何时该委派 / 委派给谁」。</p>
 *
 * @author finch
 * @since 2026-05-28
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DelegateSubAgentTool {

    public static final String TOOL_NAME = "delegate_to_subagent";

    private final SubAgentService subAgentService;
    private final SubAgentRuntime subAgentRuntime;
    private final ObjectMapper objectMapper;

    /**
     * 为给定的一组 SubAgent ID 构造一个动态工具回调。
     * 工具描述包含子 Agent 列表，输入参数为 subagent_name + task + thread_id(可选)。
     *
     * @param boundSubAgentIds 当前 Agent 绑定的 SubAgent ID 列表
     * @return ToolCallback，无可用 SubAgent 时返回 null
     */
    public ToolCallback buildCallback(List<Long> boundSubAgentIds) {
        if (boundSubAgentIds == null || boundSubAgentIds.isEmpty()) {
            return null;
        }
        List<SubAgent> subs = subAgentService.listByIds(boundSubAgentIds).stream()
                .filter(s -> s != null && Integer.valueOf(1).equals(s.getEnabled()))
                .toList();
        if (subs.isEmpty()) {
            return null;
        }

        // 1. 构造描述
        String catalog = subs.stream()
                .map(s -> "- " + s.getName() + "（" + (s.getDisplayName() != null ? s.getDisplayName() : s.getName()) + "）: "
                        + (s.getDescription() != null ? s.getDescription() : ""))
                .collect(Collectors.joining("\n"));
        String description = """
                把当前子任务委派给一个专门的子智能体执行，并返回它的最终回答。
                可委派的子智能体清单（必须严格使用 name 字段，不能编造名称）：
                """ + catalog + """

                使用规则：
                1. 仅当子任务符合某个子智能体的专长时，才调用本工具；否则继续自己回答。
                2. task 字段务必写完整、自包含的指令（背景 + 目标 + 期望产物），子智能体看不到主对话历史。
                3. 子智能体会同步返回结果，主 Agent 需基于其回答继续对话或汇总。
                4. thread_id 可选：传入已有线程 ID 可续跑上次会话，不传则自动创建新线程。
                """;

        // 2. JSON Schema
        String namesEnum = subs.stream()
                .map(s -> "\"" + s.getName() + "\"")
                .collect(Collectors.joining(", "));
        String inputSchema = """
                {
                  "type": "object",
                  "properties": {
                    "subagent_name": {
                      "type": "string",
                      "enum": [%s],
                      "description": "目标子智能体的 name"
                    },
                    "task": {
                      "type": "string",
                      "description": "完整的子任务描述（背景 + 目标 + 期望产物）"
                    },
                    "thread_id": {
                      "type": "string",
                      "description": "子代理线程 ID，传入时续跑已有会话，不传时新建"
                    }
                  },
                  "required": ["subagent_name", "task"]
                }
                """.formatted(namesEnum);

        // 3. 索引 name -> SubAgent，便于 invoke 时快速查找
        Map<String, SubAgent> byName = new HashMap<>();
        for (SubAgent sa : subs) {
            byName.put(sa.getName(), sa);
        }

        ToolDefinition definition = DefaultToolDefinition.builder()
                .name(TOOL_NAME)
                .description(description)
                .inputSchema(inputSchema)
                .build();

        return new DelegateCallback(definition, byName, subAgentRuntime, objectMapper);
    }

    /** 自定义 ToolCallback：在 call 中解析参数并委派 */
    private static class DelegateCallback implements ToolCallback {
        private final ToolDefinition definition;
        private final Map<String, SubAgent> byName;
        private final SubAgentRuntime runtime;
        private final ObjectMapper objectMapper;

        DelegateCallback(ToolDefinition definition, Map<String, SubAgent> byName, SubAgentRuntime runtime, ObjectMapper objectMapper) {
            this.definition = definition;
            this.byName = byName;
            this.runtime = runtime;
            this.objectMapper = objectMapper;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return definition;
        }

        @Override
        public ToolMetadata getToolMetadata() {
            return ToolMetadata.builder().returnDirect(false).build();
        }

        @Override
        public String call(String toolInput) {
            return call(toolInput, null);
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            String subName;
            String task;
            String threadId;
            try {
                Map<String, Object> args = objectMapper.readValue(
                        toolInput != null ? toolInput : "{}", new TypeReference<>() {});
                subName = args.get("subagent_name") != null ? args.get("subagent_name").toString() : null;
                task = args.get("task") != null ? args.get("task").toString() : null;
                threadId = args.get("thread_id") != null ? args.get("thread_id").toString() : null;
            } catch (Exception e) {
                return "参数解析失败: " + e.getMessage();
            }
            if (subName == null || subName.isBlank()) {
                return "缺少 subagent_name 参数";
            }
            if (task == null || task.isBlank()) {
                return "缺少 task 参数";
            }
            SubAgent target = byName.get(subName);
            if (target == null) {
                return "未在当前 Agent 绑定列表中找到子智能体: " + subName + "，可选: " + byName.keySet();
            }
            Long providerId = null;
            String requestId = null;
            String parentThreadId = null;
            ChatContext chatContext = null;
            if (toolContext != null && toolContext.getContext() != null) {
                Object pid = toolContext.getContext().get("providerId");
                if (pid instanceof Number n) providerId = n.longValue();
                Object rid = toolContext.getContext().get("requestId");
                if (rid != null) requestId = rid.toString();
                Object ptid = toolContext.getContext().get("parentThreadId");
                if (ptid != null) parentThreadId = ptid.toString();
                Object cctx = toolContext.getContext().get("chatContext");
                if (cctx instanceof ChatContext cc) chatContext = cc;
            }
            SubAgentRuntime.SubAgentResult result = runtime.run(target, task, providerId, requestId, threadId, parentThreadId, chatContext);
            // 返回 JSON 格式结果，包含 thread_id 供主 Agent 后续续跑
            try {
                Map<String, Object> out = new HashMap<>();
                out.put("reply", result.reply());
                out.put("thread_id", result.threadId());
                out.put("continued", result.continued());
                return objectMapper.writeValueAsString(out);
            } catch (Exception e) {
                return result.reply();
            }
        }
    }
}
