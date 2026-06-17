package com.lightbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档版本列表VO
 *
 * @author finch
 * @since 2026-06-17
 */
@Data
@Schema(description = "文档版本信息")
public class DocumentVersionVO {

    @Schema(description = "版本记录ID")
    private String id;

    @Schema(description = "版本号")
    private Integer version;

    @Schema(description = "内容哈希")
    private String contentHash;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
