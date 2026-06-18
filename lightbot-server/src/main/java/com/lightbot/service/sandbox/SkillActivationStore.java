package com.lightbot.service.sandbox;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Skill 激活状态存储
 * <p>基于 Redis 存储每个会话已激活的 Skill slug 集合，跨轮次持久化。</p>
 *
 * @author finch
 * @since 2026-06-18
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillActivationStore {

    private final RedisUtil redisUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String PREFIX = "skill:activated:";
    private static final long TTL_SECONDS = 24 * 3600; // 24h

    /**
     * 获取会话已激活的 Skill slug 集合
     */
    public Set<String> getActivated(Long sessionId) {
        if (sessionId == null) {
            return new LinkedHashSet<>();
        }
        String json = redisUtil.get(PREFIX + sessionId);
        if (json == null) {
            return new LinkedHashSet<>();
        }
        try {
            List<String> list = objectMapper.readValue(json, new TypeReference<>() {});
            return new LinkedHashSet<>(list);
        } catch (Exception e) {
            log.warn("[SkillActivation] 解析激活状态失败: sessionId={}", sessionId);
            return new LinkedHashSet<>();
        }
    }

    /**
     * 激活一个 Skill slug
     */
    public void activate(Long sessionId, String slug) {
        if (sessionId == null || slug == null) {
            return;
        }
        Set<String> slugs = getActivated(sessionId);
        slugs.add(slug);
        try {
            String json = objectMapper.writeValueAsString(new ArrayList<>(slugs));
            redisUtil.set(PREFIX + sessionId, json, TTL_SECONDS);
        } catch (Exception e) {
            log.warn("[SkillActivation] 保存激活状态失败: sessionId={}, slug={}", sessionId, slug);
        }
    }
}
