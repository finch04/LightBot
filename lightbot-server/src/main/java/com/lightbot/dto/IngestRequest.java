package com.lightbot.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 文档入库请求DTO
 *
 * @author finch
 * @since 2026-05-20
 */
@Data
public class IngestRequest {

    @NotBlank(message = "分块策略不能为空")
    private String chunkStrategy;

    @NotNull(message = "分块大小不能为空")
    @Min(value = 100, message = "分块大小最小100")
    @Max(value = 2000, message = "分块大小最大2000")
    private Integer chunkSize;

    @NotNull(message = "重叠百分比不能为空")
    @Min(value = 0, message = "重叠百分比最小0")
    @Max(value = 99, message = "重叠百分比最大99")
    private Integer chunkOverlap;

    private String chunkDelimiter;
}
