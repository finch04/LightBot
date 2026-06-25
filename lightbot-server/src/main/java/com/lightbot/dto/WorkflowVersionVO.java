package com.lightbot.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工作流版本信息
 */
@Data
@Builder
@Schema(description = "工作流版本信息")
public class WorkflowVersionVO {

    @Schema(description = "版本快照ID（agent_version.id）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "版本号")
    private Integer version;

    @Schema(description = "发布时间")
    private LocalDateTime publishedAt;

    @Schema(description = "节点数量")
    private Integer nodeCount;

    @Schema(description = "边数量")
    private Integer edgeCount;

    @Schema(description = "是否为当前生效版本")
    private Boolean current;

    @Schema(description = "发布说明")
    private String description;
}
