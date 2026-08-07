package com.lightbot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lightbot.entity.AutomationJobRun;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 自动化任务执行记录 Mapper
 *
 * @author finch
 * @since 2026-07-26
 */
@Mapper
public interface AutomationJobRunMapper extends BaseMapper<AutomationJobRun> {

    /**
     * 查询已过租约的 running 记录
     *
     * @param now   当前时间
     * @param limit 单次上限
     * @return 记录 ID
     */
    @Select("""
            SELECT id FROM automation_job_run
            WHERE deleted = 0
              AND status = 'running'
              AND lease_expire_at IS NOT NULL
              AND lease_expire_at < #{now}
            ORDER BY lease_expire_at ASC
            LIMIT #{limit}
            FOR UPDATE SKIP LOCKED
            """)
    List<Long> selectExpiredRunningIds(@Param("now") LocalDateTime now, @Param("limit") int limit);
}
