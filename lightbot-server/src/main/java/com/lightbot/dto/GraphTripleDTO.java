package com.lightbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 图谱三元组 DTO（用于 LLM 抽取结果和手动导入）
 *
 * @author finch
 * @since 2026-05-29
 */
@Data
@Schema(description = "图谱三元组")
public class GraphTripleDTO {

    @Schema(description = "头实体名称")
    private String head;

    @Schema(description = "头实体类型")
    private String headType;

    @Schema(description = "头实体描述")
    private String headDesc;

    @Schema(description = "关系类型")
    private String relation;

    @Schema(description = "关系描述")
    private String relationDesc;

    @Schema(description = "尾实体名称")
    private String tail;

    @Schema(description = "尾实体类型")
    private String tailType;

    @Schema(description = "尾实体描述")
    private String tailDesc;
}
