package com.lightbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 工作流调试运行结果
 */
@Data
@Builder
@Schema(description = "工作流调试运行结果")
public class WorkflowTestResultVO {

    @Schema(description = "输出内容")
    private String output;

    @Schema(description = "节点执行轨迹")
    private List<Map<String, Object>> nodeEvents;

    @Schema(description = "是否使用草稿")
    private Boolean usedDraft;
}
