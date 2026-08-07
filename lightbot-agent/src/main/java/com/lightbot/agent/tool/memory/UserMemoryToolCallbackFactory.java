package com.lightbot.agent.tool.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.dto.MemoryScope;
import com.lightbot.vo.UserMemoryVO;
import com.lightbot.entity.UserMemory;
import com.lightbot.service.LongMemoryPolicyService;
import com.lightbot.service.UserMemoryService;
import com.lightbot.service.chat.ChatContext;
import com.lightbot.vo.LongMemoryPolicyVO;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 用户长期记忆工具回调工厂
 *
 * @author finch
 * @since 2026-07-09
 */
@Component
@RequiredArgsConstructor
public class UserMemoryToolCallbackFactory {

    public static final String SAVE_TOOL_NAME = "memory_save";
    public static final String SEARCH_TOOL_NAME = "memory_search";
    public static final String DELETE_TOOL_NAME = "memory_delete";

    private final UserMemoryService userMemoryService;
    private final LongMemoryPolicyService longMemoryPolicyService;
    private final ObjectMapper objectMapper;

    /**
     * 构建长期记忆工具回调列表
     *
     * @return 工具回调列表
     */
    public List<ToolCallback> buildCallbacks() {
        return List.of(new SaveMemoryCallback(), new SearchMemoryCallback(), new DeleteMemoryCallback());
    }

    private class SaveMemoryCallback extends BaseMemoryCallback {

        SaveMemoryCallback() {
            super(DefaultToolDefinition.builder()
                    .name(SAVE_TOOL_NAME)
                    .description("""
                            保存用户长期记忆。仅当用户明确要求记住稳定偏好、个人背景、项目事实或长期指令时调用。
                            禁止保存密码、密钥、API Key、Token、隐私联系方式或临时性事实。
                            """)
                    .inputSchema("""
                            {
                              "type": "object",
                              "properties": {
                                "content": {"type": "string", "description": "要保存的简洁记忆内容。"},
                                "memoryType": {"type": "string", "enum": ["preference", "profile", "project_fact", "instruction"], "description": "记忆类型。"},
                                "keywords": {"type": "array", "items": {"type": "string"}, "description": "可选检索关键词。"},
                                "confidence": {"type": "number", "minimum": 0, "maximum": 1, "description": "记忆置信度。"}
                              },
                              "required": ["content"]
                            }
                            """)
                    .build());
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            try {
                Map<String, Object> args = parseArgs(toolInput);
                MemoryScope scope = resolveMemoryScope(toolContext);
                if (scope == null || !scope.isValid()) {
                    return failure("缺少用户上下文，无法保存长期记忆");
                }
                String content = str(args.get("content"));
                if (content == null || content.isBlank()) {
                    return failure("缺少 content 参数");
                }
                UserMemoryVO saved = userMemoryService.saveFromTool(
                        scope,
                        resolveMemoryAgentId(scope, toolContext),
                        resolveSessionId(toolContext),
                        resolveUserMessageId(toolContext),
                        str(args.get("memoryType")),
                        content,
                        stringList(args.get("keywords")),
                        decimal(args.get("confidence")));
                return objectMapper.writeValueAsString(Map.of("success", true, "memory", saved));
            } catch (Exception e) {
                return failure(e.getMessage());
            }
        }
    }

    private class SearchMemoryCallback extends BaseMemoryCallback {

        SearchMemoryCallback() {
            super(DefaultToolDefinition.builder()
                    .name(SEARCH_TOOL_NAME)
                    .description("查询当前用户已启用的长期记忆，用于获取相关偏好、背景或长期指令。")
                    .inputSchema("""
                            {
                              "type": "object",
                              "properties": {
                                "query": {"type": "string", "description": "检索问题或关键词。"},
                                "limit": {"type": "integer", "minimum": 1, "maximum": 10, "description": "最多返回记忆条数。"}
                              },
                              "required": ["query"]
                            }
                            """)
                    .build());
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            try {
                Map<String, Object> args = parseArgs(toolInput);
                MemoryScope scope = resolveMemoryScope(toolContext);
                if (scope == null || !scope.isValid()) {
                    return failure("缺少用户上下文，无法查询长期记忆");
                }
                String query = str(args.get("query"));
                int limit = intVal(args.get("limit"), 5, 1, 10);
                List<UserMemoryVO> memories = userMemoryService.searchForPrompt(
                                scope, resolveMemoryAgentId(scope, toolContext), query, limit)
                        .stream()
                        .map(UserMemoryVO::from)
                        .toList();
                return objectMapper.writeValueAsString(Map.of("success", true, "memories", memories));
            } catch (Exception e) {
                return failure(e.getMessage());
            }
        }
    }

    private class DeleteMemoryCallback extends BaseMemoryCallback {

