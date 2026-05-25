package com.lightbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 工作流图数据（草稿/发布/版本快照）
 */
@Data
@Schema(description = "工作流图数据")
public class WorkflowGraphDTO {

    @Schema(description = "节点列表")
    private List<Map<String, Object>> nodes;

    @Schema(description = "边列表")
    private List<Map<String, Object>> edges;

    @Schema(description = "全局配置：会话变量、上下文轮次等")
    private Map<String, Object> globalConfig;

    @Schema(description = "发布说明（选填，最多50字，仅发布时生效）")
    private String publishDescription;
}
