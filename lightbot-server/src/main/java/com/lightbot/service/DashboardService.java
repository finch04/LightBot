package com.lightbot.service;

import java.util.Map;

/**
 * Dashboard统计服务接口
 *
 * @author finch
 * @since 2026-05-20
 */
public interface DashboardService {

    /**
     * 获取基础统计概览
     *
     * @return 包含各资源总数的Map
     */
    Map<String, Object> getBasicStats();

    /**
     * 获取Agent统计详情
     *
     * @return 包含状态分布和最近Agent的Map
     */
    Map<String, Object> getAgentStats();

    /**
     * 获取知识库统计详情
     *
     * @return 包含文档状态分布和分块数的Map
     */
    Map<String, Object> getKnowledgeStats();

    /**
     * 获取对话统计详情
     *
     * @return 包含会话数、消息数和近7天趋势的Map
     */
    /**
     * 获取对话统计
     *
     * @param days      近 N 天（与自定义区间二选一，days 优先）
     * @param startDate 自定义开始日期 yyyy-MM-dd
     * @param endDate   自定义结束日期 yyyy-MM-dd
     */
    Map<String, Object> getChatStats(Integer days, String startDate, String endDate);
}
