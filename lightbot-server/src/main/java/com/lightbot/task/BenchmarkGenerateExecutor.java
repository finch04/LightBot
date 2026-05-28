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
    private final ObjectMapper objectMapper;

    @Override
    public String execute(Task task) throws Exception {
        JsonNode payload = objectMapper.readTree(task.getPayload());
        Long benchmarkId = payload.get("benchmarkId").asLong();
        Long knowledgeId = payload.get("knowledgeId").asLong();
        int count = payload.get("count").asInt(10);
        Long providerId = payload.has("providerId") ? payload.get("providerId").asLong() : null;
        String modelId = payload.has("modelId") ? payload.get("modelId").asText() : null;
        int neighborCount = payload.has("neighborCount") ? payload.get("neighborCount").asInt(3) : 3;

        log.info("[基准生成执行器] 开始, taskId={}, benchmarkId={}, knowledgeId={}, count={}, neighborCount={}",
                task.getId(), benchmarkId, knowledgeId, count, neighborCount);

        // 1. 开始生成
        taskService.updateProgress(task.getId(), 10, "正在分析知识库内容...");

        // 2. 调用生成逻辑
        taskService.updateProgress(task.getId(), 30, "正在调用 AI 生成题目...");
        try {
            benchmarkService.generateBenchmarkItems(benchmarkId, knowledgeId, count, providerId, modelId, neighborCount);
        } catch (Exception e) {
            // 生成失败时将状态重置为 ready，避免一直卡在 generating
            EvalRagBenchmark benchmark = benchmarkService.getById(benchmarkId);
            if (benchmark != null && "generating".equals(benchmark.getStatus())) {
                benchmark.setStatus("ready");
                benchmarkService.updateById(benchmark);
            }
            throw e;
        }

        // 3. 完成
        taskService.updateProgress(task.getId(), 100, "生成完成");
        return "评估基准生成完成, benchmarkId=" + benchmarkId + ", count=" + count;
    }
}
