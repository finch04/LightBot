package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * RAG 评估基准表
 *
 * @author finch
 * @since 2026-05-28
 */
@Data
@TableName("eval_rag_benchmark")
@Schema(description = "RAG 评估基准表")
public class EvalRagBenchmark {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("knowledge_id")
    @Schema(description = "知识库ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long knowledgeId;

    @TableField("name")
    @Size(max = 50, message = "基准名称不超过50字")
    @Schema(description = "基准名称")
    private String name;

    @TableField("description")
    @Size(max = 200, message = "基准描述不超过200字")
    @Schema(description = "基准描述")
    private String description;

    @TableField("question_count")
    @Schema(description = "题目数量")
    private Integer questionCount;

    @TableField("status")
    @Schema(description = "状态：generating-生成中, ready-就绪")
    private String status;

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

    @TableField(exist = false)
    @Schema(description = "是否包含标准片段ID")
    private Boolean hasGoldChunks;

    @TableField(exist = false)
    @Schema(description = "是否包含标准答案")
    private Boolean hasGoldAnswer;
}
