package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 向量表
 *
 * @author finch
 * @since 2026-05-19
 */
@Data
@TableName("embedding")
@Schema(description = "向量表")
public class Embedding {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("chunk_id")
    @Schema(description = "分块ID")
    private Long chunkId;

    @TableField("model_name")
    @Schema(description = "模型名称")
    private String modelName;

    @TableField("dimension")
    @Schema(description = "向量维度")
    private Integer dimension;

    @TableField(exist = false)
    @Schema(description = "向量数据")
    private String vector;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
