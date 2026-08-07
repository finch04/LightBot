package com.lightbot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lightbot.entity.AutomationJob;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 自动化定时任务 Mapper
 *
 * @author finch
 * @since 2026-07-26
 */
@Mapper
public interface AutomationJobMapper extends BaseMapper<AutomationJob> {

    /**
     * 抢占到期任务（多实例互斥）
     *
     * @param now   当前时间
     * @param limit 单次上限
     * @return 任务 ID 列表
     */
    @Select("""
            SELECT id FROM automation_job
            WHERE deleted = 0
              AND enabled = 1
              AND next_run_at IS NOT NULL
              AND next_run_at <= #{now}
            ORDER BY next_run_at ASC
            LIMIT #{limit}
            FOR UPDATE SKIP LOCKED
            """)
    List<Long> selectDueJobIdsForUpdate(@Param("now") LocalDateTime now, @Param("limit") int limit);
}
