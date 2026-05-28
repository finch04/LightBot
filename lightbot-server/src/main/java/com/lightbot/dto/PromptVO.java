package com.lightbot.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Prompt 详情 VO
 *
 * @author finch
 * @since 2026-05-27
 */
@Data
@Schema(description = "Prompt详情")
public class PromptVO {

    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "Prompt唯一标识")
    private String promptKey;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "最新版本号")
    private String latestVersion;

    @Schema(description = "标签")
    private String tags;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
