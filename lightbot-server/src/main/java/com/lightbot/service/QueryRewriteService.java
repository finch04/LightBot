package com.lightbot.service;

import com.lightbot.model.ModelFactory;
import com.lightbot.model.ProviderResolver;
import com.lightbot.util.LlmTraceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 查询改写服务：将用户原始问题改写为更适合知识库检索的查询
 * <p>短查询、模糊查询、代词引用等场景下，改写后能显著提升向量检索召回率。</p>
 *
 * @author finch
 * @since 2026-06-26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryRewriteService {

    private final ModelFactory modelFactory;
    private final ProviderResolver providerResolver;

    private static final String REWRITE_SYSTEM_PROMPT = """
            你是一个查询改写助手。将用户问题改写为更适合知识库向量检索的查询。
            规则：
            1. 保持核心语义不变
            2. 补充隐含的上下文信息（如代词指代、省略的主语）
            3. 将口语化表达转为更正式的检索词
            4. 只输出改写后的查询，不要输出任何解释或前缀
            5. 如果问题已经足够明确，直接原样输出
            """;

    /**
     * 改写查询。失败时静默降级，返回原始查询。
     *
     * @param query 原始用户问题
     * @return 改写后的查询，失败时返回原始问题
     */
    public String rewrite(String query) {
        if (query == null || query.isBlank()) {
            return query;
        }
        try {
            Long providerId = providerResolver.resolve();
            ChatModel chatModel = modelFactory.getChatModel(providerId);

            Prompt prompt = new Prompt(List.of(
                    new SystemMessage(REWRITE_SYSTEM_PROMPT),
                    new UserMessage(query)
            ));
            ChatResponse response = LlmTraceContext.callWithoutTrace(() -> chatModel.call(prompt));
            String rewritten = response.getResult().getOutput().getText().trim();

            if (rewritten.isBlank() || rewritten.equals(query)) {
                return query;
            }
            log.info("[QueryRewrite] 原始查询: {} → 改写: {}", query, rewritten);
            return rewritten;
        } catch (Exception e) {
            log.warn("[QueryRewrite] 查询改写失败，使用原始查询: {}", e.getMessage());
            return query;
        }
    }
}
