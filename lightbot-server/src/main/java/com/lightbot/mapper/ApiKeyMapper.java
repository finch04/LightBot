package com.lightbot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lightbot.entity.ApiKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;

/**
 * API Key Mapper
 *
 * @author finch
 * @since 2026-06-25
 */
@Mapper
public interface ApiKeyMapper extends BaseMapper<ApiKey> {

    @Update("UPDATE api_key SET last_used_at = NOW() WHERE id = #{id}")
    void updateLastUsedAt(@Param("id") Long id);

    /**
     * 原子配额检查与扣减（消除 TOCTOU 竞态）
     * <p>CASE WHEN 处理跨天重置 + WHERE 约束防止超配额扣减</p>
     *
     * @param id         API Key ID
     * @param tokenUsage 本次用量
     * @param dailyQuota 日配额上限
     * @param today      当前日期（用于跨天重置判断）
     * @return 受影响行数（0 = 配额不足，1 = 扣减成功）
     */
    @Update("""
            UPDATE api_key SET
                used_tokens = CASE
                    WHEN quota_reset_at != #{today} THEN #{tokenUsage}
                    ELSE used_tokens + #{tokenUsage}
                END,
                quota_reset_at = #{today}
            WHERE id = #{id}
              AND (
                  (quota_reset_at != #{today} AND #{tokenUsage} <= #{dailyQuota})
                  OR
                  (quota_reset_at = #{today} AND used_tokens + #{tokenUsage} <= #{dailyQuota})
              )
            """)
    int checkAndConsumeQuota(@Param("id") Long id,
                             @Param("tokenUsage") long tokenUsage,
                             @Param("dailyQuota") int dailyQuota,
                             @Param("today") LocalDate today);
}
