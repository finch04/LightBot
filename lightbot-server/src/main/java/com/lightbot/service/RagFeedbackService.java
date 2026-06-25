package com.lightbot.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lightbot.dto.RagFeedbackRequest;
import com.lightbot.entity.RagFeedback;

/**
 * RAG 检索反馈服务接口
 *
 * @author finch
 * @since 2026-06-25
 */
public interface RagFeedbackService extends IService<RagFeedback> {

    /**
     * 提交或切换 RAG 反馈（同一用户对同一引用重复提交时切换反馈类型）
     *
     * @param request 反馈请求
     * @return 保存后的反馈记录
     */
    RagFeedback submitFeedback(RagFeedbackRequest request);
}
