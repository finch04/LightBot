package com.lightbot.config;

import com.lightbot.service.AutomationJobService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 自动化定时任务：分钟级扫描抢占 + 有界线程池执行 + 僵尸回收
 *
 * @author finch
 * @since 2026-07-26
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutomationJobScheduler {

    private final AutomationJobService automationJobService;

    @Value("${lightbot.automation.scan-batch-size:20}")
    private int scanBatchSize;

    @Value("${lightbot.automation.reclaim-batch-size:50}")
    private int reclaimBatchSize;

    @Value("${lightbot.automation.execute-pool-size:2}")
    private int executePoolSize;

    private final AtomicInteger threadSeq = new AtomicInteger();
    private volatile ThreadPoolExecutor executePool;

    private ThreadPoolExecutor pool() {
        if (executePool == null) {
            synchronized (this) {
                if (executePool == null) {
                    int n = Math.max(1, executePoolSize);
                    executePool = new ThreadPoolExecutor(
                            n, n, 60L, TimeUnit.SECONDS,
                            new LinkedBlockingQueue<>(100),
                            r -> {
                                Thread t = new Thread(r, "automation-exec-" + threadSeq.incrementAndGet());
                                t.setDaemon(true);
                                return t;
                            },
                            new ThreadPoolExecutor.CallerRunsPolicy());
                }
            }
        }
        return executePool;
    }

    /**
     * 分钟级扫描到期任务（默认 60s，可配置）
     */
    @Scheduled(fixedDelayString = "${lightbot.automation.scan-interval-ms:60000}",
            initialDelayString = "${lightbot.automation.scan-initial-delay-ms:45000}")
    public void scanDue() {
        try {
            List<Long> runIds = automationJobService.claimDueJobs(scanBatchSize);
            if (runIds.isEmpty()) {
                return;
            }
            log.info("[Automation] 抢占到期任务 {} 个", runIds.size());
            for (Long runId : runIds) {
                pool().execute(() -> {
                    try {
                        automationJobService.executeClaimedRun(runId);
                    } catch (Exception e) {
                        log.error("[Automation] 执行异常 runId={}", runId, e);
                    }
                });
            }
        } catch (Exception e) {
            log.error("[Automation] 扫描失败", e);
        }
    }

    /**
     * 僵尸 running 回收（默认 5 分钟）
     */
    @Scheduled(fixedDelayString = "${lightbot.automation.reclaim-interval-ms:300000}",
            initialDelayString = "${lightbot.automation.reclaim-initial-delay-ms:120000}")
    public void reclaim() {
        try {
            int n = automationJobService.reclaimExpiredRuns(reclaimBatchSize);
            if (n > 0) {
                log.warn("[Automation] 回收超时执行记录 {} 条", n);
            }
        } catch (Exception e) {
            log.error("[Automation] 僵尸回收失败", e);
        }
    }

    @PreDestroy
    void shutdown() {
        if (executePool != null) {
            executePool.shutdownNow();
        }
    }
}
