package com.lightbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 工作流示例 Agent 信息
 *
 * @author finch
 * @since 2026-05-31
 */
@Data
@Builder
@Schema(description = "工作流示例Agent信息")
public class WorkflowExampleVO {

    @Schema(description = "示例标识key")
    private String key;

    @Schema(description = "Agent名称")
    private String name;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "涉及的节点类型标签")
    private List<String> nodeTypeTags;
}
