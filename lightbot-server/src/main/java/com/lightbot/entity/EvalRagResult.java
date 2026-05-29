package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * RAG 评估结果表
 *
 * @author finch
 * @since 2026-05-28
 */
@Data
@TableName("eval_rag_result")
@Schema(description = "RAG 评估结果表")
public class EvalRagResult {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("knowledge_id")
    @Schema(description = "知识库ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long knowledgeId;

    @TableField("benchmark_id")
    @Schema(description = "基准ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long benchmarkId;

    @TableField("benchmark_name")
    @Schema(description = "基准名称快照")
    private String benchmarkName;

    @TableField("status")
    @Schema(description = "状态：RUNNING/COMPLETED/FAILED")
    private String status;

    @TableField("overall_score")
    @Schema(description = "综合评分")
    private Double overallScore;

    @TableField("retrieval_json")
    @Schema(description = "检索指标聚合JSON")
    private String retrievalJson;

    @TableField("answer_json")
    @Schema(description = "答案指标聚合JSON")
    private String answerJson;

    @TableField("config_json")
    @Schema(description = "评估配置JSON")
    private String configJson;

    @TableField("analysis")
    @Schema(description = "AI评估分析")
    private String analysis;

    @TableField("error")
    @Schema(description = "错误信息")
    private String error;

    @TableField("duration_ms")
    @Schema(description = "耗时毫秒")
    private Long durationMs;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @TableField("deleted")
    @TableLogic
    @Schema(description = "逻辑删除标记")
    private Integer deleted;
}
