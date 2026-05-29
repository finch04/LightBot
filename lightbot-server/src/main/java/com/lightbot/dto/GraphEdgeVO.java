package com.lightbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

/**
 * 图谱边 VO
 *
 * @author finch
 * @since 2026-05-29
 */
@Data
@Schema(description = "图谱关系")
public class GraphEdgeVO {

    @Schema(description = "关系 elementId（Neo4j 内部标识）")
    private String elementId;

    @Schema(description = "关系业务ID")
    private String id;

    @Schema(description = "关系类型")
    private String relationType;

    @Schema(description = "关系描述")
    private String description;

    @Schema(description = "权重")
    private Double weight;

    @Schema(description = "来源")
    private String source;

    @Schema(description = "起始节点 elementId")
    private String startNodeElementId;

    @Schema(description = "目标节点 elementId")
    private String endNodeElementId;

    @Schema(description = "扩展属性")
    private Map<String, Object> properties;
}
