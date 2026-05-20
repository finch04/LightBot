package com.lightbot.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lightbot.enums.ChunkStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分块详情 VO（含向量化状态）
 *
 * @author finch
 * @since 2026-05-20
 */
@Data
@Schema(description = "分块详情")
public class ChunkVO {

    @Schema(description = "分块ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "文档ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long documentId;

    @Schema(description = "分块内容")
    private String content;

    @Schema(description = "分块序号")
    private Integer chunkIndex;

    @Schema(description = "Token数量")
    private Integer tokenCount;

    @Schema(description = "向量化状态")
    private ChunkStatus status;

    @Schema(description = "分块元数据")
    private String metadata;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
