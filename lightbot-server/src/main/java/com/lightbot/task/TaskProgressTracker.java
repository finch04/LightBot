package com.lightbot.task;

import com.lightbot.service.TaskService;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务进度追踪器
 * <p>提供两种进度上报模式：
 * <ul>
 *   <li>阶段模式：executor 声明阶段列表，tracker 自动映射百分比</li>
 *   <li>子范围模式：子服务通过 {@link #subRange} 获取子进度推手，按完成数 tick</li>
 * </ul>
 * <p>两种模式可混用，后设置的进度不会回退。
 *
 * @author finch
 * @since 2026-05-29
 */
@Slf4j
public class TaskProgressTracker {

    private final TaskService taskService;
    private final Long taskId;
    private final List<String> phases = new ArrayList<>();
    private int currentPhaseIndex = -1;
    private int lastProgress = 0;

    public TaskProgressTracker(TaskService taskService, Long taskId) {
        this.taskService = taskService;
        this.taskId = taskId;
    }

    /**
     * 声明所有阶段（顺序调用，一次性声明）
     */
    public TaskProgressTracker phases(String... phaseNames) {
        for (String name : phaseNames) {
            phases.add(name);
        }
        return this;
    }

    /**
     * 进入下一个阶段，自动计算该阶段的起始进度并推送
     */
    public void nextPhase(String message) {
        currentPhaseIndex++;
        int progress = phaseStartProgress(currentPhaseIndex);
        doUpdate(progress, message);
    }

    /**
     * 直接设置进度（不会回退到更低的值）
     */
    public void update(int progress, String message) {
        if (progress <= lastProgress) return;
        doUpdate(progress, message);
    }

    /**
     * 获取子范围进度推手，用于子服务报告细粒度进度
     *
     * @param from  子范围起始百分比
     * @param to    子范围结束百分比
     * @param total 子任务总数
     * @return 子进度推手
     */
    public SubProgress subRange(int from, int to, int total) {
        return new SubProgress(from, to, total);
    }

    /**
     * 获取当前进度值
     */
    public int getLastProgress() {
        return lastProgress;
    }

    private void doUpdate(int progress, String message) {
        lastProgress = Math.max(lastProgress, progress);
        try {
            taskService.updateProgress(taskId, lastProgress, message);
        } catch (Exception e) {
            log.warn("[ProgressTracker] 更新进度失败: taskId={}, error={}", taskId, e.getMessage());
        }
    }

    /**
     * 计算第 N 个阶段的起始进度（均分 0~100）
     */
    private int phaseStartProgress(int phaseIndex) {
        if (phases.isEmpty()) return 0;
        return (int) ((phaseIndex / (double) phases.size()) * 100);
    }

    // ──────────────────────────────────────────
    //  子范围进度推手（供子服务使用）
    // ──────────────────────────────────────────

    /**
     * 子范围进度推手
     * <p>将 [from%, to%] 范围映射到 total 个子任务，每完成一个调用 {@link #tick(String)}。
     */
    public class SubProgress {
        private final int from;
        private final int range;
        private final int total;
        private int completed = 0;

        SubProgress(int from, int to, int total) {
            this.from = from;
            this.range = to - from;
            this.total = Math.max(total, 1);
        }

        /**
         * 完成一个子任务，自动计算并推送进度
         */
        public void tick(String message) {
            completed++;
            int progress = from + (int) ((completed / (double) total) * range);
            doUpdate(progress, message);
        }

        /**
         * 直接设置子范围内的进度（按完成数）
         */
        public void setCompleted(int count, String message) {
            this.completed = count;
            int progress = from + (int) ((count / (double) total) * range);
            doUpdate(progress, message);
        }
    }
}
