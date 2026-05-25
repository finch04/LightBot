package com.lightbot.service;

import com.lightbot.dto.WorkflowGraphDTO;
import com.lightbot.dto.WorkflowTestRequest;
import com.lightbot.dto.WorkflowTestResultVO;
import com.lightbot.dto.WorkflowVersionVO;

import java.util.List;
import java.util.Map;

/**
 * Agent 工作流配置：草稿、发布、版本、调试
 */
public interface WorkflowConfigService {

    /**
     * 获取工作流编辑态（草稿 + 发布状态 + 全局配置）
     */
    Map<String, Object> getWorkflowConfig(Long agentId);

    /**
     * 暂存草稿（跳过校验）
     */
    void saveDraft(Long agentId, WorkflowGraphDTO graph);

    /**
     * 发布工作流（必须通过校验）
     */
    Map<String, Object> publish(Long agentId, WorkflowGraphDTO graph);

    /**
     * 校验工作流配置
     */
    List<String> validate(Long agentId, WorkflowGraphDTO graph);

    /**
     * 版本列表
     */
    List<WorkflowVersionVO> listVersions(Long agentId);

    /**
     * 恢复指定版本到草稿
     */
    void restoreVersion(Long agentId, Integer version);

    /**
     * 调试运行
     */
    WorkflowTestResultVO testRun(Long agentId, WorkflowTestRequest request);
}
