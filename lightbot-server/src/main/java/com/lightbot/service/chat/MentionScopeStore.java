package com.lightbot.service.chat;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Mention 知识库范围跨线程传递存储。
 * <p>{@link com.lightbot.tool.builtin.QueryKnowledgeTool} 在 lightBotExecutor 线程池执行，
 * 无法直接读取 {@link ChatContext}，改用 Caffeine Cache 以 requestId 为 key 传递 mention 收窄范围。
 * 模式参考 {@code QueryKnowledgeTool.SEARCH_RESULTS_CACHE}。</p>
 *
 * @author finch
 * @since 2026-06-29
 */
@Slf4j
@Component
public class MentionScopeStore {

    private static final Cache<String, Set<Long>> KNOWLEDGE_SCOPE_CACHE =
            Caffeine.newBuilder().maximumSize(1000).expireAfterWrite(5, TimeUnit.MINUTES).build();

    /**
     * 写入本轮 mention 收窄的知识库 ID 集合
     *
     * @param requestId    请求 ID（同 ToolContext 中的 requestId）
     * @param knowledgeIds 收窄后的知识库 ID 集合，null 或空表示无 mention
     */
    public void putKnowledgeScope(String requestId, Set<Long> knowledgeIds) {
        if (requestId == null || knowledgeIds == null || knowledgeIds.isEmpty()) {
            return;
        }
        KNOWLEDGE_SCOPE_CACHE.put(requestId, knowledgeIds);
    }

    /**
     * 读取本轮 mention 收窄的知识库 ID 集合
     *
     * @param requestId 请求 ID
     * @return 收窄集合，null 表示无 mention（沿用原检索逻辑）
     */
    public Set<Long> getKnowledgeScope(String requestId) {
        if (requestId == null) {
            return null;
        }
        return KNOWLEDGE_SCOPE_CACHE.getIfPresent(requestId);
    }

    /**
     * 读取后立即失效，避免缓存残留污染后续同名 requestId
     *
     * @param requestId 请求 ID
     */
    public void invalidate(String requestId) {
        if (requestId != null) {
            KNOWLEDGE_SCOPE_CACHE.invalidate(requestId);
        }
    }
}
