package com.lightbot.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lightbot.dto.automation.AutomationJobSaveDTO;
import com.lightbot.entity.AutomationJob;
import com.lightbot.vo.AutomationJobRunVO;
import com.lightbot.vo.AutomationJobVO;

import java.util.List;

/**
 * 自动化定时任务服务
 *
 * @author finch
 * @since 2026-07-26
 */
public interface AutomationJobService extends IService<AutomationJob> {

    List<AutomationJobVO> listMine(String keyword, Boolean enabled, Long agentId);

    AutomationJobVO getMine(Long id);

    AutomationJobVO create(AutomationJobSaveDTO dto);

    AutomationJobVO update(Long id, AutomationJobSaveDTO dto);

    void delete(Long id);

    AutomationJobVO setEnabled(Long id, boolean enabled);

    /**
     * 立即执行：创建 running 记录并返回 runId（不推进 cron next_run_at）
     *
     * @param id 任务 ID
     * @return runId，由调用方在事务外执行 {@link #executeClaimedRun(Long)}
     */
    Long prepareManualRun(Long id);

    Page<AutomationJobRunVO> pageRuns(String keyword, String status, Long agentId, int pageNum, int pageSize);

    AutomationJobRunVO getRunMine(Long runId);

    /**
     * 扫描并抢占到期任务，返回待执行的 runId 列表
     *
     * @param limit 单次上限
     * @return runId 列表
     */
    List<Long> claimDueJobs(int limit);

    /** 执行已抢占的 run（异步线程调用） */
    void executeClaimedRun(Long runId);

    /** 回收超时 running */
    int reclaimExpiredRuns(int limit);

    // ========== Agent Tool 作用域（强制 userId + agentId，不依赖 StpUtil）==========

    /**
     * 列出指定用户下某智能体的定时任务
     */
    List<AutomationJobVO> listByAgent(Long userId, Long agentId, String keyword, Boolean enabled);

    /**
     * 获取任务详情（须属于该 user + agent）
     */
    AutomationJobVO getByAgent(Long userId, Long agentId, Long id);

    /**
     * 为指定智能体创建任务（强制写入 agentId，忽略 dto 中的其他 agent）
     */
    AutomationJobVO createForAgent(Long userId, Long agentId, AutomationJobSaveDTO dto);

    /**
     * 更新任务（须属于该 agent；强制保持 agentId 不变）
     */
    AutomationJobVO updateForAgent(Long userId, Long agentId, Long id, AutomationJobSaveDTO dto);

    /**
     * 删除任务（须属于该 agent）
     */
    void deleteForAgent(Long userId, Long agentId, Long id);

    /**
     * 启停任务（须属于该 agent）
     */
    AutomationJobVO setEnabledForAgent(Long userId, Long agentId, Long id, boolean enabled);

    /**
     * 立即执行（须属于该 agent），返回 runId
     */
    Long prepareManualRunForAgent(Long userId, Long agentId, Long id);

    /**
     * 分页查询某智能体的执行记录（列表不含 detail 大字段）
     *
     * @param jobId 可选：按任务过滤
     */
    Page<AutomationJobRunVO> pageRunsByAgent(Long userId, Long agentId, Long jobId,
                                             String keyword, String status,
                                             int pageNum, int pageSize);

    /**
     * 执行记录详情（须属于该 user + agent，含 detail）
     */
    AutomationJobRunVO getRunByAgent(Long userId, Long agentId, Long runId);
}
