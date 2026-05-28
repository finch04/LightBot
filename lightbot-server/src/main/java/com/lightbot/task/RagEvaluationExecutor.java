package com.lightbot.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.entity.EvalRagBenchmarkItem;
import com.lightbot.entity.Task;
import com.lightbot.service.EvalRagBenchmarkService;
import com.lightbot.service.EvalRagResultService;
import com.lightbot.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * RAG 评估任务执行器
 *
 * @author finch
 * @since 2026-05-28
 */
@Slf4j
@Component("ragEvaluationExecutor")
@RequiredArgsConstructor
public class RagEvaluationExecutor implements TaskExecutor {

    private final EvalRagResultService resultService;
    private final EvalRagBenchmarkService benchmarkService;
    private final TaskService taskService;
    private final ObjectMapper objectMapper;

    @Override
    public String execute(Task task) throws Exception {
        JsonNode payload = objectMapper.readTree(task.getPayload());
        Long resultId = payload.get("resultId").asLong();
        Long benchmarkId = payload.get("benchmarkId").asLong();
        Long knowledgeId = payload.get("knowledgeId").asLong();
        Long answerProviderId = payload.has("answerProviderId") ? payload.get("answerProviderId").asLong() : null;
        String answerModelId = payload.has("answerModelId") ? payload.get("answerModelId").asText() : null;
        Long judgeProviderId = payload.has("judgeProviderId") ? payload.get("judgeProviderId").asLong() : null;
        String judgeModelId = payload.has("judgeModelId") ? payload.get("judgeModelId").asText() : null;

        log.info("[RAG评估执行器] 开始, taskId={}, resultId={}, benchmarkId={}",
                task.getId(), resultId, benchmarkId);

        // 1. 加载基准数据
        taskService.updateProgress(task.getId(), 5, "正在加载基准数据...");

        // 2. 执行评估
        taskService.updateProgress(task.getId(), 10, "正在评估中...");
        resultService.executeEvaluation(resultId, benchmarkId, knowledgeId,
                answerProviderId, answerModelId, judgeProviderId, judgeModelId);

        // 3. 完成
        taskService.updateProgress(task.getId(), 100, "评估完成");
        return "RAG评估完成, resultId=" + resultId;
    }
}
