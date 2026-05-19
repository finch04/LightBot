package com.lightbot.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lightbot.common.BizException;
import com.lightbot.entity.ChatSession;
import com.lightbot.enums.SessionStatus;
import com.lightbot.mapper.ChatSessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 对话会话服务
 *
 * @author finch
 * @since 2026-05-19
 */
@Service
@RequiredArgsConstructor
public class ChatSessionService extends ServiceImpl<ChatSessionMapper, ChatSession> {

    /**
     * 创建新会话
     */
    public ChatSession createSession(Long agentId) {
        long userId = StpUtil.getLoginIdAsLong();
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setAgentId(agentId);
        session.setTitle("新对话");
        session.setStatus(SessionStatus.ACTIVE);
        session.setMessageCount(0);
        session.setTotalTokens(0L);
        save(session);
        return session;
    }

    /**
     * 分页查询当前用户的会话列表
     */
    public Page<ChatSession> listMySessions(int pageNum, int pageSize) {
        long userId = StpUtil.getLoginIdAsLong();
        return page(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getUserId, userId)
                        .eq(ChatSession::getStatus, SessionStatus.ACTIVE)
                        .orderByDesc(ChatSession::getLastMessageAt));
    }

    /**
     * 更新会话标题
     */
    public void updateTitle(Long sessionId, String title) {
        ChatSession session = getById(sessionId);
        if (session == null) {
            throw new BizException("会话不存在");
        }
        session.setTitle(title);
        updateById(session);
    }

    /**
     * 归档会话
     */
    public void archiveSession(Long sessionId) {
        ChatSession session = getById(sessionId);
        if (session == null) {
            throw new BizException("会话不存在");
        }
        session.setStatus(SessionStatus.ARCHIVED);
        updateById(session);
    }

    /**
     * 更新会话统计（消息数、token数、最后消息时间）
     */
    public void updateStats(Long sessionId, int tokenCount) {
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
