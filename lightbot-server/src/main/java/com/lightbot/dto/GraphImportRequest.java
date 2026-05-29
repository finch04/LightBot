package com.lightbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 图谱批量导入请求
 *
 * @author finch
 * @since 2026-05-29
 */
@Data
@Schema(description = "图谱批量导入请求")
public class GraphImportRequest {

    @NotEmpty(message = "三元组列表不能为空")
    @Valid
    @Schema(description = "三元组列表")
    private List<GraphTripleDTO> triples;
}
