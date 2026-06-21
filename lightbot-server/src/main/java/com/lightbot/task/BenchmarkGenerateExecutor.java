package com.lightbot.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.entity.EvalRagBenchmark;
import com.lightbot.entity.Task;
import com.lightbot.service.EvalRagBenchmarkService;
import com.lightbot.service.TaskService;
import com.lightbot.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * AI 生成评估基准任务执行器
 *
 * @author finch
 * @since 2026-05-28
 */
@Slf4j
@Component("benchmarkGenerateExecutor")
@RequiredArgsConstructor
public class BenchmarkGenerateExecutor implements TaskExecutor {

    private final EvalRagBenchmarkService benchmarkService;
    private final TaskService taskService;
    private final RedisUtil redisUtil;
    private final ObjectMapper objectMapper;

    @Override
    public String execute(Task task) throws Exception {
        JsonNode payload = objectMapper.readTree(task.getPayload());
        Long benchmarkId = payload.get("benchmarkId").asLong();
        Long knowledgeId = payload.get("knowledgeId").asLong();
        int count = payload.get("count").asInt(10);
        Long providerId = payload.has("providerId") ? payload.get("providerId").asLong() : null;
        String modelId = payload.has("modelId") ? payload.get("modelId").asText() : null;
        String providerName = payload.has("providerName") ? payload.get("providerName").asText("") : "";
        int neighborCount = payload.has("neighborCount") ? payload.get("neighborCount").asInt(3) : 3;

        String modelInfo = providerName.isBlank() ? String.valueOf(providerId) : providerName + (modelId != null ? "/" + modelId : "");
        log.info("[基准生成执行器] 开始, taskId={}, benchmarkId={}, knowledgeId={}, model={}, count={}, neighborCount={}",
                task.getId(), benchmarkId, knowledgeId, modelInfo, count, neighborCount);

        var tracker = new TaskProgressTracker(taskService, task.getId())
                .phases("分析知识库", "生成题目", "保存结果");

        // 1. 分析知识库
        tracker.nextPhase("正在分析知识库内容...");
        checkCancelled(task.getId());

        // 2. 逐题生成（30% → 90%）
        tracker.nextPhase("正在生成题目 (0/" + count + ")...");
        try {
            TaskProgressTracker.SubProgress sub = tracker.subRange(30, 90, count);
            benchmarkService.generateBenchmarkItems(benchmarkId, knowledgeId, count, providerId, modelId, neighborCount,
                    progress -> {
                        checkCancelled(task.getId());
                        int done = Math.min((int) Math.round((progress - 30) / 60.0 * count), count);
                        sub.setCompleted(done, "正在生成题目 (" + done + "/" + count + ")...");
                    });
        } catch (Exception e) {
            EvalRagBenchmark benchmark = benchmarkService.getById(benchmarkId);
            if (benchmark != null && "generating".equals(benchmark.getStatus())) {
                benchmark.setStatus("ready");
                benchmarkService.updateById(benchmark);
            }
            throw e;
        }

        // 3. 完成
        tracker.update(100, "生成完成");
        return "评估基准生成完成, benchmarkId=" + benchmarkId + ", count=" + count;
    }

    private void checkCancelled(Long taskId) {
        if (redisUtil.hasCancelSignal(taskId)) {
            throw new RuntimeException("任务已被用户取消");
        }
    }
}
