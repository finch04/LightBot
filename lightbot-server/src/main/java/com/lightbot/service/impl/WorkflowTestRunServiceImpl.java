package com.lightbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.BizException;
import com.lightbot.dto.WorkflowTestRequest;
import com.lightbot.dto.WorkflowTestResultVO;
import com.lightbot.dto.WorkflowTestRunDetailVO;
import com.lightbot.dto.WorkflowTestRunVO;
import com.lightbot.entity.WorkflowTestRun;
import com.lightbot.enums.ErrorCode;
import com.lightbot.mapper.WorkflowTestRunMapper;
import com.lightbot.service.WorkflowTestRunService;
import com.lightbot.workflow.WorkflowDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 工作流测试运行记录 Service
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowTestRunServiceImpl extends ServiceImpl<WorkflowTestRunMapper, WorkflowTestRun>
        implements WorkflowTestRunService {

    private static final int MAX_RETAIN_PER_AGENT = 50;
    private static final int SUMMARY_MAX_LEN = 120;

    private final ObjectMapper objectMapper;

    @Override
    public String startRun(Long agentId, Long userId, WorkflowTestRequest request,
                           WorkflowDefinition definition, boolean usedDraft) {
        String runId = UUID.randomUUID().toString().replace("-", "");
        WorkflowTestRun record = new WorkflowTestRun();
        record.setRunId(runId);
        record.setAgentId(agentId);
        record.setUserId(userId);
        record.setTestMode(normalizeTestMode(request.getTestMode()));
        record.setUsedDraft(usedDraft ? 1 : 0);
        record.setStatus("running");
        record.setUserInput(request.getInput());
        record.setNodeEvents("[]");
        record.setStartTime(LocalDateTime.now());
        try {
            if (request.getGraph() != null) {
                record.setWorkflowGraph(objectMapper.writeValueAsString(request.getGraph()));
            } else if (definition != null) {
                record.setWorkflowGraph(objectMapper.writeValueAsString(definition));
            }
        } catch (Exception e) {
            log.warn("[WorkflowTestRun] 序列化图快照失败: {}", e.getMessage());
        }
        save(record);
        trimOldRecords(agentId);
        return runId;
    }

    @Override
    public void finishRun(String runId, WorkflowTestResultVO result, long durationMs, String errorInfo) {
        WorkflowTestRun record = getByRunId(runId);
        if (record == null) {
            return;
        }
        applyResult(record, result, durationMs, errorInfo);
        updateById(record);
    }

    @Override
    public void updateAfterResume(String runId, WorkflowTestResultVO result, long durationMs, String errorInfo) {
        WorkflowTestRun record = getByRunId(runId);
        if (record == null) {
            return;
        }
        applyResult(record, result, durationMs, errorInfo);
        updateById(record);
    }

    @Override
    public List<WorkflowTestRunVO> listByAgent(Long agentId, int limit) {
        int size = limit > 0 ? Math.min(limit, MAX_RETAIN_PER_AGENT) : MAX_RETAIN_PER_AGENT;
        List<WorkflowTestRun> records = list(new LambdaQueryWrapper<WorkflowTestRun>()
                .eq(WorkflowTestRun::getAgentId, agentId)
                .orderByDesc(WorkflowTestRun::getStartTime)
                .last("LIMIT " + size));
        return records.stream().map(this::toListVO).toList();
    }

    @Override
    public WorkflowTestRunDetailVO getDetail(Long agentId, String runId) {
        WorkflowTestRun record = getByAgentAndRunId(agentId, runId);
        if (record == null) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "测试记录不存在");
        }
        return toDetailVO(record);
    }

    @Override
    public void deleteRun(Long agentId, String runId) {
        WorkflowTestRun record = getByAgentAndRunId(agentId, runId);
        if (record == null) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "测试记录不存在");
        }
        removeById(record.getId());
    }

    @Override
    public void clearByAgent(Long agentId) {
        remove(new LambdaQueryWrapper<WorkflowTestRun>().eq(WorkflowTestRun::getAgentId, agentId));
    }

    @Override
    public Long findIdByRunId(String runId) {
        WorkflowTestRun record = getByRunId(runId);
        return record != null ? record.getId() : null;
    }

    private WorkflowTestRun getByRunId(String runId) {
        if (!StringUtils.hasText(runId)) {
            return null;
        }
        return getOne(new LambdaQueryWrapper<WorkflowTestRun>()
                .eq(WorkflowTestRun::getRunId, runId)
                .last("LIMIT 1"));
    }

    private WorkflowTestRun getByAgentAndRunId(Long agentId, String runId) {
        if (!StringUtils.hasText(runId)) {
            return null;
        }
        return getOne(new LambdaQueryWrapper<WorkflowTestRun>()
                .eq(WorkflowTestRun::getAgentId, agentId)
                .eq(WorkflowTestRun::getRunId, runId)
                .last("LIMIT 1"));
    }

    private void applyResult(WorkflowTestRun record, WorkflowTestResultVO result,
                             long durationMs, String errorInfo) {
        LocalDateTime now = LocalDateTime.now();
        record.setEndTime(now);
        record.setDurationMs(durationMs);
        record.setErrorInfo(errorInfo);
        if (result == null) {
            record.setStatus("failed");
            return;
        }
        if (Boolean.TRUE.equals(result.getSuspended())) {
            record.setStatus("suspended");
        } else if (StringUtils.hasText(errorInfo)) {
            record.setStatus("failed");
        } else {
            record.setStatus("completed");
        }
        if (result.getOutput() != null) {
            record.setOutput(result.getOutput());
        }
        try {
            if (result.getNodeEvents() != null) {
                record.setNodeEvents(objectMapper.writeValueAsString(result.getNodeEvents()));
            }
            if (result.getVariables() != null) {
                record.setVariables(objectMapper.writeValueAsString(result.getVariables()));
            }
        } catch (Exception e) {
            log.warn("[WorkflowTestRun] 序列化结果失败 runId={}: {}", record.getRunId(), e.getMessage());
        }
    }

    private void trimOldRecords(Long agentId) {
        List<WorkflowTestRun> all = list(new LambdaQueryWrapper<WorkflowTestRun>()
                .eq(WorkflowTestRun::getAgentId, agentId)
                .orderByDesc(WorkflowTestRun::getStartTime)
                .select(WorkflowTestRun::getId));
        if (all.size() <= MAX_RETAIN_PER_AGENT) {
            return;
        }
        List<Long> removeIds = all.subList(MAX_RETAIN_PER_AGENT, all.size()).stream()
                .map(WorkflowTestRun::getId)
                .toList();
        removeByIds(removeIds);
    }

    private WorkflowTestRunVO toListVO(WorkflowTestRun record) {
        WorkflowTestRunVO vo = new WorkflowTestRunVO();
        vo.setId(record.getId());
        vo.setRunId(record.getRunId());
        vo.setStatus(record.getStatus());
        vo.setTestMode(record.getTestMode());
        vo.setUsedDraft(record.getUsedDraft() != null && record.getUsedDraft() == 1);
        vo.setUserInputSummary(summarize(record.getUserInput()));
        vo.setOutputSummary(summarize(record.getOutput()));
        vo.setDurationMs(record.getDurationMs());
        vo.setStartTime(record.getStartTime());
        vo.setEndTime(record.getEndTime());
        return vo;
    }

    private WorkflowTestRunDetailVO toDetailVO(WorkflowTestRun record) {
        WorkflowTestRunDetailVO vo = new WorkflowTestRunDetailVO();
        vo.setId(record.getId());
        vo.setRunId(record.getRunId());
        vo.setStatus(record.getStatus());
        vo.setTestMode(record.getTestMode());
        vo.setUsedDraft(record.getUsedDraft() != null && record.getUsedDraft() == 1);
        vo.setUserInput(record.getUserInput());
        vo.setOutput(record.getOutput());
        vo.setErrorInfo(record.getErrorInfo());
        vo.setDurationMs(record.getDurationMs());
        vo.setStartTime(record.getStartTime());
        vo.setEndTime(record.getEndTime());
        vo.setNodeEvents(readJsonList(record.getNodeEvents()));
        vo.setVariables(readJsonMap(record.getVariables()));
        vo.setWorkflowGraph(readJsonMap(record.getWorkflowGraph()));
        return vo;
    }

    private List<Map<String, Object>> readJsonList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private Map<String, Object> readJsonMap(String json) {
        if (!StringUtils.hasText(json)) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private String summarize(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String trimmed = text.trim().replaceAll("\\s+", " ");
        return trimmed.length() <= SUMMARY_MAX_LEN ? trimmed : trimmed.substring(0, SUMMARY_MAX_LEN) + "...";
    }

    private String normalizeTestMode(String testMode) {
        if ("conversation".equalsIgnoreCase(testMode)) {
            return "conversation";
        }
        return "generation";
    }
}
