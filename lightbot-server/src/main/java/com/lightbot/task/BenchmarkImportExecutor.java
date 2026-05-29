package com.lightbot.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.entity.EvalRagBenchmark;
import com.lightbot.entity.Task;
import com.lightbot.service.EvalRagBenchmarkService;
import com.lightbot.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 基准导入任务执行器（异步上传 JSONL 文件）
 *
 * @author finch
 * @since 2026-05-29
 */
@Slf4j
@Component("benchmarkImportExecutor")
@RequiredArgsConstructor
public class BenchmarkImportExecutor implements TaskExecutor {

    private final EvalRagBenchmarkService benchmarkService;
    private final TaskService taskService;
    private final ObjectMapper objectMapper;

    @Override
    public String execute(Task task) throws Exception {
        JsonNode payload = objectMapper.readTree(task.getPayload());
        Long benchmarkId = payload.get("benchmarkId").asLong();
        Long knowledgeId = payload.get("knowledgeId").asLong();
        String tempFilePath = payload.get("tempFilePath").asText();

        log.info("[基准导入执行器] 开始, taskId={}, benchmarkId={}", task.getId(), benchmarkId);

        var tracker = new TaskProgressTracker(taskService, task.getId())
                .phases("解析文件", "导入数据");

        // 1. 解析文件（0% ~ 10%）
        tracker.nextPhase("正在解析 JSONL 文件...");
        checkCancelled(task.getId());

        // 2. 逐行导入（10% ~ 95%）
        tracker.nextPhase("正在导入数据...");
        try {
            benchmarkService.importBenchmarkItems(benchmarkId, tempFilePath,
                    progress -> {
                        checkCancelled(task.getId());
                        tracker.update(10 + (int) (progress * 0.85), "导入中 " + progress + "%");
                    });
        } catch (Exception e) {
            // 失败时重置基准状态
            EvalRagBenchmark benchmark = benchmarkService.getById(benchmarkId);
            if (benchmark != null && "generating".equals(benchmark.getStatus())) {
                benchmark.setStatus("ready");
                benchmarkService.updateById(benchmark);
            }
            throw e;
        }

        // 3. 完成
        tracker.update(100, "导入完成");
        return "基准导入完成, benchmarkId=" + benchmarkId;
    }

    private void checkCancelled(Long taskId) {
        Task latest = taskService.getById(taskId);
        if (latest != null && latest.getCancelRequested() == 1) {
            throw new RuntimeException("任务已被用户取消");
        }
    }
}
