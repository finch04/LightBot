package com.lightbot.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.entity.Task;
import com.lightbot.service.EvalExperimentService;
import com.lightbot.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 实验执行任务执行器
 *
 * @author finch
 * @since 2026-05-27
 */
@Slf4j
@Component("experimentRunExecutor")
@RequiredArgsConstructor
public class ExperimentRunExecutor implements TaskExecutor {

    private final EvalExperimentService evalExperimentService;
    private final TaskService taskService;
    private final ObjectMapper objectMapper;

    @Override
    public String execute(Task task) throws Exception {
        JsonNode payload = objectMapper.readTree(task.getPayload());
        Long experimentId = payload.get("experimentId").asLong();
        log.info("[实验执行器] 开始, taskId={}, experimentId={}", task.getId(), experimentId);
        checkCancelled(task.getId());
        evalExperimentService.executeExperiment(experimentId, task);
        return "实验执行完成, experimentId=" + experimentId;
    }

    private void checkCancelled(Long taskId) {
        Task latest = taskService.getById(taskId);
        if (latest != null && latest.getCancelRequested() == 1) {
            throw new RuntimeException("任务已被用户取消");
        }
    }
}
