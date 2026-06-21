package com.lightbot.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.entity.Task;
import com.lightbot.service.EvalRagResultService;
import com.lightbot.service.TaskService;
import com.lightbot.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
    private final TaskService taskService;
    private final RedisUtil redisUtil;
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
        String answerProviderName = payload.has("answerProviderName") ? payload.get("answerProviderName").asText("") : "";
        String judgeProviderName = payload.has("judgeProviderName") ? payload.get("judgeProviderName").asText("") : "";

        String answerModel = answerProviderName.isBlank() ? String.valueOf(answerProviderId) : answerProviderName + (answerModelId != null ? "/" + answerModelId : "");
        String judgeModel = judgeProviderName.isBlank() ? String.valueOf(judgeProviderId) : judgeProviderName + (judgeModelId != null ? "/" + judgeModelId : "");
        log.info("[RAG评估执行器] 开始, taskId={}, resultId={}, benchmarkId={}, answerModel={}, judgeModel={}",
                task.getId(), resultId, benchmarkId, answerModel, judgeModel);

        var tracker = new TaskProgressTracker(taskService, task.getId())
                .phases("加载基准", "逐题评估", "聚合指标");

        // 1. 加载基准数据
        tracker.nextPhase("正在加载基准数据...");
        checkCancelled(task.getId());

        // 2. 逐题评估（10% → 80% 由回调驱动）
        tracker.nextPhase("正在评估...");
        resultService.executeEvaluation(resultId, benchmarkId, knowledgeId,
                answerProviderId, answerModelId, judgeProviderId, judgeModelId,
                (progress, message) -> {
                    checkCancelled(task.getId());
                    tracker.update(progress, message);
                });

        // 3. 完成
        tracker.update(100, "评估完成");
        return "RAG评估完成, resultId=" + resultId;
    }

    private void checkCancelled(Long taskId) {
        if (redisUtil.hasCancelSignal(taskId)) {
            throw new RuntimeException("任务已被用户取消");
        }
    }
}
