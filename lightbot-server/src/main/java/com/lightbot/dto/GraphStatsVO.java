package com.lightbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 图谱统计 VO
 *
 * @author finch
 * @since 2026-05-29
 */
@Data
@NoArgsConstructor
@Schema(description = "图谱统计信息")
public class GraphStatsVO {

    @Schema(description = "节点总数")
    private Integer nodeCount;

    @Schema(description = "边总数")
    private Integer edgeCount;

    @Schema(description = "实体类型分布")
    private Map<String, Integer> typeDistribution;

    @Schema(description = "Neo4j 是否可用")
    private Boolean available;

    public GraphStatsVO(Integer nodeCount, Integer edgeCount, Map<String, Integer> typeDistribution) {
        this.nodeCount = nodeCount;
        this.edgeCount = edgeCount;
        this.typeDistribution = typeDistribution;
    }
}
