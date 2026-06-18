package com.lightbot.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lightbot.common.BizException;
import com.lightbot.entity.ChatSession;
import com.lightbot.enums.ErrorCode;
import com.lightbot.enums.SessionStatus;
import com.lightbot.mapper.ChatSessionMapper;
import com.lightbot.service.AgentService;
import com.lightbot.service.ChatSessionService;
import com.lightbot.service.LlmTraceService;
import com.lightbot.service.MessageService;
import com.lightbot.util.RedisUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 对话会话服务实现类
 *
 * @author finch
 * @since 2026-05-19
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSession>
        implements ChatSessionService {

    private final AgentService agentService;
    private final MessageService messageService;
    private final LlmTraceService llmTraceService;
    private final RedisUtil redisUtil;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String CACHE_PREFIX = "lightbot:session:";
    private static final String LIST_CACHE_PREFIX = "lightbot:session:list:";
    private static final String LIST_VERSION_PREFIX = "lightbot:session:list:ver:";
    private static final long CACHE_TTL_SECONDS = 1800; // 30min
    private static final long LIST_CACHE_TTL_SECONDS = 60; // 60s

    private String cacheKey(Long sessionId) {
        return CACHE_PREFIX + sessionId;
    }

    private String listCacheKey(Long userId, int pageNum, int pageSize) {
        // 版本号嵌入 key，evict 时递增版本即可使旧 key 自然失效
        long ver = getListVersion(userId);
        return LIST_CACHE_PREFIX + userId + ":" + ver + ":" + pageNum + ":" + pageSize;
    }

    private String listVersionKey(Long userId) {
        return LIST_VERSION_PREFIX + userId;
    }

    /** 获取当前列表缓存版本号 */
    private long getListVersion(Long userId) {
        String ver = redisUtil.get(listVersionKey(userId));
        return ver != null ? Long.parseLong(ver) : 0L;
    }

    /** 写操作后递增版本号，使旧 key 自然过期 */
    private void evictListCache(Long userId) {
        redisUtil.increment(listVersionKey(userId));
    }

    /** 写操作后清除 session 详情缓存 */
    private void evictSessionCache(Long sessionId) {
        redisUtil.delete(cacheKey(sessionId));
    }

    @Override
    public ChatSession getById(java.io.Serializable id) {
        // 优先读缓存
        String json = redisUtil.get(cacheKey(Long.parseLong(id.toString())));
        if (json != null) {
            try {
                return OBJECT_MAPPER.readValue(json, ChatSession.class);
            } catch (Exception e) {
                log.warn("[Session] 反序列化缓存失败: id={}", id);
            }
        }
        ChatSession session = super.getById(id);
        if (session != null) {
            try {
                redisUtil.set(cacheKey(session.getId()), OBJECT_MAPPER.writeValueAsString(session), CACHE_TTL_SECONDS);
            } catch (Exception e) {
                log.warn("[Session] 写入缓存失败: id={}", id);
            }
        }
        return session;
    }

    @Override
    public ChatSession createSession(Long agentId) {
        // 1. 获取当前用户ID
        long userId = StpUtil.getLoginIdAsLong();

        // 2. agentId为空时查询用户的默认Agent
        Long finalAgentId = agentId;
        if (finalAgentId == null) {
            var defaultAgent = agentService.getDefaultAgent(userId);
            if (defaultAgent != null) {
                finalAgentId = defaultAgent.getId();
            }
        }

        // 3. 创建会话，初始化统计数据
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setAgentId(finalAgentId);
        session.setTitle("新对话");
        session.setStatus(SessionStatus.ACTIVE);
        session.setMessageCount(0);
        session.setTotalTokens(0L);
        session.setPinned(false);
        save(session);
        // 新建会话后清除列表缓存
        evictListCache(userId);
        return session;
    }

    @Override
    public Page<ChatSession> listMySessions(int pageNum, int pageSize) {
        long userId = StpUtil.getLoginIdAsLong();
        // 优先读列表缓存
        String listKey = listCacheKey(userId, pageNum, pageSize);
        String cached = redisUtil.get(listKey);
        if (cached != null) {
            try {
                return OBJECT_MAPPER.readValue(cached, new com.fasterxml.jackson.core.type.TypeReference<>() {});
            } catch (Exception e) {
                log.warn("[Session] 列表缓存反序列化失败: userId={}", userId);
            }
        }
        Page<ChatSession> page = baseMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getUserId, userId)
                        .eq(ChatSession::getStatus, SessionStatus.ACTIVE)
                        .orderByDesc(ChatSession::getPinned)
                        .orderByDesc(ChatSession::getLastMessageAt));
        try {
            redisUtil.set(listKey, OBJECT_MAPPER.writeValueAsString(page), LIST_CACHE_TTL_SECONDS);
        } catch (Exception e) {
            log.warn("[Session] 列表缓存写入失败: userId={}", userId);
        }
        return page;
    }

    @Override
    public void updateTitle(Long sessionId, String title) {
        ChatSession session = getById(sessionId);
        if (session == null) {
            throw new BizException(ErrorCode.SESSION_NOT_FOUND);
        }
        session.setTitle(title);
        updateById(session);
        evictSessionCache(sessionId);
        evictListCache(session.getUserId());
    }

    @Override
    public void archiveSession(Long sessionId) {
        ChatSession session = getById(sessionId);
        if (session == null) {
            throw new BizException(ErrorCode.SESSION_NOT_FOUND);
        }
        session.setStatus(SessionStatus.ARCHIVED);
        updateById(session);
        evictSessionCache(sessionId);
        evictListCache(session.getUserId());
    }

    @Override
    public void updateStats(Long sessionId, int tokenCount) {
        // 累加消息数、token数，更新最后消息时间
        ChatSession session = getById(sessionId);
        if (session == null) {
            return;
        }
        session.setMessageCount(session.getMessageCount() + 1);
        session.setTotalTokens(session.getTotalTokens() + tokenCount);
        session.setLastMessageAt(LocalDateTime.now());
        updateById(session);
        // updateStats 高频调用，只失效列表缓存（列表排序依赖 lastMessageAt），不失效详情缓存
        evictListCache(session.getUserId());
    }

    @Override
    public void deleteSession(Long sessionId) {
        ChatSession session = getById(sessionId);
        if (session == null) {
            throw new BizException(ErrorCode.SESSION_NOT_FOUND);
        }
        // 1. 物理删除会话下的所有消息
        messageService.deleteBySessionId(sessionId);
        // 2. 物理删除会话下的所有调用链记录
        llmTraceService.deleteBySessionId(sessionId);
        // 3. 物理删除会话
        removeById(sessionId);
        evictSessionCache(sessionId);
        evictListCache(session.getUserId());
    }

    @Override
    public void togglePin(Long sessionId) {
        ChatSession session = getById(sessionId);
        if (session == null) {
            throw new BizException(ErrorCode.SESSION_NOT_FOUND);
        }
        session.setPinned(Boolean.TRUE.equals(session.getPinned()) ? false : true);
        updateById(session);
        evictSessionCache(sessionId);
        evictListCache(session.getUserId());
    }

    @Override
    public void updateAgentId(Long sessionId, Long agentId) {
        ChatSession session = getById(sessionId);
        if (session == null) {
            throw new BizException(ErrorCode.SESSION_NOT_FOUND);
        }
        long userId = StpUtil.getLoginIdAsLong();
        if (userId != session.getUserId()) {
            throw new BizException(ErrorCode.SESSION_NOT_FOUND);
        }
        if (agentId == null || agentId.equals(session.getAgentId())) {
            return;
        }
        session.setAgentId(agentId);
        updateById(session);
        evictSessionCache(sessionId);
    }
}
