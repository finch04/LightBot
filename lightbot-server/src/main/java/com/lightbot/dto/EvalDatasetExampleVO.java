package com.lightbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 示例评测集信息
 *
 * @author finch
 * @since 2026-06-17
 */
@Data
@Builder
@Schema(description = "示例评测集信息")
public class EvalDatasetExampleVO {

    @Schema(description = "示例标识key")
    private String key;

    @Schema(description = "评测集名称")
    private String name;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "涉及的评测维度标签")
    private List<String> tags;

    @Schema(description = "示例数据条数")
    private Integer itemCount;
}
