package com.lightbot.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流挂起快照（人工确认节点等待恢复时使用，存 Redis）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowSuspendedRun {

    private String runId;

    private Long agentId;

    private Long sessionId;

    private String userInput;

    /** 调试时内联图 JSON；对话模式为空，恢复时按 configVersion 加载 */
    private String workflowGraphJson;

    private Integer configVersion;

    @Builder.Default
    private Map<String, Object> variables = new LinkedHashMap<>();

    @Builder.Default
    private Map<String, Object> nodeOutputs = new LinkedHashMap<>();

    private String suspendNodeId;

    /** 确认通过后继续执行的下一节点 */
    private String nextNodeId;

    private int stepIndex;

    @Builder.Default
    private List<Map<String, Object>> workflowEvents = new ArrayList<>();
}
