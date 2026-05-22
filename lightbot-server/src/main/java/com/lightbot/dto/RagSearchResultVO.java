package com.lightbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 检索测试结果VO
 * <p>用于纯向量检索测试，返回检索到的文档块列表（不调用LLM）</p>
 *
 * @author finch
 * @since 2026-05-22
 */
@Data
@Schema(description = "检索测试结果")
public class RagSearchResultVO {

    @Schema(description = "文档块内容")
    private String content;

    @Schema(description = "排序号（从1开始，按相似度降序）")
    private Integer rank;

    @Schema(description = "相似度分数")
    private Double score;

    @Schema(description = "来源文档名称")
    private String documentName;

    @Schema(description = "文档ID")
    private Long documentId;
}
