package com.lightbot.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lightbot.common.BizException;
import com.lightbot.dto.automation.AutomationJobSaveDTO;
import com.lightbot.entity.AutomationJob;
import com.lightbot.entity.AutomationJobRun;
import com.lightbot.enums.AutomationRunStatus;
import com.lightbot.enums.AutomationScheduleType;
import com.lightbot.enums.ErrorCode;
import com.lightbot.mapper.AutomationJobMapper;
import com.lightbot.mapper.AutomationJobRunMapper;
import com.lightbot.service.AutomationJobService;
import com.lightbot.service.port.AutomationAgentPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.util.AutomationScheduleUtil;
import com.lightbot.vo.AutomationJobRunVO;
import com.lightbot.vo.AutomationJobVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 自动化定时任务：CRUD、抢占调度、执行与僵尸回收
 *
 * @author finch
 * @since 2026-07-26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutomationJobServiceImpl extends ServiceImpl<AutomationJobMapper, AutomationJob>
        implements AutomationJobService {

    private static final int LEASE_MINUTES = 30;
    private static final int SUMMARY_MAX = 500;

    private final AutomationJobRunMapper runMapper;
    private final ObjectProvider<AutomationAgentPort> agentPortProvider;
    private final ObjectMapper objectMapper;

    @Override
    public List<AutomationJobVO> listMine(String keyword, Boolean enabled, Long agentId) {
        // 企业资产：建设者可见全部自动化任务
        LambdaQueryWrapper<AutomationJob> qw = new LambdaQueryWrapper<AutomationJob>()
                .eq(enabled != null, AutomationJob::getEnabled, Boolean.TRUE.equals(enabled) ? 1 : 0)
                .eq(agentId != null, AutomationJob::getAgentId, agentId)
                .orderByDesc(AutomationJob::getUpdateTime);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            qw.and(w -> w.like(AutomationJob::getName, kw)
                    .or().like(AutomationJob::getAgentName, kw)
                    .or().like(AutomationJob::getInstruction, kw));
        }
        return list(qw).stream().map(this::toJobVo).toList();
    }

    @Override
    public AutomationJobVO getMine(Long id) {
        return toJobVo(requireOwned(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AutomationJobVO create(AutomationJobSaveDTO dto) {
        return doCreate(StpUtil.getLoginIdAsLong(), dto);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AutomationJobVO update(Long id, AutomationJobSaveDTO dto) {
        AutomationJob job = requireOwned(id);
        return doUpdate(job.getUserId(), id, dto);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireOwned(id);
        removeById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AutomationJobVO setEnabled(Long id, boolean enabled) {
        return doSetEnabled(requireOwned(id), enabled);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long prepareManualRun(Long id) {
        return doPrepareManualRun(requireOwned(id));
    }

    private AutomationJobVO doCreate(Long userId, AutomationJobSaveDTO dto) {
        if (dto.getAgentId() == null) {
            throw new BizException(ErrorCode.AUTOMATION_AGENT_INVALID);
        }
        AutomationScheduleType type = AutomationScheduleUtil.parseType(dto.getScheduleType());
        String configJson = AutomationScheduleUtil.buildConfigJson(type, dto);
        String agentName = agentPort().requireAgentName(dto.getAgentId(), userId);

        boolean enabled = dto.getEnabled() == null || Boolean.TRUE.equals(dto.getEnabled());
        LocalDateTime next = enabled
                ? AutomationScheduleUtil.computeNextRun(type, configJson, LocalDateTime.now(AutomationScheduleUtil.ZONE))
                : null;
        if (enabled && type == AutomationScheduleType.ONCE && next == null) {
            LocalDateTime once = AutomationScheduleUtil.parseOnceAt(dto.getOnceAt());
            throw new BizException(ErrorCode.AUTOMATION_SCHEDULE_INVALID,
                    AutomationScheduleUtil.pastOnceMessage(once, LocalDateTime.now(AutomationScheduleUtil.ZONE)));
        }

        AutomationJob job = new AutomationJob();
        job.setUserId(userId);
        job.setName(dto.getName().trim());
        job.setAgentId(dto.getAgentId());
        job.setAgentName(agentName);
        job.setInstruction(dto.getInstruction().trim());
        job.setScheduleType(type);
        job.setScheduleConfig(configJson);
        job.setEnabled(enabled ? 1 : 0);
        job.setNextRunAt(next);
        save(job);
        return toJobVo(job);
    }

    private AutomationJobVO doUpdate(Long userId, Long id, AutomationJobSaveDTO dto) {
        AutomationJob job = getById(id);
        if (job == null) {
            throw new BizException(ErrorCode.AUTOMATION_JOB_NOT_FOUND);
        }
        if (dto.getAgentId() == null) {
            throw new BizException(ErrorCode.AUTOMATION_AGENT_INVALID);
        }
        AutomationScheduleType type = AutomationScheduleUtil.parseType(dto.getScheduleType());
        String configJson = AutomationScheduleUtil.buildConfigJson(type, dto);
        String agentName = agentPort().requireAgentName(dto.getAgentId(), userId);

        boolean enabled = dto.getEnabled() == null || Boolean.TRUE.equals(dto.getEnabled());
        LocalDateTime next = enabled
                ? AutomationScheduleUtil.computeNextRun(type, configJson, LocalDateTime.now(AutomationScheduleUtil.ZONE))
                : null;

        job.setName(dto.getName().trim());
        job.setAgentId(dto.getAgentId());
        job.setAgentName(agentName);
        job.setInstruction(dto.getInstruction().trim());
        job.setScheduleType(type);
        job.setScheduleConfig(configJson);
        job.setEnabled(enabled ? 1 : 0);
        job.setNextRunAt(next);
        updateById(job);
        return toJobVo(job);
    }

    private AutomationJobVO doSetEnabled(AutomationJob job, boolean enabled) {
        job.setEnabled(enabled ? 1 : 0);
        if (!enabled) {
            job.setNextRunAt(null);
        } else {
            LocalDateTime next = AutomationScheduleUtil.computeNextRun(
                    job.getScheduleType(), job.getScheduleConfig(),
                    LocalDateTime.now(AutomationScheduleUtil.ZONE));
            if (job.getScheduleType() == AutomationScheduleType.ONCE && next == null) {
                throw new BizException(ErrorCode.AUTOMATION_SCHEDULE_INVALID, "一次性任务已过期，请修改执行时刻后再启用");
            }
            job.setNextRunAt(next);
        }
        updateById(job);
        return toJobVo(job);
    }

    private Long doPrepareManualRun(AutomationJob job) {
        if (hasActiveLease(job.getId())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "任务正在执行中，请稍后再试");
        }
        AutomationJobRun run = newRunningRun(job, "manual");
        runMapper.insert(run);
        return run.getId();
    }

    @Override
    public Page<AutomationJobRunVO> pageRuns(String keyword, String status, Long agentId, int pageNum, int pageSize) {
        // 企业资产：建设者可见全部运行记录
        Page<AutomationJobRun> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<AutomationJobRun> qw = new LambdaQueryWrapper<AutomationJobRun>()
                .eq(agentId != null, AutomationJobRun::getAgentId, agentId)
                .orderByDesc(AutomationJobRun::getTriggerTime);
        if (StringUtils.hasText(status)) {
            qw.eq(AutomationJobRun::getStatus, AutomationRunStatus.fromValue(status.trim()));
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            qw.and(w -> w.like(AutomationJobRun::getJobName, kw)
                    .or().like(AutomationJobRun::getAgentName, kw)
                    .or().like(AutomationJobRun::getInstruction, kw));
        }
        Page<AutomationJobRun> raw = runMapper.selectPage(page, qw);
        Page<AutomationJobRunVO> voPage = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        // 列表不带大字段 detail，详情接口再加载
        voPage.setRecords(raw.getRecords().stream().map(r -> toRunVo(r, false)).toList());
        return voPage;
    }

    @Override
    public AutomationJobRunVO getRunMine(Long runId) {
        AutomationJobRun run = runMapper.selectById(runId);
        if (run == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return toRunVo(run, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> claimDueJobs(int limit) {
        LocalDateTime now = LocalDateTime.now(AutomationScheduleUtil.ZONE);
        List<Long> jobIds = baseMapper.selectDueJobIdsForUpdate(now, Math.max(1, limit));
        List<Long> runIds = new ArrayList<>();
        for (Long jobId : jobIds) {
            AutomationJob job = getById(jobId);
            if (job == null || job.getEnabled() == null || job.getEnabled() != 1) {
                continue;
            }
            if (hasActiveLease(jobId)) {
                // 仍在执行：跳过本轮，不推进 next（避免漏后续周期）。将 next 稍推后防热循环
                job.setNextRunAt(now.plusMinutes(1));
                updateById(job);
                continue;
            }
            // 1. 先推进 next_run_at（防双跑 / 停机追赶只跑一次）
            LocalDateTime next = AutomationScheduleUtil.computeNextRun(
                    job.getScheduleType(), job.getScheduleConfig(), now);
            if (job.getScheduleType() == AutomationScheduleType.ONCE) {
                job.setEnabled(0);
                job.setNextRunAt(null);
            } else {
                job.setNextRunAt(next);
            }
            job.setLastRunAt(now);
            updateById(job);

            // 2. 写入 running 记录
            AutomationJobRun run = newRunningRun(job, "schedule");
            run.setTriggerTime(now);
            runMapper.insert(run);
            runIds.add(run.getId());
        }
        return runIds;
    }

    @Override
    public void executeClaimedRun(Long runId) {
        AutomationJobRun run = runMapper.selectById(runId);
        if (run == null || run.getStatus() != AutomationRunStatus.RUNNING) {
            return;
        }
        long t0 = System.currentTimeMillis();
        try {
            AutomationAgentPort.AutomationAgentRunResult result = agentPort().run(
                    run.getUserId(), run.getAgentId(), run.getInstruction(), run.getJobName());
            run.setStatus(AutomationRunStatus.SUCCESS);
            run.setSessionId(result.sessionId());
            run.setSummary(truncate(result.summary(), SUMMARY_MAX));
            run.setDetailJson(result.detailJson());
            run.setError(null);
        } catch (Exception e) {
            log.warn("[Automation] 执行失败 runId={}, err={}", runId, e.getMessage());
            run.setStatus(AutomationRunStatus.FAILED);
            run.setError(truncate(e.getMessage(), SUMMARY_MAX));
            run.setSummary(null);
        }
        run.setDurationMs(System.currentTimeMillis() - t0);
        run.setLeaseExpireAt(null);
        runMapper.updateById(run);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int reclaimExpiredRuns(int limit) {
        LocalDateTime now = LocalDateTime.now(AutomationScheduleUtil.ZONE);
        List<Long> ids = runMapper.selectExpiredRunningIds(now, Math.max(1, limit));
        int n = 0;
        for (Long id : ids) {
            AutomationJobRun run = runMapper.selectById(id);
            if (run == null || run.getStatus() != AutomationRunStatus.RUNNING) {
                continue;
            }
            run.setStatus(AutomationRunStatus.FAILED);
            run.setError("执行超时（租约过期）");
            run.setLeaseExpireAt(null);
            if (run.getDurationMs() == null && run.getTriggerTime() != null) {
                run.setDurationMs(ChronoUnit.MILLIS.between(run.getTriggerTime(), now));
            }
            runMapper.updateById(run);
            n++;
        }
        return n;
    }

    @Override
    public List<AutomationJobVO> listByAgent(Long userId, Long agentId, String keyword, Boolean enabled) {
        requireUserAgent(userId, agentId);
        LambdaQueryWrapper<AutomationJob> qw = new LambdaQueryWrapper<AutomationJob>()
                .eq(AutomationJob::getAgentId, agentId)
                .eq(enabled != null, AutomationJob::getEnabled, Boolean.TRUE.equals(enabled) ? 1 : 0)
                .orderByDesc(AutomationJob::getUpdateTime);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            qw.and(w -> w.like(AutomationJob::getName, kw)
                    .or().like(AutomationJob::getInstruction, kw));
        }
        return list(qw).stream().map(this::toJobVo).toList();
    }

    @Override
    public AutomationJobVO getByAgent(Long userId, Long agentId, Long id) {
        return toJobVo(requireOwnedByAgent(userId, agentId, id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AutomationJobVO createForAgent(Long userId, Long agentId, AutomationJobSaveDTO dto) {
        requireUserAgent(userId, agentId);
        dto.setAgentId(agentId);
        return doCreate(userId, dto);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AutomationJobVO updateForAgent(Long userId, Long agentId, Long id, AutomationJobSaveDTO dto) {
        requireOwnedByAgent(userId, agentId, id);
        dto.setAgentId(agentId);
        return doUpdate(userId, id, dto);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteForAgent(Long userId, Long agentId, Long id) {
        requireOwnedByAgent(userId, agentId, id);
        removeById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AutomationJobVO setEnabledForAgent(Long userId, Long agentId, Long id, boolean enabled) {
        AutomationJob job = requireOwnedByAgent(userId, agentId, id);
        return doSetEnabled(job, enabled);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long prepareManualRunForAgent(Long userId, Long agentId, Long id) {
        AutomationJob job = requireOwnedByAgent(userId, agentId, id);
        return doPrepareManualRun(job);
    }

    @Override
    public Page<AutomationJobRunVO> pageRunsByAgent(Long userId, Long agentId, Long jobId,
                                                    String keyword, String status,
                                                    int pageNum, int pageSize) {
        requireUserAgent(userId, agentId);
        int pn = Math.max(pageNum, 1);
        int ps = Math.min(Math.max(pageSize, 1), 50);
        Page<AutomationJobRun> page = new Page<>(pn, ps);
        LambdaQueryWrapper<AutomationJobRun> qw = new LambdaQueryWrapper<AutomationJobRun>()
                .eq(AutomationJobRun::getAgentId, agentId)
                .eq(jobId != null, AutomationJobRun::getJobId, jobId)
                .orderByDesc(AutomationJobRun::getTriggerTime);
        if (StringUtils.hasText(status)) {
            qw.eq(AutomationJobRun::getStatus, AutomationRunStatus.fromValue(status.trim()));
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            qw.and(w -> w.like(AutomationJobRun::getJobName, kw)
                    .or().like(AutomationJobRun::getInstruction, kw));
        }
        Page<AutomationJobRun> raw = runMapper.selectPage(page, qw);
        Page<AutomationJobRunVO> voPage = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        voPage.setRecords(raw.getRecords().stream().map(r -> toRunVo(r, false)).toList());
        return voPage;
    }

    @Override
    public AutomationJobRunVO getRunByAgent(Long userId, Long agentId, Long runId) {
        requireUserAgent(userId, agentId);
        AutomationJobRun run = runMapper.selectById(runId);
        if (run == null || run.getAgentId() == null || !run.getAgentId().equals(agentId)) {
            throw new BizException("执行记录不存在或无权查看");
        }
        return toRunVo(run, true);
    }

    private AutomationJob requireOwned(Long id) {
        AutomationJob job = getById(id);
        if (job == null) {
            throw new BizException(ErrorCode.AUTOMATION_JOB_NOT_FOUND);
        }
        return job;
    }

    private AutomationJob requireOwnedByAgent(Long userId, Long agentId, Long id) {
        requireUserAgent(userId, agentId);
        AutomationJob job = getById(id);
        if (job == null || job.getAgentId() == null || !job.getAgentId().equals(agentId)) {
            throw new BizException(ErrorCode.AUTOMATION_JOB_NOT_FOUND);
        }
        return job;
    }

    private static void requireUserAgent(Long userId, Long agentId) {
        // 企业资产：仅校验 Agent 上下文；userId 用于创建审计与会话归属
        if (agentId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "缺少智能体上下文");
        }
    }

    private boolean hasActiveLease(Long jobId) {
        LocalDateTime now = LocalDateTime.now(AutomationScheduleUtil.ZONE);
        Long cnt = runMapper.selectCount(new LambdaQueryWrapper<AutomationJobRun>()
                .eq(AutomationJobRun::getJobId, jobId)
                .eq(AutomationJobRun::getStatus, AutomationRunStatus.RUNNING)
                .and(w -> w.isNull(AutomationJobRun::getLeaseExpireAt)
                        .or().gt(AutomationJobRun::getLeaseExpireAt, now)));
        return cnt != null && cnt > 0;
    }

    private AutomationJobRun newRunningRun(AutomationJob job, String triggerType) {
        LocalDateTime now = LocalDateTime.now(AutomationScheduleUtil.ZONE);
        AutomationJobRun run = new AutomationJobRun();
        run.setJobId(job.getId());
        run.setUserId(job.getUserId());
        run.setAgentId(job.getAgentId());
        run.setJobName(job.getName());
        run.setAgentName(job.getAgentName());
        run.setInstruction(job.getInstruction());
        run.setTriggerType(triggerType);
        run.setTriggerTime(now);
        run.setStatus(AutomationRunStatus.RUNNING);
        run.setLeaseExpireAt(now.plusMinutes(LEASE_MINUTES));
        return run;
    }

    private AutomationAgentPort agentPort() {
        AutomationAgentPort port = agentPortProvider.getIfAvailable();
        if (port == null) {
            throw new BizException(ErrorCode.INTERNAL_ERROR);
        }
        return port;
    }

    private AutomationJobVO toJobVo(AutomationJob job) {
        AutomationJobVO vo = new AutomationJobVO();
        vo.setId(job.getId());
        vo.setName(job.getName());
        vo.setAgentId(job.getAgentId());
        vo.setAgentName(job.getAgentName());
        vo.setInstruction(job.getInstruction());
        vo.setScheduleType(job.getScheduleType() != null ? job.getScheduleType().getCode() : null);
        vo.setEnabled(job.getEnabled() != null && job.getEnabled() == 1);
        vo.setNextRunAt(job.getNextRunAt());
        vo.setLastRunAt(job.getLastRunAt());
        vo.setCreateTime(job.getCreateTime());
        vo.setUpdateTime(job.getUpdateTime());
        Map<String, Object> cfg = AutomationScheduleUtil.parseConfig(job.getScheduleConfig());
        vo.setTime(str(cfg.get("time")));
        vo.setOnceAt(str(cfg.get("onceAt")));
        vo.setCron(str(cfg.get("cron")));
        Object md = cfg.get("monthDay");
        if (md instanceof Number n) {
            vo.setMonthDay(n.intValue());
        }
        Object wd = cfg.get("weekdays");
        if (wd instanceof List<?> list) {
            vo.setWeekdays(list.stream()
                    .filter(Number.class::isInstance)
                    .map(o -> ((Number) o).intValue())
                    .toList());
        }
        return vo;
    }

    private AutomationJobRunVO toRunVo(AutomationJobRun run, boolean withDetail) {
        AutomationJobRunVO vo = new AutomationJobRunVO();
        vo.setId(run.getId());
        vo.setJobId(run.getJobId());
        vo.setJobName(run.getJobName());
        vo.setAgentId(run.getAgentId());
        vo.setAgentName(run.getAgentName());
        vo.setInstruction(run.getInstruction());
        vo.setTriggerType(run.getTriggerType());
        vo.setTriggerTime(run.getTriggerTime());
        vo.setStatus(run.getStatus() != null ? run.getStatus().getCode() : null);
        vo.setSessionId(run.getSessionId());
        vo.setSummary(run.getSummary());
        if (withDetail) {
            vo.setDetail(parseDetail(run.getDetailJson()));
        }
        vo.setError(run.getError());
        vo.setDurationMs(run.getDurationMs());
        vo.setDuration(formatDuration(run.getDurationMs()));
        return vo;
    }

    private Object parseDetail(String detailJson) {
        if (!StringUtils.hasText(detailJson)) {
            return null;
        }
        try {
            return objectMapper.readValue(detailJson, Object.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static String formatDuration(Long ms) {
        if (ms == null) {
            return "—";
        }
        if (ms < 1000) {
            return ms + "ms";
        }
        return String.format("%.1fs", ms / 1000.0);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
