package com.lightbot.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lightbot.entity.Message;

import java.util.List;

/**
 * 消息服务接口
 *
 * @author finch
 * @since 2026-05-19
 */
public interface MessageService extends IService<Message> {

    /**
     * 获取会话的消息历史
     *
     * @param sessionId 会话ID
     * @return 消息列表
     */
    List<Message> listBySessionId(Long sessionId);

    /**
     * 删除会话下的所有消息
     *
     * @param sessionId 会话ID
     */
    void deleteBySessionId(Long sessionId);
}
