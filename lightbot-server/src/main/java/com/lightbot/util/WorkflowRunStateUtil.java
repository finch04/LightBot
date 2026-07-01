package com.lightbot.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.workflow.WorkflowSuspendedRun;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 工作流挂起状态 Redis 封装
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowRunStateUtil {

    private static final String KEY_PREFIX = "lightbot:workflow:suspended:";
    /** 挂起状态保留 24 小时 */
    private static final long TTL_SECONDS = 24 * 3600;

    private final RedisUtil redisUtil;
    private final ObjectMapper objectMapper;

    /**
     * 保存挂起快照
     *
     * @param run 挂起状态
     */
    public void saveSuspended(WorkflowSuspendedRun run) {
        if (run == null || run.getRunId() == null || run.getRunId().isBlank()) {
            throw new IllegalArgumentException("runId 不能为空");
        }
        try {
            String json = objectMapper.writeValueAsString(run);
            redisUtil.set(KEY_PREFIX + run.getRunId(), json, TTL_SECONDS);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化挂起状态失败", e);
        }
    }

    /**
     * 读取挂起快照
     *
     * @param runId 运行 ID
     * @return 挂起状态，不存在返回 null
     */
    public WorkflowSuspendedRun getSuspended(String runId) {
        if (runId == null || runId.isBlank()) {
            return null;
        }
        String json = redisUtil.get(KEY_PREFIX + runId);
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, WorkflowSuspendedRun.class);
        } catch (JsonProcessingException e) {
            log.warn("[WorkflowRunStateUtil] 反序列化挂起状态失败: runId={}", runId, e);
            return null;
        }
    }

    /**
     * 删除挂起快照
     *
     * @param runId 运行 ID
     */
    public void deleteSuspended(String runId) {
        if (runId != null && !runId.isBlank()) {
            redisUtil.delete(KEY_PREFIX + runId);
        }
    }
}
