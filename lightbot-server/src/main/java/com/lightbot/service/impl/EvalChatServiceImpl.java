package com.lightbot.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.BizException;
import com.lightbot.dto.EvalScoreResult;
import com.lightbot.enums.ErrorCode;
import com.lightbot.model.ModelFactory;
import com.lightbot.service.EvalChatService;
import com.lightbot.service.LlmCallStatsService;
import com.lightbot.service.TokenBudgetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 评测对话服务实现类
 *
 * @author finch
 * @since 2026-05-27
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvalChatServiceImpl implements EvalChatService {

    private final ModelFactory modelFactory;
    private final TokenBudgetService tokenBudgetService;
    private final LlmCallStatsService llmCallStatsService;
    private final ObjectMapper objectMapper;

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(\\w+)}}");

    private static final String EVALUATOR_SYSTEM_PROMPT =
            "\n\n按照Json格式返回评估结果。例如\n"
            + "{\"score\":\"0.85\",\"reason\":\"回答基本正确，准确回答了用户关于人工智能的问题。\"}\n"
            + "只返回Json字符串，不要有其他任何内容。";

    @Override
    public String callPrompt(String modelConfig, String promptTemplate, String variables) {
        // 1. 解析模型配置
        Map<String, Object> config = parseModelConfig(modelConfig);
        Long providerId = getProviderId(config);

        // 2. 替换模板变量
        String renderedPrompt = replaceVariables(promptTemplate, variables);

        // 3. 预估 Token 并检查预算
        int estimatedTokens = estimateTokens(renderedPrompt);
        tokenBudgetService.checkBudget(0L, estimatedTokens);

        // 4. 获取ChatModel并调用
        ChatModel chatModel = modelFactory.getChatModel(providerId);
        ChatOptions options = modelFactory.buildChatOptions(providerId, config);
        UserMessage userMessage = new UserMessage(renderedPrompt);
        Prompt chatPrompt = new Prompt(userMessage, options);

        long startTime = System.currentTimeMillis();
        try {
            ChatResponse response = chatModel.call(chatPrompt);
            long latency = System.currentTimeMillis() - startTime;
            // 记录调用统计
            recordCallStats(response, latency);
            return response.getResult().getOutput().getText();
        } catch (Exception e) {
            llmCallStatsService.recordFailure(0L, System.currentTimeMillis() - startTime);
            throw e;
        }
    }

    @Override
    public Flux<String> callPromptStream(String modelConfig, String promptTemplate, String variables) {
        // 1. 解析模型配置
        Map<String, Object> config = parseModelConfig(modelConfig);
        Long providerId = getProviderId(config);

        // 2. 替换模板变量
        String renderedPrompt = replaceVariables(promptTemplate, variables);

        // 3. 获取ChatModel并流式调用
        ChatModel chatModel = modelFactory.getChatModel(providerId);
        ChatOptions options = modelFactory.buildChatOptions(providerId, config);
        UserMessage userMessage = new UserMessage(renderedPrompt);
        Prompt chatPrompt = new Prompt(userMessage, options);
        return chatModel.stream(chatPrompt)
                .map(response -> {
                    if (response.getResult() == null || response.getResult().getOutput() == null) {
                        return "";
                    }
                    String text = response.getResult().getOutput().getText();
                    return text != null ? text : "";
                })
                .filter(text -> !text.isEmpty());
    }

    @Override
    public EvalScoreResult callEvaluator(String modelConfig, String promptTemplate, String variables) {
        // 1. 解析模型配置
        Map<String, Object> config = parseModelConfig(modelConfig);
        Long providerId = getProviderId(config);

        // 2. 替换模板变量并追加系统指令
        String renderedPrompt = replaceVariables(promptTemplate, variables) + EVALUATOR_SYSTEM_PROMPT;

        // 3. 预估 Token 并检查预算
        int estimatedTokens = estimateTokens(renderedPrompt);
        tokenBudgetService.checkBudget(0L, estimatedTokens);

        // 4. 获取ChatModel并调用
        ChatModel chatModel = modelFactory.getChatModel(providerId);
        ChatOptions options = modelFactory.buildChatOptions(providerId, config);
        UserMessage userMessage = new UserMessage(renderedPrompt);
        Prompt chatPrompt = new Prompt(userMessage, options);

        long startTime = System.currentTimeMillis();
        try {
            ChatResponse response = chatModel.call(chatPrompt);
            long latency = System.currentTimeMillis() - startTime;
            recordCallStats(response, latency);
            String content = response.getResult().getOutput().getText();
            return parseScoreResult(content);
        } catch (Exception e) {
            llmCallStatsService.recordFailure(0L, System.currentTimeMillis() - startTime);
            throw e;
        }
    }

    @Override
    public String replaceVariables(String template, String variablesJson) {
        if (template == null || variablesJson == null || variablesJson.isBlank()) {
            return template;
        }
        try {
            Map<String, String> variables = objectMapper.readValue(variablesJson, new TypeReference<>() {});
            String result = template;
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
            }
            return result;
        } catch (Exception e) {
            log.warn("[EvalChatService] 变量替换失败: {}", e.getMessage());
            return template;
        }
    }

    /**
     * 解析LLM返回的评分JSON
     */
    private EvalScoreResult parseScoreResult(String content) {
        // 1. 提取JSON（可能包含在markdown代码块中）
        String json = extractJson(content);
        try {
            EvalScoreResult result = objectMapper.readValue(json, EvalScoreResult.class);
            if (result.getScore() == null) {
                result.setScore(BigDecimal.ZERO);
            }
            return result;
        } catch (Exception e) {
            log.warn("[EvalChatService] 评分结果解析失败, content={}, error={}", content, e.getMessage());
            EvalScoreResult fallback = new EvalScoreResult();
            fallback.setScore(BigDecimal.ZERO);
            fallback.setReason("评分结果解析失败: " + content);
            return fallback;
        }
    }

    /**
     * 从LLM响应中提取JSON字符串（兼容markdown代码块格式）
     */
    private String extractJson(String content) {
        if (content == null) {
            return "{}";
        }
        // 去除markdown代码块
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                trimmed = trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        // 提取JSON对象
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    /**
     * 粗估 Token 数（中文约 1.5 token/字，英文约 0.75 token/word）
     */
    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        // 简单估算：字符数 * 1.2
        return (int) (text.length() * 1.2);
    }

    /**
     * 从 ChatResponse 中提取 usage 并记录统计
     */
    private void recordCallStats(ChatResponse response, long latencyMs) {
        try {
            if (response != null && response.getMetadata() != null) {
                var usage = response.getMetadata().getUsage();
                if (usage != null) {
                    int promptTokens = (int) usage.getPromptTokens();
                    int completionTokens = (int) usage.getCompletionTokens();
                    tokenBudgetService.recordUsage(0L, promptTokens, completionTokens);
                    llmCallStatsService.recordSuccess(0L, promptTokens, completionTokens, latencyMs);
                    return;
                }
            }
            // 无法提取 usage 时，记录调用成功但 token 为 0
            llmCallStatsService.recordSuccess(0L, 0, 0, latencyMs);
        } catch (Exception e) {
            log.debug("[EvalChatService] 记录调用统计失败: {}", e.getMessage());
        }
    }

    /**
     * 解析模型配置JSON
     */
    private Map<String, Object> parseModelConfig(String modelConfig) {
        if (modelConfig == null || modelConfig.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(modelConfig, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("[EvalChatService] 模型配置解析失败: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * 从配置中获取providerId，未配置时取第一个可用的
     */
    private Long getProviderId(Map<String, Object> config) {
        Object providerId = config.get("providerId");
        if (providerId instanceof Number) {
            return ((Number) providerId).longValue();
        }
        if (providerId instanceof String) {
            return Long.parseLong((String) providerId);
        }
        // 默认取第一个可用的provider
        var ids = modelFactory.getAvailableProviderIds();
        if (ids.isEmpty()) {
            throw new BizException(ErrorCode.AI_NO_PROVIDER);
        }
        return ids.get(0);
    }
}
