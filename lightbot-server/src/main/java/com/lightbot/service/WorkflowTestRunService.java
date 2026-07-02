package com.lightbot.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lightbot.dto.WorkflowTestRequest;
import com.lightbot.dto.WorkflowTestResultVO;
import com.lightbot.dto.WorkflowTestRunDetailVO;
import com.lightbot.dto.WorkflowTestRunVO;
import com.lightbot.entity.WorkflowTestRun;
import com.lightbot.workflow.WorkflowDefinition;

import java.util.List;

/**
 * 工作流编排页测试运行记录
 */
public interface WorkflowTestRunService extends IService<WorkflowTestRun> {

    /**
     * 创建 running 状态记录
     *
     * @return runId
     */
    String startRun(Long agentId, Long userId, WorkflowTestRequest request,
                    WorkflowDefinition definition, boolean usedDraft);

    /**
     * 测试执行完成后更新记录
     */
    void finishRun(String runId, WorkflowTestResultVO result, long durationMs, String errorInfo);

    /**
     * resume 完成后更新同 runId 记录（仅测试来源记录存在时）
     */
    void updateAfterResume(String runId, WorkflowTestResultVO result, long durationMs, String errorInfo);

    /**
     * 列表（最近 N 条）
     */
    List<WorkflowTestRunVO> listByAgent(Long agentId, int limit);

    /**
     * 详情
     */
    WorkflowTestRunDetailVO getDetail(Long agentId, String runId);

    /**
     * 删除单条
     */
    void deleteRun(Long agentId, String runId);

    /**
     * 清空 Agent 测试历史
     */
    void clearByAgent(Long agentId);

    /**
     * 按 runId 查 DB 主键
     */
    Long findIdByRunId(String runId);
}
