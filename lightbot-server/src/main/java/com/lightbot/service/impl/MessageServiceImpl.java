package com.lightbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lightbot.entity.Message;
import com.lightbot.mapper.MessageMapper;
import com.lightbot.service.MessageService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 消息服务实现类
 *
 * @author finch
 * @since 2026-05-19
 */
@Service
public class MessageServiceImpl extends ServiceImpl<MessageMapper, Message>
        implements MessageService {

    @Override
    public Page<Message> listBySessionIdPage(Long sessionId, int pageNum, int pageSize) {
        return baseMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getSessionId, sessionId)
                        .orderByDesc(Message::getCreateTime));
    }

    @Override
    public List<Message> listBySessionId(Long sessionId) {
        return list(new LambdaQueryWrapper<Message>()
                .eq(Message::getSessionId, sessionId)
                .orderByAsc(Message::getCreateTime));
    }

    @Override
    public void deleteBySessionId(Long sessionId) {
        remove(new LambdaQueryWrapper<Message>().eq(Message::getSessionId, sessionId));
    }

    @Override
    public void deleteMessage(Long messageId, Long sessionId) {
        remove(new LambdaQueryWrapper<Message>()
                .eq(Message::getId, messageId)
                .eq(Message::getSessionId, sessionId));
    }
}
