package com.lightbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.BizException;
import com.lightbot.entity.*;
import com.lightbot.enums.ErrorCode;
import com.lightbot.enums.ExperimentStatus;
import com.lightbot.enums.TaskType;
import com.lightbot.mapper.EvalExperimentMapper;
import com.lightbot.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 评测实验服务实现类
 *
 * @author finch
 * @since 2026-05-27
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvalExperimentServiceImpl extends ServiceImpl<EvalExperimentMapper, EvalExperiment>
        implements EvalExperimentService {

    private final TaskService taskService;
    private final EvalDatasetVersionService datasetVersionService;
    private final EvalDatasetItemService datasetItemService;
    private final PromptVersionService promptVersionService;
    private final EvalEvaluatorVersionService evaluatorVersionService;
    private final EvalEvaluatorService evaluatorService;
    private final EvalExperimentResultService experimentResultService;
    private final EvalChatService evalChatService;
    private final ObjectMapper objectMapper;

    @Override
    public EvalExperiment create(String name, String description, Long datasetId, Long datasetVersionId,
                                  String datasetVersion, String evaluationObjectConfig, String evaluatorConfig, Long userId) {
        // 1. 构建实验记录
        EvalExperiment experiment = new EvalExperiment();
        experiment.setName(name);
        experiment.setDescription(description);
        experiment.setDatasetId(datasetId);
        experiment.setDatasetVersionId(datasetVersionId);
        experiment.setDatasetVersion(datasetVersion);
        experiment.setEvaluationObjectConfig(evaluationObjectConfig);
        experiment.setEvaluatorConfig(evaluatorConfig);
        experiment.setStatus(ExperimentStatus.RUNNING);
        experiment.setProgress(0);
        experiment.setUserId(userId);
        save(experiment);

        // 2. 创建异步任务
        String payload = "{\"experimentId\":" + experiment.getId() + "}";
        Task task = taskService.createTask(TaskType.EXPERIMENT_RUN, "评测实验: " + name, userId, experiment.getId(), payload);
        experiment.setTaskId(task.getId());
        updateById(experiment);

        log.info("[评测实验] 创建成功, experimentId={}, taskId={}", experiment.getId(), task.getId());
        return experiment;
    }

    @Override
    public void stop(Long id, Long userId) {
        EvalExperiment experiment = getById(id);
        if (experiment == null) {
            throw new BizException(ErrorCode.EVAL_EXPERIMENT_NOT_FOUND);
        }
        if (experiment.getStatus() != ExperimentStatus.RUNNING) {
            throw new BizException(ErrorCode.EVAL_EXPERIMENT_STATUS_INVALID);
        }
        // 通过Task的cancelRequested标记请求取消
        if (experiment.getTaskId() != null) {
            taskService.requestCancel(experiment.getTaskId());
        }
    }

    @Override
    public void deleteById(Long id, Long userId) {
        EvalExperiment experiment = getById(id);
        if (experiment == null) {
            throw new BizException(ErrorCode.EVAL_EXPERIMENT_NOT_FOUND);
        }
        if (experiment.getStatus() == ExperimentStatus.RUNNING) {
            throw new BizException(ErrorCode.EVAL_EXPERIMENT_STATUS_INVALID);
        }
        removeById(id);
    }

    @Override
    public EvalExperiment restart(Long id, Long userId) {
        EvalExperiment experiment = getById(id);
        if (experiment == null) {
            throw new BizException(ErrorCode.EVAL_EXPERIMENT_NOT_FOUND);
        }
        // 1. 清理旧结果
        experimentResultService.lambdaUpdate()
                .eq(EvalExperimentResult::getExperimentId, id)
                .remove();

        // 2. 重置状态
        experiment.setStatus(ExperimentStatus.RUNNING);
        experiment.setProgress(0);
        experiment.setCompleteTime(null);
        updateById(experiment);

        // 3. 创建新任务
        String payload = "{\"experimentId\":" + id + "}";
        Task task = taskService.createTask(TaskType.EXPERIMENT_RUN, "重启实验: " + experiment.getName(), userId, id, payload);
        experiment.setTaskId(task.getId());
        updateById(experiment);

        return experiment;
    }

    @Override
    public Page<EvalExperiment> list(int pageNum, int pageSize, String keyword, String status, Long userId) {
        Page<EvalExperiment> page = new Page<>(pageNum, pageSize);
        var wrapper = new LambdaQueryWrapper<EvalExperiment>()
                .eq(userId != null, EvalExperiment::getUserId, userId)
                .like(keyword != null && !keyword.isBlank(), EvalExperiment::getName, keyword)
                .eq(status != null && !status.isBlank(), EvalExperiment::getStatus, status)
                .orderByDesc(EvalExperiment::getCreateTime);
        return baseMapper.selectPage(page, wrapper);
    }

    @Override
    public EvalExperiment getDetail(Long id) {
        return getById(id);
    }

    @Override
    public void executeExperiment(Long experimentId, Task task) {
        EvalExperiment experiment = getById(experimentId);
        if (experiment == null) {
            throw new BizException(ErrorCode.EVAL_EXPERIMENT_NOT_FOUND);
        }

        try {
            // 1. 解析评测对象配置
            JsonNode objectConfig = objectMapper.readTree(experiment.getEvaluationObjectConfig());
            String type = objectConfig.get("type").asText();
            if (!"prompt".equals(type)) {
                throw new BizException(ErrorCode.EVAL_EXPERIMENT_STATUS_INVALID);
            }
            JsonNode config = objectConfig.get("config");
            String promptKey = config.get("promptKey").asText();
            String version = config.get("version").asText();
            JsonNode variableMap = config.get("variableMap");

            // 2. 加载数据集
            EvalDatasetVersion datasetVersion = datasetVersionService.getById(experiment.getDatasetVersionId());
            if (datasetVersion == null) {
                throw new BizException(ErrorCode.EVAL_DATASET_VERSION_NOT_FOUND);
            }
            List<Long> itemIds = objectMapper.readValue(datasetVersion.getDatasetItems(), new TypeReference<>() {});
            List<EvalDatasetItem> items = datasetItemService.listByIds(itemIds);

            // 3. 加载Prompt版本
            PromptVersion promptVersion = promptVersionService.getByKeyAndVersion(promptKey, version);
            if (promptVersion == null) {
                throw new BizException(ErrorCode.PROMPT_VERSION_NOT_FOUND);
            }

            // 4. 解析评估器配置
            List<Map<String, Object>> evaluatorConfigs = objectMapper.readValue(
                    experiment.getEvaluatorConfig(), new TypeReference<>() {});

            int total = items.size();
            int completed = 0;

            // 5. 遍历数据项执行评测
            for (EvalDatasetItem item : items) {
                // 5.1 检查取消请求
                Task latest = taskService.getById(task.getId());
                if (latest != null && latest.getCancelRequested() == 1) {
                    experiment.setStatus(ExperimentStatus.STOPPED);
                    updateById(experiment);
                    return;
                }

                // 5.2 解析数据内容
                JsonNode dataContent = objectMapper.readTree(item.getDataContent());

                // 5.3 调用被测Prompt获取实际输出
                String actualOutput = getPromptResult(promptVersion, dataContent, variableMap, promptVersion.getModelConfig());

                // 5.4 遍历评估器打分
                for (Map<String, Object> evalConfig : evaluatorConfigs) {
                    Long evaluatorVersionId = Long.parseLong(evalConfig.get("evaluatorVersionId").toString());
                    EvalEvaluatorVersion evaluatorVersion = evaluatorVersionService.getById(evaluatorVersionId);
                    if (evaluatorVersion == null) {
                        continue;
                    }

                    // 获取评估器名称
                    EvalEvaluator evaluator = evaluatorService.getById(evaluatorVersion.getEvaluatorId());
                    String evaluatorName = evaluator != null ? evaluator.getName() : "评估器#" + evaluatorVersion.getEvaluatorId();

                    // 构建评估器变量
                    String evalVariables = buildEvaluatorVariables(evalConfig, dataContent, actualOutput);
                    var scoreResult = evalChatService.callEvaluator(
                            evaluatorVersion.getModelConfig(), evaluatorVersion.getPrompt(), evalVariables);

                    // 5.5 保存结果
                    EvalExperimentResult result = new EvalExperimentResult();
                    result.setExperimentId(experimentId);
                    result.setInput(dataContent.has("input") ? dataContent.get("input").asText() : dataContent.toString());
                    result.setActualOutput(actualOutput);
                    result.setReferenceOutput(dataContent.has("reference_output") ? dataContent.get("reference_output").asText() : null);
                    result.setScore(scoreResult.getScore());
                    result.setReason(scoreResult.getReason());
                    result.setEvaluatorVersionId(evaluatorVersionId);
                    result.setEvaluatorName(evaluatorName);
                    result.setEvaluationTime(LocalDateTime.now());
                    experimentResultService.save(result);
                }

                // 5.6 更新进度
                completed++;
                int progress = (int) ((completed * 100.0) / total);
                experiment.setProgress(progress);
                updateById(experiment);
                taskService.updateProgress(task.getId(), progress, "评测进度: " + completed + "/" + total);
            }

            // 6. 完成
            experiment.setStatus(ExperimentStatus.COMPLETED);
            experiment.setProgress(100);
            experiment.setCompleteTime(LocalDateTime.now());
            updateById(experiment);

        } catch (Exception e) {
            log.error("[评测实验] 执行失败, experimentId={}", experimentId, e);
            experiment.setStatus(ExperimentStatus.FAILED);
            updateById(experiment);
            throw new RuntimeException("实验执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 调用被测Prompt获取实际输出
     */
    private String getPromptResult(PromptVersion promptVersion, JsonNode dataContent,
                                    JsonNode variableMap, String modelConfig) {
        // 1. 构建变量映射
        Map<String, String> variables = new HashMap<>();
        if (variableMap != null && variableMap.isArray()) {
            for (JsonNode mapping : variableMap) {
                String promptVar = mapping.get("promptVariable").asText();
                String datasetCol = mapping.get("datasetColumn").asText();
                if (dataContent.has(datasetCol)) {
                    variables.put(promptVar, dataContent.get(datasetCol).asText());
                }
            }
        }
        String variablesJson;
        try {
            variablesJson = objectMapper.writeValueAsString(variables);
        } catch (Exception e) {
            variablesJson = "{}";
        }

        // 2. 调用LLM
        return evalChatService.callPrompt(modelConfig, promptVersion.getTemplate(), variablesJson);
    }

    /**
     * 构建评估器变量映射
     */
    private String buildEvaluatorVariables(Map<String, Object> evalConfig, JsonNode dataContent, String actualOutput) {
        Map<String, String> variables = new HashMap<>();
        List<Map<String, String>> variableMap = (List<Map<String, String>>) evalConfig.get("variableMap");
        if (variableMap != null) {
            for (Map<String, String> mapping : variableMap) {
                String evalVar = mapping.get("evaluatorVariable");
                String source = mapping.get("source");
                if ("actual_output".equals(source)) {
                    variables.put(evalVar, actualOutput);
                } else if (dataContent.has(source)) {
                    variables.put(evalVar, dataContent.get(source).asText());
                }
            }
        }
        try {
            return objectMapper.writeValueAsString(variables);
        } catch (Exception e) {
            return "{}";
        }
    }
}
