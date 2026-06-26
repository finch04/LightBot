package com.lightbot.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lightbot.dto.MessageFeedbackRequest;
import com.lightbot.dto.MessageFeedbackVO;
import com.lightbot.entity.MessageFeedback;

import java.util.List;
import java.util.Map;

/**
 * 消息反馈服务接口
 *
 * @author finch
 * @since 2026-06-26
 */
public interface MessageFeedbackService extends IService<MessageFeedback> {

    /**
     * 提交消息反馈（toggle 逻辑：同类型删、不同类型切、无则建）
     *
     * @param messageId 消息ID
     * @param request   反馈请求
     * @return 反馈记录（null 表示取消）
     */
    MessageFeedback submitFeedback(Long messageId, MessageFeedbackRequest request);

    /**
     * 获取当前用户对指定消息的反馈
     *
     * @param messageId 消息ID
     * @return 反馈记录（null 表示无反馈）
     */
    MessageFeedback getMyFeedback(Long messageId);

    /**
     * 获取当前用户的所有反馈记录（分页）
     *
     * @param pageNum  页码
     * @param pageSize 每页大小
     * @return 分页结果（含消息内容摘要）
     */
    Page<MessageFeedbackVO> listMyFeedbacks(int pageNum, int pageSize);

    /**
     * 批量获取当前用户对多条消息的反馈
     *
     * @param messageIds 消息ID列表
     * @return messageId → MessageFeedback 映射
     */
    Map<Long, MessageFeedback> batchGetFeedbacks(List<Long> messageIds);

    /**
     * 获取当前用户的反馈统计（总反馈数、like数、dislike数）
     *
     * @return 统计数据 { total, likeCount, dislikeCount }
     */
    Map<String, Object> getFeedbackStats();
}
