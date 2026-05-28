package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * RAG 评估基准题目表
 *
 * @author finch
 * @since 2026-05-28
 */
@Data
@TableName("eval_rag_benchmark_item")
@Schema(description = "RAG 评估基准题目表")
public class EvalRagBenchmarkItem {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("benchmark_id")
    @Schema(description = "基准ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long benchmarkId;

    @TableField("query")
    @Schema(description = "评估问题")
    private String query;

    @TableField("gold_chunk_ids")
    @Schema(description = "标准片段ID列表（JSON数组）")
    private String goldChunkIds;

    @TableField("gold_answer")
    @Schema(description = "标准答案")
    private String goldAnswer;

    @TableField("sort_order")
    @Schema(description = "排序")
    private Integer sortOrder;
}
