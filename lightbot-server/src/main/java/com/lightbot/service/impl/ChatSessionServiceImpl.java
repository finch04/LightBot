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
import com.lightbot.service.ChatSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 对话会话服务实现类
 *
 * @author finch
 * @since 2026-05-19
 */
@Service
@RequiredArgsConstructor
public class ChatSessionServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSession>
        implements ChatSessionService {

    private static final long DEFAULT_AGENT_ID = 1L;

    @Override
    public ChatSession createSession(Long agentId) {
        // 1. 获取当前用户ID
        long userId = StpUtil.getLoginIdAsLong();

        // 2. agentId为空时使用默认Agent
        Long finalAgentId = agentId != null ? agentId : DEFAULT_AGENT_ID;

        // 3. 创建会话，初始化统计数据
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setAgentId(finalAgentId);
        session.setTitle("新对话");
        session.setStatus(SessionStatus.ACTIVE);
        session.setMessageCount(0);
        session.setTotalTokens(0L);
        save(session);
        return session;
    }

    @Override
    public Page<ChatSession> listMySessions(int pageNum, int pageSize) {
        long userId = StpUtil.getLoginIdAsLong();
        return page(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getUserId, userId)
                        .eq(ChatSession::getStatus, SessionStatus.ACTIVE)
                        .orderByDesc(ChatSession::getLastMessageAt));
    }

    @Override
    public void updateTitle(Long sessionId, String title) {
        ChatSession session = getById(sessionId);
        if (session == null) {
            throw new BizException(ErrorCode.SESSION_NOT_FOUND);
        }
        session.setTitle(title);
        updateById(session);
    }

    @Override
    public void archiveSession(Long sessionId) {
        ChatSession session = getById(sessionId);
        if (session == null) {
            throw new BizException(ErrorCode.SESSION_NOT_FOUND);
        }
        session.setStatus(SessionStatus.ARCHIVED);
        updateById(session);
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
    }
}
