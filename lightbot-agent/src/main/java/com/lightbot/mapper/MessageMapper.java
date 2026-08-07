package com.lightbot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lightbot.entity.Message;
import com.lightbot.vo.ConversationSearchResultVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    /**
     * 统计近N天每天的消息数量
     *
     * @param days 天数
     * @return 每条包含 date(日期字符串) 和 count(数量)
     */
    @Select("""
            SELECT TO_CHAR(create_time, 'YYYY-MM-DD') AS date, COUNT(*) AS count
            FROM message
            WHERE create_time >= CURRENT_DATE - #{days} * INTERVAL '1 day'
            GROUP BY TO_CHAR(create_time, 'YYYY-MM-DD')
            ORDER BY date
            """)
    List<Map<String, Object>> countMessagesPerDay(int days);

    /**
     * 统计指定日期区间内每天的消息数量（含起止日）
     */
    @Select("""
            SELECT TO_CHAR(create_time, 'YYYY-MM-DD') AS date, COUNT(*) AS count
            FROM message
            WHERE create_time >= #{startDate}::date
              AND create_time < (#{endDate}::date + INTERVAL '1 day')
            GROUP BY TO_CHAR(create_time, 'YYYY-MM-DD')
            ORDER BY date
            """)
    List<Map<String, Object>> countMessagesPerDayRange(@Param("startDate") String startDate,
                                                       @Param("endDate") String endDate);

    /**
     * 统计指定日期区间内的消息总数（含起止日）
     */
    @Select("""
            SELECT COUNT(*) FROM message
            WHERE create_time >= #{startDate}::date
              AND create_time < (#{endDate}::date + INTERVAL '1 day')
            """)
    long countByDateRange(@Param("startDate") String startDate, @Param("endDate") String endDate);

    /** 按 metadata.requestId 查询本轮助手消息，避免把会话历史投影到当前调研任务。 */
    @Select("""
            SELECT * FROM message
            WHERE session_id = #{sessionId}
              AND role = 'assistant'
              AND metadata ->> 'requestId' = #{requestId}
            ORDER BY create_time ASC, id ASC
            """)
    List<Message> selectAssistantByRequestId(@Param("sessionId") Long sessionId,
                                             @Param("requestId") String requestId);

    /** 按 metadata.requestId 查询本轮用户输入，保证流式运行中也能定位请求级附件。 */
    @Select("""
            SELECT * FROM message
            WHERE session_id = #{sessionId}
              AND role = 'user'
              AND metadata ->> 'requestId' = #{requestId}
            ORDER BY create_time DESC, id DESC
            LIMIT 1
            """)
    Message selectUserByRequestId(@Param("sessionId") Long sessionId,
                                  @Param("requestId") String requestId);

    /** 查询某轮助手消息前最近的用户输入，用于返回请求级附件。 */
    @Select("""
            SELECT * FROM message
            WHERE session_id = #{sessionId}
              AND role = 'user'
              AND id < #{beforeMessageId}
            ORDER BY id DESC
            LIMIT 1
            """)
    Message selectPreviousUserMessage(@Param("sessionId") Long sessionId,
                                      @Param("beforeMessageId") Long beforeMessageId);

    /**
     * 跨会话按关键词搜索消息：返回每条命中消息 + 所属会话基础信息（标题/agent/置顶/最后消息时间）。
     * <p>限当前用户的会话，关键字使用 ILIKE 不区分大小写模糊匹配。
     * 前导通配符 '%kw%' 无法走 B-tree，依赖 LIMIT 兜底限制扫描规模（v3.1 2.1.3 短期方案）。
     * 消息量 > 5w 后启用 pg_trgm + GIN 索引（gin_trgm_ops）让 ILIKE 走索引，详见 backend-optimization-v3.1.md</p>
     */
    @Select("""
            SELECT m.id           AS messageId,
                   m.role         AS messageRole,
                   m.content      AS snippet,
                   m.create_time  AS messageCreateTime,
                   s.id           AS sessionId,
                   s.title        AS sessionTitle,
                   s.agent_id     AS agentId,
                   s.pinned       AS pinned,
                   s.last_message_at AS sessionLastMessageAt
            FROM message m
            JOIN chat_session s ON m.session_id = s.id
            WHERE s.user_id = #{userId}
              AND s.deleted = 0
              AND (s.source = 'platform' OR s.source IS NULL)
              AND m.content ILIKE CONCAT('%', #{keyword}, '%')
            ORDER BY m.create_time DESC, m.id DESC
            LIMIT #{limit}
            """)
    List<ConversationSearchResultVO> searchConversationsByContent(@Param("userId") Long userId,
                                                                  @Param("keyword") String keyword,
                                                                  @Param("limit") int limit);
}
