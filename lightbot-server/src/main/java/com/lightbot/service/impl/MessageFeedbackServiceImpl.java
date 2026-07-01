package com.lightbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lightbot.dto.MessageFeedbackRequest;
import com.lightbot.dto.MessageFeedbackVO;
import com.lightbot.entity.Agent;
import com.lightbot.entity.AgentVersion;
import com.lightbot.entity.ChatSession;
import com.lightbot.entity.Message;
import com.lightbot.entity.MessageFeedback;
import com.lightbot.mapper.AgentVersionMapper;
import com.lightbot.mapper.MessageFeedbackMapper;
import com.lightbot.service.AgentService;
import com.lightbot.service.ChatSessionService;
import com.lightbot.service.MessageFeedbackService;
import com.lightbot.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 消息反馈服务实现类
 *
 * @author finch
 * @since 2026-06-26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageFeedbackServiceImpl extends ServiceImpl<MessageFeedbackMapper, MessageFeedback>
        implements MessageFeedbackService {

    private final MessageService messageService;
    private final ChatSessionService chatSessionService;
    private final AgentService agentService;
    private final AgentVersionMapper agentVersionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MessageFeedback submitFeedback(Long messageId, Long userId, MessageFeedbackRequest request) {
        // 1. 查询是否已有反馈
        MessageFeedback existing = getOne(new LambdaQueryWrapper<MessageFeedback>()
                .eq(MessageFeedback::getUserId, userId)
                .eq(MessageFeedback::getMessageId, messageId));

        // 2. toggle 逻辑：同类型删、不同类型切、无则建
        if (existing != null) {
            if (existing.getRating().equals(request.getRating())) {
                // 同类型 → 删除（取消反馈）
                removeById(existing.getId());
                log.info("[消息反馈] 取消反馈, userId={}, messageId={}, rating={}", userId, messageId, request.getRating());
                return null;
            } else {
                // 不同类型 → 切换
                existing.setRating(request.getRating());
                existing.setReason(request.getReason());
                updateById(existing);
                log.info("[消息反馈] 切换反馈, userId={}, messageId={}, rating={}", userId, messageId, request.getRating());
                return existing;
            }
        }

        // 3. 无则新建，快照所属 Agent 与版本
        MessageFeedback feedback = new MessageFeedback();
        feedback.setMessageId(messageId);
        feedback.setUserId(userId);
        feedback.setRating(request.getRating());
        feedback.setReason(request.getReason());
        applyAgentSnapshot(feedback, messageId);
        save(feedback);
        log.info("[消息反馈] 新增反馈, userId={}, messageId={}, rating={}, agentId={}",
                userId, messageId, request.getRating(), feedback.getAgentId());
        return feedback;
    }

    @Override
    public MessageFeedback getMyFeedback(Long messageId, Long userId) {
        return getOne(new LambdaQueryWrapper<MessageFeedback>()
                .eq(MessageFeedback::getUserId, userId)
                .eq(MessageFeedback::getMessageId, messageId));
    }

    @Override
    public Page<MessageFeedbackVO> listMyFeedbacks(Long userId, int pageNum, int pageSize, String rating) {
        // 1. 分页查询当前用户的反馈
        LambdaQueryWrapper<MessageFeedback> wrapper = new LambdaQueryWrapper<MessageFeedback>()
                .eq(MessageFeedback::getUserId, userId)
                .eq(rating != null && !rating.isEmpty(), MessageFeedback::getRating, rating)
                .orderByDesc(MessageFeedback::getCreateTime);
        Page<MessageFeedback> page = page(new Page<>(pageNum, pageSize), wrapper);

        // 2. 批量查询关联的消息与会话
        List<Long> messageIds = page.getRecords().stream()
                .map(MessageFeedback::getMessageId)
                .collect(Collectors.toList());

        Map<Long, Message> messageMap = new HashMap<>();
        Map<Long, ChatSession> sessionMap = new HashMap<>();
        if (!messageIds.isEmpty()) {
            List<Message> messages = messageService.listByIds(messageIds);
            messageMap = messages.stream()
                    .collect(Collectors.toMap(Message::getId, m -> m));
            Set<Long> sessionIds = messages.stream()
                    .map(Message::getSessionId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (!sessionIds.isEmpty()) {
                sessionMap = chatSessionService.listByIds(sessionIds).stream()
                        .collect(Collectors.toMap(ChatSession::getId, s -> s));
            }
        }

        // 3. 批量查询 Agent 名称
        Set<Long> agentIds = new HashSet<>();
        for (MessageFeedback fb : page.getRecords()) {
            if (fb.getAgentId() != null) {
                agentIds.add(fb.getAgentId());
            }
        }
        Map<Long, ChatSession> finalSessionMap = sessionMap;
        Map<Long, Message> finalMessageMap = messageMap;
        for (MessageFeedback fb : page.getRecords()) {
            if (fb.getAgentId() != null) {
                continue;
            }
            Message msg = finalMessageMap.get(fb.getMessageId());
            if (msg == null) {
                continue;
            }
            ChatSession session = finalSessionMap.get(msg.getSessionId());
            if (session != null && session.getAgentId() != null) {
                agentIds.add(session.getAgentId());
            }
        }
        Map<Long, Agent> agentMap = agentIds.isEmpty() ? Map.of()
                : agentService.listByIds(agentIds).stream()
                .collect(Collectors.toMap(Agent::getId, a -> a));

        // 4. 组装 VO
        Page<MessageFeedbackVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        List<MessageFeedbackVO> voList = page.getRecords().stream().map(fb -> {
            MessageFeedbackVO vo = new MessageFeedbackVO();
            vo.setId(fb.getId());
            vo.setMessageId(fb.getMessageId());
            vo.setRating(fb.getRating());
            vo.setReason(fb.getReason());
            vo.setCreateTime(fb.getCreateTime());

            Long agentId = fb.getAgentId();
            Integer agentVersion = fb.getAgentVersion();
            Message msg = finalMessageMap.get(fb.getMessageId());
            if (msg != null) {
                vo.setMessageContent(msg.getContent());
                vo.setSessionId(msg.getSessionId());
            }
            if (agentId == null && msg != null) {
                ChatSession session = finalSessionMap.get(msg.getSessionId());
                if (session != null) {
                    agentId = session.getAgentId();
                    if (agentVersion == null) {
                        agentVersion = resolveVersionNumber(session);
                    }
                }
            }
            vo.setAgentId(agentId);
            vo.setAgentVersion(agentVersion);
            if (agentId != null) {
                Agent agent = agentMap.get(agentId);
                if (agent != null) {
                    vo.setAgentName(agent.getName());
                }
            }
            return vo;
        }).collect(Collectors.toList());
        voPage.setRecords(voList);

        return voPage;
    }

    @Override
    public Map<Long, MessageFeedback> batchGetFeedbacks(Long userId, List<Long> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return Map.of();
        }

        List<MessageFeedback> feedbacks = list(new LambdaQueryWrapper<MessageFeedback>()
                .eq(MessageFeedback::getUserId, userId)
                .in(MessageFeedback::getMessageId, messageIds));

        return feedbacks.stream()
                .collect(Collectors.toMap(MessageFeedback::getMessageId, fb -> fb));
    }

    @Override
    public Map<String, Object> getFeedbackStats(Long userId) {
        long total = count(new LambdaQueryWrapper<MessageFeedback>()
                .eq(MessageFeedback::getUserId, userId));
        long likeCount = count(new LambdaQueryWrapper<MessageFeedback>()
                .eq(MessageFeedback::getUserId, userId)
                .eq(MessageFeedback::getRating, "like"));
        long dislikeCount = count(new LambdaQueryWrapper<MessageFeedback>()
                .eq(MessageFeedback::getUserId, userId)
                .eq(MessageFeedback::getRating, "dislike"));

        return Map.of("total", total, "likeCount", likeCount, "dislikeCount", dislikeCount);
    }

    /** 从消息所属会话快照 Agent 与版本 */
    private void applyAgentSnapshot(MessageFeedback feedback, Long messageId) {
        Message msg = messageService.getById(messageId);
        if (msg == null || msg.getSessionId() == null) {
            return;
        }
        ChatSession session = chatSessionService.getById(msg.getSessionId());
        if (session == null || session.getAgentId() == null) {
            return;
        }
        feedback.setAgentId(session.getAgentId());
        feedback.setAgentVersion(resolveVersionNumber(session));
    }

    private Integer resolveVersionNumber(ChatSession session) {
        if (session.getAgentVersionId() != null) {
            AgentVersion versionRow = agentVersionMapper.selectById(session.getAgentVersionId());
            if (versionRow != null && versionRow.getVersion() != null) {
                return versionRow.getVersion();
            }
        }
        Agent agent = agentService.getById(session.getAgentId());
        return agent != null ? agent.getVersion() : null;
    }
}