        DeleteMemoryCallback() {
            super(DefaultToolDefinition.builder()
                    .name(DELETE_TOOL_NAME)
                    .description("当用户要求忘记某条长期记忆时，停用该长期记忆。")
                    .inputSchema("""
                            {
                              "type": "object",
                              "properties": {
                                "memoryId": {"type": "string", "description": "要停用的记忆ID。"}
                              },
                              "required": ["memoryId"]
                            }
                            """)
                    .build());
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            try {
                Map<String, Object> args = parseArgs(toolInput);
                MemoryScope scope = resolveMemoryScope(toolContext);
                Long memoryId = longVal(args.get("memoryId"));
                if (scope == null || !scope.isValid() || memoryId == null) {
                    return failure("缺少用户上下文或 memoryId 参数");
                }
                if (!userMemoryService.disableMemory(scope, memoryId)) {
                    return failure("未找到可停用的长期记忆");
                }
                return objectMapper.writeValueAsString(Map.of("success", true, "memoryId", String.valueOf(memoryId)));
            } catch (Exception e) {
                return failure(e.getMessage());
            }
        }
    }

    private abstract class BaseMemoryCallback implements ToolCallback {

        private final ToolDefinition definition;

        BaseMemoryCallback(ToolDefinition definition) {
            this.definition = definition;
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
    }

    private Map<String, Object> parseArgs(String toolInput) throws Exception {
        if (toolInput == null || toolInput.isBlank()) {
            return Map.of();
        }
        return objectMapper.readValue(toolInput, new TypeReference<>() {});
    }

    private String failure(String message) {
        try {
            return objectMapper.writeValueAsString(Map.of("success", false, "error", message != null ? message : "unknown"));
        } catch (Exception e) {
            return "{\"success\":false}";
        }
    }

    private MemoryScope resolveMemoryScope(ToolContext toolContext) {
        Long apiKeyId = longVal(contextValue(toolContext, "apiKeyId"));
        String externalUserId = str(contextValue(toolContext, "externalUserId"));
        if (apiKeyId == null || externalUserId == null) {
            ChatContext chatContext = resolveChatContext(toolContext);
            if (chatContext != null && chatContext.getRequest() != null) {
                if (apiKeyId == null) {
                    apiKeyId = chatContext.getRequest().getApiKeyId();
                }
                if (externalUserId == null) {
                    externalUserId = chatContext.getRequest().getExternalUserId();
                }
            }
        }
        if (apiKeyId != null && externalUserId != null && !externalUserId.isBlank()) {
            return MemoryScope.external(apiKeyId, externalUserId);
        }
        Long userId = longVal(contextValue(toolContext, "userId"));
        if (userId == null) {
            ChatContext chatContext = resolveChatContext(toolContext);
            userId = chatContext != null ? chatContext.getUserId() : null;
        }
        // 控制台调试：统一走 debug_user_{userId} 命名空间
        return userId != null ? MemoryScope.consoleDebug(userId) : null;
    }

    private Long resolveAgentId(ToolContext toolContext) {
        Object value = contextValue(toolContext, "agentId");
        return longVal(value);
    }

    private Long resolveMemoryAgentId(MemoryScope scope, ToolContext toolContext) {
        if (scope == null) {
            return null;
        }
        try {
            Long apiKeyId = scope.isExternal() ? scope.getApiKeyId() : null;
            LongMemoryPolicyVO policy = longMemoryPolicyService.resolveEffective(apiKeyId);
            return "agent".equalsIgnoreCase(policy.getScope()) ? resolveAgentId(toolContext) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Long resolveSessionId(ToolContext toolContext) {
        Object value = contextValue(toolContext, "sessionId");
        return longVal(value);
    }

    private Long resolveUserMessageId(ToolContext toolContext) {
        ChatContext chatContext = resolveChatContext(toolContext);
        return chatContext != null ? chatContext.getUserMessageId() : null;
    }

    private ChatContext resolveChatContext(ToolContext toolContext) {
        Object value = contextValue(toolContext, "chatContext");
        return value instanceof ChatContext chatContext ? chatContext : null;
    }

    private Object contextValue(ToolContext toolContext, String key) {
        return toolContext != null && toolContext.getContext() != null ? toolContext.getContext().get(key) : null;
    }

    private String str(Object value) {
        return value != null ? value.toString() : null;
    }

    private Long longVal(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private int intVal(Object value, int defaultValue, int min, int max) {
        int parsed = defaultValue;
        if (value instanceof Number n) {
            parsed = n.intValue();
        } else if (value != null && !value.toString().isBlank()) {
            try {
                parsed = Integer.parseInt(value.toString());
            } catch (Exception ignored) {
            }
        }
        return Math.max(min, Math.min(max, parsed));
    }

    private BigDecimal decimal(Object value) {
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> raw)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : raw) {
            if (item != null && !item.toString().isBlank()) {
                result.add(item.toString());
            }
        }
        return result;
    }
}
