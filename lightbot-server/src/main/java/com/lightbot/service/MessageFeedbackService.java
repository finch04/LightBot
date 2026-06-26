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
     * @param userId    用户ID
     * @param request   反馈请求
     * @return 反馈记录（null 表示取消）
     */
    MessageFeedback submitFeedback(Long messageId, Long userId, MessageFeedbackRequest request);

    /**
     * 获取当前用户对指定消息的反馈
     *
     * @param messageId 消息ID
     * @param userId    用户ID
     * @return 反馈记录（null 表示无反馈）
     */
    MessageFeedback getMyFeedback(Long messageId, Long userId);

    /**
     * 获取当前用户的所有反馈记录（分页）
     *
     * @param userId   用户ID
     * @param pageNum  页码
     * @param pageSize 每页大小
     * @param rating   评分过滤（like/dislike/null=全部）
     * @return 分页结果（含消息内容摘要）
     */
    Page<MessageFeedbackVO> listMyFeedbacks(Long userId, int pageNum, int pageSize, String rating);

    /**
     * 批量获取当前用户对多条消息的反馈
     *
     * @param userId      用户ID
     * @param messageIds  消息ID列表
     * @return messageId → MessageFeedback 映射
     */
    Map<Long, MessageFeedback> batchGetFeedbacks(Long userId, List<Long> messageIds);

    /**
     * 获取当前用户的反馈统计（总反馈数、like数、dislike数）
     *
     * @param userId 用户ID
     * @return 统计数据 { total, likeCount, dislikeCount }
     */
    Map<String, Object> getFeedbackStats(Long userId);
}
