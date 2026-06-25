package com.lightbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lightbot.dto.RagFeedbackRequest;
import com.lightbot.entity.RagFeedback;
import com.lightbot.mapper.RagFeedbackMapper;
import com.lightbot.service.RagFeedbackService;
import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * RAG 检索反馈服务实现
 *
 * @author finch
 * @since 2026-06-25
 */
@Slf4j
@Service
public class RagFeedbackServiceImpl extends ServiceImpl<RagFeedbackMapper, RagFeedback>
        implements RagFeedbackService {

    @Override
    public RagFeedback submitFeedback(RagFeedbackRequest request) {
        long userId = StpUtil.getLoginIdAsLong();

        // 1. 查询是否已有反馈（同一用户 + 同一消息 + 同一引用）
        LambdaQueryWrapper<RagFeedback> wrapper = new LambdaQueryWrapper<RagFeedback>()
                .eq(RagFeedback::getMessageId, request.getMessageId())
                .eq(RagFeedback::getUserId, userId)
                .eq(RagFeedback::getSourceType, request.getSourceType());

        if ("chunk".equals(request.getSourceType())) {
            wrapper.eq(RagFeedback::getChunkId, request.getChunkId());
        } else {
            wrapper.eq(RagFeedback::getQaPairId, request.getQaPairId());
        }

        RagFeedback existing = getOne(wrapper);

        if (existing != null) {
            // 2. 已有反馈：相同类型则取消（删除），不同类型则切换
            if (existing.getFeedbackType().equals(request.getFeedbackType())) {
                removeById(existing.getId());
                log.info("[RAG反馈] 取消反馈: messageId={}, userId={}", request.getMessageId(), userId);
                return null;
            } else {
                existing.setFeedbackType(request.getFeedbackType());
                updateById(existing);
                log.info("[RAG反馈] 切换反馈: messageId={}, type={}", request.getMessageId(), request.getFeedbackType());
                return existing;
            }
        }

        // 3. 新增反馈
        RagFeedback feedback = new RagFeedback();
        feedback.setMessageId(request.getMessageId());
        feedback.setUserId(userId);
        feedback.setChunkId(request.getChunkId());
        feedback.setQaPairId(request.getQaPairId());
        feedback.setSourceType(request.getSourceType());
        feedback.setFeedbackType(request.getFeedbackType());
        feedback.setCreateTime(LocalDateTime.now());
        save(feedback);

        log.info("[RAG反馈] 新增反馈: messageId={}, type={}", request.getMessageId(), request.getFeedbackType());
        return feedback;
    }
}
