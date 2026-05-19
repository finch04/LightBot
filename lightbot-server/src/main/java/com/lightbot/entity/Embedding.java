package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 向量表
 * <p>向量字段(vector)通过原生SQL操作，不映射到实体</p>
 *
 * @author finch
 * @since 2026-05-19
 */
@Data
@TableName("embedding")
public class Embedding {

    /** 主键ID，雪花算法生成 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联分块ID，一对一 */
    private Long chunkId;

    /** 向量化模型名称 */
    private String modelName;

    /** 向量维度 */
    private Integer dimension;

    /** 向量数据，通过原生SQL操作 */
    @TableField(exist = false)
    private String vector;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
