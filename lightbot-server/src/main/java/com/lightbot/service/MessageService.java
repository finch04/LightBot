package com.lightbot.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
     * 分页获取会话消息（按创建时间倒序，返回最新N条）
     *
     * @param sessionId 会话ID
     * @param pageNum   页码
     * @param pageSize  每页数量
     * @return 分页消息列表
     */
    Page<Message> listBySessionIdPage(Long sessionId, int pageNum, int pageSize);

    /**
     * 获取会话的全部消息历史
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
