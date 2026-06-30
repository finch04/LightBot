package com.lightbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Agent mention 候选资源聚合响应
 *
 * @author finch
 * @since 2026-06-29
 */
@Data
@Builder
@Schema(description = "Agent mention 候选资源")
public class MentionOptionsVO {

    @Schema(description = "AgentID（字符串形式）")
    private String agentId;

    @Schema(description = "Agent 版本快照ID（字符串形式）")
    private String agentVersionId;

    @Schema(description = "按类型分组的候选资源")
    private List<MentionGroupVO> groups;
}
