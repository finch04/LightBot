package com.lightbot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lightbot.entity.UserMemory;
import com.lightbot.vo.ExternalMemoryUserSummaryVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 用户长期记忆 Mapper
 *
 * @author finch
 * @since 2026-07-09
 */
@Mapper
public interface UserMemoryMapper extends BaseMapper<UserMemory> {

    /**
     * 更新记忆向量
     *
     * @param id     记忆ID
     * @param vector 向量字符串
     */
    @Update("UPDATE user_memory SET embedding_vector = #{vector}::vector, update_time = NOW() WHERE id = #{id}")
    void updateEmbeddingVector(@Param("id") Long id, @Param("vector") String vector);

    /**
     * 语义检索用户长期记忆
     *
     * @param userId  用户ID
     * @param agentId AgentID
     * @param vector  查询向量
     * @param limit   返回数量
     * @return 记忆列表
     */
    @Select("""
            SELECT id, user_id, api_key_id, external_user_id, agent_id, session_id, memory_type, content, keywords,
                   source_message_id, confidence, status, last_used_at, create_time, update_time, deleted
            FROM user_memory
            WHERE user_id = #{userId}
              AND api_key_id IS NULL
              AND deleted = 0
              AND status = 'active'
              AND embedding_vector IS NOT NULL
              AND (agent_id IS NULL OR agent_id = #{agentId})
            ORDER BY embedding_vector <=> #{vector}::vector
            LIMIT #{limit}
            """)
    List<UserMemory> searchSemantic(@Param("userId") Long userId,
                                    @Param("agentId") Long agentId,
                                    @Param("vector") String vector,
                                    @Param("limit") int limit);

    /**
     * 控制台调试：按 user_id + debug_user_{userId} 语义检索
     */
    @Select("""
            SELECT id, user_id, api_key_id, external_user_id, agent_id, session_id, memory_type, content, keywords,
                   source_message_id, confidence, status, last_used_at, create_time, update_time, deleted
            FROM user_memory
            WHERE user_id = #{userId}
              AND api_key_id IS NULL
              AND external_user_id = #{externalUserId}
              AND deleted = 0
              AND status = 'active'
              AND embedding_vector IS NOT NULL
              AND (agent_id IS NULL OR agent_id = #{agentId})
            ORDER BY embedding_vector <=> #{vector}::vector
            LIMIT #{limit}
            """)
    List<UserMemory> searchSemanticConsole(@Param("userId") Long userId,
                                           @Param("externalUserId") String externalUserId,
                                           @Param("agentId") Long agentId,
                                           @Param("vector") String vector,
                                           @Param("limit") int limit);

    /**
     * 开放 API：按 api_key_id + external_user_id 语义检索
     */
    @Select("""
            SELECT id, user_id, api_key_id, external_user_id, agent_id, session_id, memory_type, content, keywords,
                   source_message_id, confidence, status, last_used_at, create_time, update_time, deleted
            FROM user_memory
            WHERE api_key_id = #{apiKeyId}
              AND external_user_id = #{externalUserId}
              AND deleted = 0
              AND status = 'active'
              AND embedding_vector IS NOT NULL
              AND (agent_id IS NULL OR agent_id = #{agentId})
            ORDER BY embedding_vector <=> #{vector}::vector
            LIMIT #{limit}
            """)
    List<UserMemory> searchSemanticExternal(@Param("apiKeyId") Long apiKeyId,
                                            @Param("externalUserId") String externalUserId,
                                            @Param("agentId") Long agentId,
                                            @Param("vector") String vector,
                                            @Param("limit") int limit);

    /**
     * 按外部用户汇总某企业 API Key 下的记忆数量
     */
    @Select("""
            SELECT external_user_id AS externalUserId,
                   COUNT(*) FILTER (WHERE status = 'active') AS activeCount,
                   COUNT(*) AS totalCount,
                   MAX(update_time) AS lastUpdateTime
            FROM user_memory
            WHERE api_key_id = #{apiKeyId}
              AND deleted = 0
              AND external_user_id IS NOT NULL
              AND external_user_id <> ''
            GROUP BY external_user_id
            ORDER BY MAX(update_time) DESC
            """)
    List<ExternalMemoryUserSummaryVO> summarizeExternalUsers(@Param("apiKeyId") Long apiKeyId);
}
