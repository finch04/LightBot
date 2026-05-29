package com.lightbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 子图 VO（用于可视化和检索）
 *
 * @author finch
 * @since 2026-05-29
 */
@Data
@Schema(description = "子图数据")
public class GraphSubgraphVO {

    @Schema(description = "节点列表")
    private List<GraphNodeVO> nodes;

    @Schema(description = "边列表")
    private List<GraphEdgeVO> edges;

    @Schema(description = "节点总数")
    private Integer nodeCount;

    @Schema(description = "边总数")
    private Integer edgeCount;
}
