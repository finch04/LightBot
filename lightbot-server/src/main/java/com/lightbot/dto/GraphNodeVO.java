package com.lightbot.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 图谱节点 VO
 *
 * @author finch
 * @since 2026-05-29
 */
@Data
@Schema(description = "图谱节点")
public class GraphNodeVO {

    @Schema(description = "节点 elementId（Neo4j 内部标识）")
    private String elementId;

    @Schema(description = "节点业务ID")
    private String id;

    @Schema(description = "节点名称")
    private String name;

    @Schema(description = "实体类型")
    private String entityType;

    @Schema(description = "实体描述")
    private String description;

    @Schema(description = "来源")
    private String source;

    @Schema(description = "来源文档ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long documentId;

    @Schema(description = "节点标签列表")
    private List<String> labels;

    @Schema(description = "扩展属性")
    private Map<String, Object> properties;

    @Schema(description = "向量搜索相似度分数")
    private Double score;
}
